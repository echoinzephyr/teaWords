package com.teameow.teawords.data

import com.google.gson.JsonParser
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/** 一次「检查更新」拿到的版本信息。 */
data class UpdateInfo(
    val tag: String,
    val name: String,
    val notes: String,
    val pageUrl: String,
    val publishedAt: String?,
    /** true 表示这条来自 tag 而不是正式 release，发布说明自然为空。 */
    val fromTag: Boolean = false,
    val apk: UpdateApk? = null,
    val prerelease: Boolean = false
)

data class UpdateApk(val name: String, val url: String, val size: Long, val sha256: String? = null)

internal fun trustedUpdateUrl(raw: String, owner: String = UpdateChecker.OWNER, repo: String = UpdateChecker.REPO): Boolean {
    val url = raw.toHttpUrlOrNull() ?: return false
    return url.scheme == "https" && url.host == "github.com" && url.port == 443 &&
        url.username.isEmpty() && url.password.isEmpty() &&
        url.encodedPath.startsWith("/$owner/$repo/releases/download/") &&
        url.encodedPath.endsWith(".apk", ignoreCase = true)
}

/**
 * 严格校验 "v1.2.3"、"1.2"、"1.2.3-beta.1"；无法解析时返回无效标记 [0]。
 * 只在数字段上比较，绝不用字符串比较（"1.10" > "1.9" 这样才不会判错）。
 */
private val VERSION = Regex("^[vV]?([0-9]+(?:\\.[0-9]+)*)(?:-([0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*))?(?:\\+[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?$")

internal fun versionParts(raw: String?): List<Int> {
    val match = VERSION.matchEntire(raw?.trim().orEmpty()) ?: return listOf(0)
    return match.groupValues[1].split('.').map { it.toIntOrNull() ?: return listOf(0) }
}

fun isComparableVersion(raw: String?): Boolean = versionParts(raw).any { it > 0 }
fun isPrereleaseVersion(raw: String?): Boolean = VERSION.matchEntire(raw?.trim().orEmpty())?.groupValues?.get(2)?.isNotEmpty() == true

/**
 * [latest] 是否比 [current] 新。解析不出有意义版本号时一律返回 false：
 * 宁可说「没查到新版本」，也不能凭一个看不懂的字符串谎报有更新。
 */
fun isNewerVersion(latest: String?, current: String?): Boolean {
    val candidate = versionParts(latest)
    if (!isComparableVersion(latest) || !isComparableVersion(current)) return false
    val installed = versionParts(current)
    for (index in 0 until maxOf(candidate.size, installed.size)) {
        val remote = candidate.getOrElse(index) { 0 }
        val local = installed.getOrElse(index) { 0 }
        if (remote != local) return remote > local
    }
    // A stable release supersedes a prerelease of the same numeric version.
    val remotePre = VERSION.matchEntire(latest!!.trim())!!.groupValues[2]
    val localPre = VERSION.matchEntire(current!!.trim())!!.groupValues[2]
    if (remotePre == localPre) return false
    if (remotePre.isEmpty()) return true
    if (localPre.isEmpty()) return false
    val remoteIds = remotePre.split('.')
    val localIds = localPre.split('.')
    for (index in 0 until minOf(remoteIds.size, localIds.size)) {
        val a = remoteIds[index]; val b = localIds[index]
        if (a == b) continue
        val aNumber = a.all { it in '0'..'9' }; val bNumber = b.all { it in '0'..'9' }
        val comparison = when {
            aNumber && bNumber -> a.toBigInteger().compareTo(b.toBigInteger())
            aNumber -> -1
            bNumber -> 1
            else -> a.compareTo(b)
        }
        if (comparison != 0) return comparison > 0
    }
    return remoteIds.size > localIds.size
}

/**
 * 匿名读取公开发布信息。API 不可用时读取同一仓库维护的静态清单；
 * 正式版与预发布分别缓存五分钟，并遵守 GitHub 返回的限流等待时间。
 */
