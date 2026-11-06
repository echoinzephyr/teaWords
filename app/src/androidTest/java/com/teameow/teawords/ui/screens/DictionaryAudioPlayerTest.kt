package com.teameow.teawords.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.teameow.teawords.data.PronunciationDialect
import com.teameow.teawords.data.pronunciation.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DictionaryAudioPlayerTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var synthesized = 0
    private fun manager(): PronunciationManager = PronunciationManager(object : SpeechBackend {
        override fun initialize(onReady: (Boolean) -> Unit, onEvent: (String, SpeechPhase, String?) -> Unit) = onReady(true)
        override fun voices() = listOf(SpeechVoice("us", Locale.US, false), SpeechVoice("uk", Locale.UK, false))
        override fun select(voice: SpeechVoice) = true
        override fun speak(word: String, id: String): Boolean { synthesized++; return true }
        override fun stop() = Unit
        override fun close() = Unit
    })

    @Test fun loadingIsNotReportedAsPlayingAndNativeCompletionReleasesThePlayer() {
        val fixture = File(compose.activity.cacheDir, "audio-state-test.wav")
        val samples = 16_000 // Half a second of silent PCM, with no third-party media fixture.
        fixture.writeBytes(ByteBuffer.allocate(44 + samples).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + samples); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(16_000); putInt(32_000); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(samples)
        }.array())
        lateinit var speech: PronunciationManager
        lateinit var audio: DictionaryAudioPlayer
        compose.runOnUiThread { speech = manager(); audio = DictionaryAudioPlayer(compose.activity, speech) { _, _ -> fixture.absolutePath } }
        try {
            compose.runOnIdle {
                audio.play("comprehension", PronunciationDialect.US)
                assertEquals(DictionaryAudioPhase.LOADING, audio.state.value.phase)
            }
            compose.waitUntil(5_000) { audio.state.value.phase == DictionaryAudioPhase.COMPLETED }
            assertEquals(0, synthesized)
            compose.runOnIdle { audio.stop(); assertEquals(DictionaryAudioPhase.IDLE, audio.state.value.phase) }
        } finally { compose.runOnUiThread { audio.close(); speech.close() }; fixture.delete() }
    }

    @Test fun missingRecordingFallsBackToTheRequestedDialectAndCanBeStopped() {
        lateinit var speech: PronunciationManager
        lateinit var audio: DictionaryAudioPlayer
        compose.runOnUiThread { speech = manager(); audio = DictionaryAudioPlayer(compose.activity, speech) { _, _ -> "/missing/recording.mp3" } }
        try {
            compose.waitUntil(5_000) { speech.state.value.phase == SpeechPhase.READY }
            compose.runOnIdle { audio.play("comprehension", PronunciationDialect.UK) }
            compose.waitUntil(5_000) { audio.state.value.phase == DictionaryAudioPhase.FALLBACK }
            assertEquals(1, synthesized)
            assertEquals(PronunciationDialect.UK, speech.state.value.dialect)
            assertEquals("comprehension", speech.state.value.word)
            assertFalse(audio.state.value.message.isNullOrBlank())
            compose.runOnIdle { audio.stop(); speech.stop(); assertEquals(DictionaryAudioPhase.IDLE, audio.state.value.phase) }
        } finally { compose.runOnUiThread { audio.close(); speech.close() } }
    }
}
