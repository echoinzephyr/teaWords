package com.teameow.teawords.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.*
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Hash-addressed media remains available offline. Part files are resumable and never played. */
class CourseMedia(context: Context, private val client: OkHttpClient = downloadClient) {
    private val directory = File(context.filesDir, "course_audio").apply { mkdirs() }
    private fun target(audio: CourseAudio) = File(directory, "${audio.sha256}.mp3")
    fun cached(audio: CourseAudio): File? = runCatching { target(audio).takeIf { valid(it, audio) } }.getOrNull()
    private fun valid(file: File, audio: CourseAudio) = file.isFile && file.length() == audio.bytes &&
        WordbookProtocol.sha256(file.readBytes()) == audio.sha256

    suspend fun download(audio: CourseAudio, progress: (Long, Long) -> Unit = { _, _ -> }): File = gate.withLock {
        withContext(Dispatchers.IO) {
            require(audio.url.startsWith("https://") || com.teameow.teawords.BuildConfig.DEBUG &&
                audio.url.startsWith("http://127.0.0.1:"))
            require(audio.sha256.matches(Regex("[a-f0-9]{64}")) && audio.bytes in 1..30_000_000)
            cached(audio)?.let { return@withContext it }
            val part = File(directory, "${audio.sha256}.part")
            if (part.length() > audio.bytes) part.delete()
            if (!valid(part, audio)) transfer(audio, part, progress)
            currentCoroutineContext().ensureActive()
            if (!valid(part, audio)) { part.delete(); throw IOException("音频校验失败，请重新下载") }
            val file = target(audio)
            if (file.exists()) file.delete()
            check(part.renameTo(file)) { "音频保存失败，请重试" }
            file
        }
    }
    private suspend fun transfer(audio: CourseAudio, part: File, progress: (Long, Long) -> Unit) {
        val finished = CompletableDeferred<Unit>()
        var networkStarted = false
        try { suspendCancellableCoroutine<Unit> { continuation ->
            val offset = part.length()
            val mirror = com.teameow.teawords.BuildConfig.COURSE_BASE_URL.trimEnd('/')
            val url = if (mirror.isEmpty()) audio.url else "$mirror/api/courses/audio/${audio.sha256}.mp3"
            val request = Request.Builder().url(url).header("Accept-Encoding", "identity")
                .apply { if (offset > 0) header("Range", "bytes=$offset-") }.build()
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                    finished.complete(Unit)
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use {
                            if (it.code == 416) { part.delete(); throw IOException("下载片段已失效，请重试") }
                            if (it.code !in setOf(200, 206)) throw IOException("音频下载失败（${it.code}），请重试")
                            val append = it.code == 206
                            if (append && it.header("Content-Range") != "bytes $offset-${audio.bytes - 1}/${audio.bytes}") {
                                part.delete(); throw IOException("音频续传范围异常，请重试")
                            }
                            var done = if (append) offset else 0L
                            val body = it.body ?: throw IOException("没有收到音频")
                            body.byteStream().use { input -> part.outputStream(append).use { output ->
                                val buffer = ByteArray(16 * 1024)
                                while (true) {
                                    if (!continuation.isActive) throw IOException("下载已暂停")
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    done += count
                                    if (done > audio.bytes) { part.delete(); throw IOException("音频大小异常") }
                                    output.write(buffer, 0, count)
                                    progress(done, audio.bytes)
                                }
                            } }
                            if (done != audio.bytes) throw IOException("音频下载中断，可重试续传")
                        }
                        if (continuation.isActive) continuation.resume(Unit)
                    } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
                    finally { finished.complete(Unit) }
                }
            })
            networkStarted = true
        } } finally {
            // Keep the file gate until the canceled network callback has closed its output stream.
            if (networkStarted) withContext(NonCancellable) { finished.await() }
        }
    }
    companion object {
        private val gate = Mutex()
        private val downloadClient = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS).followSslRedirects(false).build()
    }
}

private fun File.outputStream(append: Boolean) = java.io.FileOutputStream(this, append)
