package com.teameow.teawords.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException

class CourseMediaTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    /** Explicit opt-in: network availability must not make the regular test suite flaky. */
    @Test fun officialLessonDownloadsWithPinnedDigestAndOpensOffline() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyCourseCdn") == "true")
        val original=CourseCatalog.all.first().audio
        val alternative=InstrumentationRegistry.getArguments().getString("courseMediaHost")
        require(alternative == null || alternative == "voa-audio-ns.akamaized.net")
        val audio=if(alternative==null) original else original.copy(url=original.url.replace("voa-audio.voanews.eu",alternative))
        val file=File(context.filesDir,"course_audio/${audio.sha256}.mp3")
        val part=File(context.filesDir,"course_audio/${audio.sha256}.part")
        val previous=file.takeIf{it.exists()}?.readBytes()
        val previousPart=part.takeIf{it.exists()}?.readBytes()
        file.delete();part.delete()
        try {
            val downloaded=CourseMedia(context).download(audio)
            assertEquals(audio.bytes,downloaded.length())
            val metadata=android.media.MediaMetadataRetriever()
            try {
                metadata.setDataSource(downloaded.absolutePath)
                assertTrue(kotlin.math.abs(metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toInt()-audio.durationMs)<100)
            } finally { metadata.release() }
            val offline=OkHttpClient.Builder().addInterceptor{throw IOException("offline")}.build()
            assertEquals(downloaded,CourseMedia(context,offline).download(audio))
        } finally {
            file.delete();part.delete()
            previous?.let{file.writeBytes(it)};previousPart?.let{part.writeBytes(it)}
        }
    }
    @Test fun interruptionResumesOnlyVerifiedRangeThenReopensOffline() = runBlocking {
        val bytes = "audio-fixture-${System.nanoTime()}".toByteArray()
        val hash = WordbookProtocol.sha256(bytes)
        val audio = CourseAudio("https://audio.example.test/a.mp3",hash,bytes.size.toLong(),1000)
        val seen = mutableListOf<String?>()
        var first = true
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request(); seen.add(request.header("Range"))
            val interrupted = first; first = false
            val body = if (interrupted) object : ResponseBody() {
                override fun contentType() = "audio/mpeg".toMediaType()
                override fun contentLength() = bytes.size.toLong()
                override fun source(): BufferedSource = object : Source {
                    var sent = false
                    override fun timeout() = Timeout.NONE
                    override fun close() { }
                    override fun read(sink: Buffer, byteCount: Long): Long {
                        if (sent) throw IOException("connection interrupted")
                        sent = true; sink.write(bytes,0,4);return 4
                    }
                }.buffer()
            } else bytes.copyOfRange(4,bytes.size).toResponseBody("audio/mpeg".toMediaType())
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(if(interrupted)200 else 206)
                .message("test").body(body).apply { if(!interrupted) header("Content-Range","bytes 4-${bytes.size-1}/${bytes.size}") }.build()
        }.build()
        val file = File(context.filesDir,"course_audio/$hash.mp3")
        val part = File(context.filesDir,"course_audio/$hash.part")
        try {
            try { CourseMedia(context,client).download(audio); fail("interruption accepted") } catch (_: IOException) { }
            assertEquals(4L,part.length()); assertFalse(file.exists())
            assertArrayEquals(bytes,CourseMedia(context,client).download(audio).readBytes())
            assertEquals(listOf(null,"bytes=4-"),seen)
            val offline = OkHttpClient.Builder().addInterceptor { throw IOException("offline") }.build()
            assertEquals(file,CourseMedia(context,offline).download(audio))
        } finally { file.delete();part.delete() }
    }
    @Test fun checksumFailureNeverBecomesPlayableAndCanRetry() = runBlocking {
        val bytes = "expected-${System.nanoTime()}".toByteArray();val hash=WordbookProtocol.sha256(bytes)
        val audio=CourseAudio("https://audio.example.test/b.mp3",hash,bytes.size.toLong(),1000)
        var corrupt = true
        val client=OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("ok")
                .body((if(corrupt) ByteArray(bytes.size) else bytes).toResponseBody()).build()
        }.build()
        val media=CourseMedia(context,client)
        try {
            try { media.download(audio);fail("bad checksum") } catch (_: IOException) { }
            assertNull(media.cached(audio));assertFalse(File(context.filesDir,"course_audio/$hash.part").exists())
            corrupt=false;assertArrayEquals(bytes,media.download(audio).readBytes())
        } finally { File(context.filesDir,"course_audio/$hash.mp3").delete() }
    }
}
