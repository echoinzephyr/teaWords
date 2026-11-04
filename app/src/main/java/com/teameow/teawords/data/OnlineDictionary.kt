package com.teameow.teawords.data

import com.google.gson.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Online answers have no database identity and cannot implicitly become learning evidence. */
data class OnlineWordEntry(val word: String, val detail: StudyDictionaryEntry)
data class OnlineSuggestion(val word: String, val explanation: String)
data class OnlineDictionaryResult(val entry: OnlineWordEntry?, val suggestions: List<OnlineSuggestion>)

internal class MemoryLru<K, V>(private val capacity: Int) {
    private val values = object : LinkedHashMap<K, V>(capacity, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?) = size > capacity
    }
    @Synchronized fun get(key: K): V? = values[key]
    @Synchronized fun put(key: K, value: V) { values[key] = value }
    @Synchronized fun clear() = values.clear()
}

/** WordMoment's website endpoints; cancellable requests, bounded process-only cache. */
class OnlineDictionary(private val fetch: suspend (String) -> String = { DictionaryHttp.get(it) }) {
    private val cache = MemoryLru<String, OnlineDictionaryResult>(64)
    suspend fun lookup(query: String): OnlineDictionaryResult {
        val word = query.trim()
        require(word.length <= 160) { "查词最多支持 160 个字符；长文请使用翻译模式" }
        require(word.isNotEmpty()) { "请输入要查询的单词" }
        val key = word.lowercase(Locale.ROOT)
        cache.get(key)?.let { return it }
        val url = "https://dict.youdao.com/jsonapi".toHttpUrl().newBuilder().addQueryParameter("q", word).build()
        val detail = parse(fetch(url.toString()))
        val entry = detail.takeIf { it.definition.isNotBlank() }?.let { OnlineWordEntry(word, it) }
        val suggestions = if (entry == null) {
            val suggestionUrl = "https://dict.youdao.com/suggest".toHttpUrl().newBuilder()
                .addQueryParameter("q", word).addQueryParameter("num", "8").addQueryParameter("doctype", "json").build()
            parseSuggestions(fetch(suggestionUrl.toString()))
        } else emptyList()
        currentCoroutineContext().ensureActive()
        return OnlineDictionaryResult(entry, suggestions).also { cache.put(key, it) }
    }
    companion object {
        val shared by lazy { OnlineDictionary() }
        fun parse(payload: String): StudyDictionaryEntry {
            val root = JsonParser.parseString(payload).asJsonObject
            val head = root.obj("ec").array("word").objects().firstOrNull()
            val simple = root.obj("simple").array("word").objects().firstOrNull() ?: root.obj("base")
            val meanings = head.array("trs").objects().flatMap { tr ->
                tr.array("tr").objects().flatMap { line -> line.obj("l").array("i").strings() }
            }.filter(String::isNotBlank).distinct()
            val phrases = root.obj("phrs").array("phrs").objects().mapNotNull {
                val phrase = it.obj("phr")
                val text = phrase.obj("headword").obj("l").string("i")
                val meaning = phrase.array("trs").objects().map { row -> row.obj("tr").obj("l").string("i") }
                    .filter(String::isNotBlank).joinToString("；")
                if (text.isBlank() || meaning.isBlank()) null else text to meaning
            }.distinct().take(12)
            val forms = (head.array("wfs").objects() + root.array("wfs").objects()).mapNotNull {
                val form = it.obj("wf"); val name = form.string("name"); val value = form.string("value")
                if (name.isBlank() || value.isBlank()) null else name to value
            }.distinct()
            return StudyDictionaryEntry(meanings.joinToString("\n"),
                head.string("ukphone").ifBlank { simple.string("ukphone") },
                head.string("usphone").ifBlank { simple.string("usphone") }, phrases, forms)
        }
        fun parseSuggestions(payload: String): List<OnlineSuggestion> {
            val root = JsonParser.parseString(payload).asJsonObject
            return root.obj("data").array("entries").objects().mapNotNull {
                val word = it.string("entry").trim()
                if (word.isBlank()) null else OnlineSuggestion(word, it.string("explain"))
            }.distinctBy { it.word.lowercase(Locale.ROOT) }.take(8)
        }
    }
}

internal object DictionaryHttp {
    val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS).build()
    suspend fun get(url: String): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(url).header("User-Agent", "teaWords/1.2.1 (Android)").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.use {
                        if (!it.isSuccessful) throw IOException("在线词典暂时不可用（${it.code}）")
                        it.body?.string()?.takeIf(String::isNotBlank) ?: throw IOException("在线词典返回空内容")
                    }
                    if (continuation.isActive) continuation.resume(body)
                } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
            }
        })
    }
}
internal fun JsonObject?.obj(key: String): JsonObject? = this?.get(key)?.takeIf { it.isJsonObject }?.asJsonObject
internal fun JsonObject?.array(key: String): JsonArray? = this?.get(key)?.takeIf { it.isJsonArray }?.asJsonArray
internal fun JsonObject?.string(key: String): String = this?.get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
internal fun JsonArray?.objects(): List<JsonObject> = this?.filter { it.isJsonObject }?.map { it.asJsonObject }.orEmpty()
internal fun JsonArray?.strings(): List<String> = this?.filter { it.isJsonPrimitive }?.map { it.asString.trim() }.orEmpty()