class UpdateChecker(
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build(),
    private val owner: String = OWNER,
    private val repo: String = REPO,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val mutex = Mutex()
    private data class Cached(val time: Long, val info: UpdateInfo?)
    private val cached = mutableMapOf<Boolean, Cached>()
    private var apiRetryAt = 0L
    private var apiFailure: IOException? = null

    suspend fun latest(includePrereleases: Boolean = false): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        mutex.withLock { checkLatest(includePrereleases) }
    }

    private fun checkLatest(includePrereleases: Boolean): Result<UpdateInfo?> {
        try {
            cached[includePrereleases]?.takeIf { now() - it.time in 0 until CACHE_MILLIS }
                ?.let { return Result.success(it.info) }
            val info = try {
                if (now() < apiRetryAt) throw checkNotNull(apiFailure)
                (if (!includePrereleases) releaseLatest() else null)
                    ?: releaseList(includePrereleases) ?: tagList(includePrereleases)
            } catch (e: IOException) {
                if (e is ApiException && e.code !in listOf(403, 429, 500, 502, 503, 504)) throw e
                try { manifestLatest(includePrereleases) } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (fallback: Exception) {
                    throw IOException("${e.message}；备用更新源也暂时无法访问，请稍后重试或打开发布页", e)
                }
            }
            cached[includePrereleases] = Cached(now(), info)
            return Result.success(info)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SocketTimeoutException) {
            return Result.failure(IOException("连接 GitHub 超时，请检查网络后重试", e))
        } catch (e: UnknownHostException) {
            return Result.failure(IOException("无法连接 GitHub，请检查网络后重试", e))
        } catch (e: Exception) {
            return Result.failure(IOException(e.message ?: "检查更新失败", e))
        }
    }

    private class ApiException(val code: Int, message: String) : IOException(message)

    private fun get(path: String): Pair<Int, String> {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo$path")
            .header("Accept", "application/vnd.github+json")
            // GitHub API 对没有 User-Agent 的请求直接 403，这个头不是可选项。
            .header("User-Agent", "teaWords-android")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful && response.code != 404) {
                val limited = response.code == 429 || response.header("X-RateLimit-Remaining") == "0" ||
                    response.header("Retry-After") != null || body.contains("rate limit", ignoreCase = true)
                val message = when {
                    limited -> "GitHub 更新接口请求过于频繁（HTTP ${response.code}），请稍后重试"
                    response.code == 403 -> "GitHub 更新接口暂时拒绝访问（HTTP 403）"
                    else -> "GitHub 更新接口返回 HTTP ${response.code}"
                }
                val failure = ApiException(response.code, message)
                if (response.code == 403 || response.code == 429) {
                    val reset = response.header("X-RateLimit-Reset")?.toLongOrNull()?.times(1000)
                    val retry = response.header("Retry-After")?.toLongOrNull()?.coerceIn(0, 86_400)?.times(1000)
                    apiRetryAt = maxOf(now() + 60_000, reset ?: 0, now() + (retry ?: 0))
                    apiFailure = failure
                }
                throw failure
            }
            return response.code to body
        }
    }

    /** 清单由发布工作流生成，不使用第三方镜像，也不在客户端嵌入访问令牌。 */
    private fun manifestLatest(includePrereleases: Boolean): UpdateInfo {
        val request = Request.Builder()
            .url("https://raw.githubusercontent.com/$owner/$repo/main/updates/releases.json")
            .header("User-Agent", "teaWords-android").build()
        val json = client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "备用更新源返回 HTTP ${response.code}" }
            JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
        }
        check(json.get("schema")?.asInt == 1 && json.text("repository") == "$owner/$repo") { "更新清单格式不正确" }
        return json.getAsJsonArray("releases").mapNotNull { releaseInfo(it.asJsonObject, includePrereleases) }
            .reduceOrNull { best, next -> if (isNewerVersion(next.tag, best.tag)) next else best }
            ?: throw IOException("备用清单暂无对应渠道的发布版本")
    }

    /** 仓库没有正式发布时返回 null；其它非 2xx 直接抛错，交给调用方显示原因。 */
    private fun releaseLatest(): UpdateInfo? {
        val (code, body) = get("/releases/latest")
        if (code == 404) return null
        require(code in 200..299) { "GitHub 返回 HTTP $code" }
        return releaseInfo(JsonParser.parseString(body).asJsonObject)
    }

    private fun releaseList(includePrereleases: Boolean): UpdateInfo? {
        var best: UpdateInfo? = null
        for (page in 1..10) {
            val (code, body) = get("/releases?per_page=100&page=$page")
            require(code != 404) { "仓库不存在或为私有仓库，当前应用无法匿名读取更新；请登录 GitHub 后打开发布页。" }
            require(code in 200..299) { "GitHub 返回 HTTP $code" }
            val entries = JsonParser.parseString(body).asJsonArray
            entries.mapNotNull { releaseInfo(it.asJsonObject, includePrereleases) }.forEach { next ->
                if (best == null || isNewerVersion(next.tag, best!!.tag)) best = next
            }
            if (entries.size() < 100) break
        }
        return best
    }

    private fun JsonObject.text(key: String): String =
        get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()

    private fun releaseInfo(json: JsonObject, includePrereleases: Boolean = false): UpdateInfo? {
        if (json.get("draft")?.asBoolean == true) return null
        val tag = json.text("tag_name")
        val prerelease = json.get("prerelease")?.asBoolean == true || isPrereleaseVersion(tag)
        if (prerelease && !includePrereleases) return null
        if (!isComparableVersion(tag)) return null
        return UpdateInfo(
            tag = tag,
            prerelease = prerelease,
            name = json.text("name"),
            notes = json.text("body"),
            pageUrl = json.text("html_url").ifBlank { repositoryPage() },
            publishedAt = json.text("published_at").ifBlank { null },
            apk = json.get("assets")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { element ->
                val asset = element.asJsonObject
                val name = asset.text("name")
                val url = asset.text("browser_download_url")
                val size = asset.get("size")?.takeUnless { it.isJsonNull }?.asLong ?: 0L
                if (!name.endsWith(".apk", true) || !trustedUpdateUrl(url, owner, repo) || size <= 0) null
                else UpdateApk(name, url, size,
                    asset.text("digest").removePrefix("sha256:").takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) })
            }?.firstOrNull()
        )
    }

    private fun tagList(includePrereleases: Boolean): UpdateInfo? {
        val (code, body) = get("/tags")
        require(code in 200..299) { "GitHub 返回 HTTP $code" }
        val name = JsonParser.parseString(body).asJsonArray.map { it.asJsonObject.text("name") }
            .filter { isComparableVersion(it) && (includePrereleases || !isPrereleaseVersion(it)) }
            .reduceOrNull { best, next -> if (isNewerVersion(next, best)) next else best } ?: return null
        return UpdateInfo(
            tag = name,
            name = name,
            notes = "",
            pageUrl = "https://github.com/$owner/$repo/releases/tag/$name",
            publishedAt = null,
            prerelease = isPrereleaseVersion(name),
            fromTag = true
        )
    }

    private fun repositoryPage() = "https://github.com/$owner/$repo"

    companion object {
        const val OWNER = "echoinzephyr"
        const val REPO = "teaWords"
        const val PAGE_URL = "https://github.com/echoinzephyr/teaWords"
        const val RELEASES_URL = "https://github.com/echoinzephyr/teaWords/releases"
        private const val CACHE_MILLIS = 5 * 60_000L
    }
}
