package com.teameow.teawords.data.pronunciation

import android.content.Context
import android.media.MediaPlayer
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioFocusRequest
import android.os.Handler
import android.os.Looper
import android.os.Build
import com.teameow.teawords.data.PronunciationDialect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrl

enum class DictionaryAudioPhase { IDLE, LOADING, PLAYING, COMPLETED, FALLBACK, ERROR }
data class DictionaryAudioState(val phase: DictionaryAudioPhase = DictionaryAudioPhase.IDLE, val message: String? = null)

/** A single cancellable dictionary recording, with exact-dialect system speech as fallback. */
class DictionaryAudioPlayer(context: Context, private val fallback: PronunciationManager,
    private val recordingUrl: (String, PronunciationDialect) -> String = { word, dialect ->
        "https://dict.youdao.com/dictvoice".toHttpUrl().newBuilder()
            .addQueryParameter("type", if (dialect == PronunciationDialect.UK) "1" else "2")
            .addQueryParameter("audio", word).build().toString()
    }) : AutoCloseable {
    private val audio = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private var player: MediaPlayer? = null
    private var timeout: Runnable? = null
    private var focus: AudioFocusRequest? = null
    private val focusListener = AudioManager.OnAudioFocusChangeListener {
        if (it < 0) { stop(); mutable.value = DictionaryAudioState(DictionaryAudioPhase.ERROR, "发音被其他音频中断，请重新播放") }
    }
    private val mutable = MutableStateFlow(DictionaryAudioState())
    val state = mutable.asStateFlow()
    fun play(word: String, dialect: PronunciationDialect) {
        stop(); fallback.stop()
        val recording = MediaPlayer()
        player = recording; mutable.value = DictionaryAudioState(DictionaryAudioPhase.LOADING)
        fun failed() {
            if (player !== recording) return
            stop()
            mutable.value = DictionaryAudioState(DictionaryAudioPhase.FALLBACK, "录音暂不可用，已尝试系统发音")
            fallback.play(word, dialect)
        }
        timeout = Runnable { failed() }.also { handler.postDelayed(it, 5000) }
        try {
            recording.setAudioAttributes(attributes)
            recording.setDataSource(recordingUrl(word, dialect))
            recording.setOnPreparedListener {
                if (player === recording) {
                    val granted = if (Build.VERSION.SDK_INT >= 26) {
                        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                            .setAudioAttributes(attributes).setOnAudioFocusChangeListener(focusListener).build()
                        focus = request; audio.requestAudioFocus(request)
                    } else {
                        @Suppress("DEPRECATION")
                        audio.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    }
                    if (granted != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        stop(); mutable.value = DictionaryAudioState(DictionaryAudioPhase.ERROR, "暂时无法获得音频输出，请结束其他音频后重试")
                        return@setOnPreparedListener
                    }
                    timeout?.let(handler::removeCallbacks)
                    timeout = Runnable { failed() }.also { handler.postDelayed(it, 30000) }
                    runCatching {
                        recording.start()
                        mutable.value = DictionaryAudioState(DictionaryAudioPhase.PLAYING)
                    }.onFailure { failed() }
                }
            }
            recording.setOnCompletionListener {
                if (player === recording) { stop(); mutable.value = DictionaryAudioState(DictionaryAudioPhase.COMPLETED) }
            }
            recording.setOnErrorListener { _, _, _ -> failed(); true }
            recording.prepareAsync()
        } catch (_: Exception) { failed() }
    }
    fun stop() {
        timeout?.let(handler::removeCallbacks); timeout = null
        val old = player; player = null
        runCatching { old?.release() }
        if (Build.VERSION.SDK_INT >= 26) focus?.let { audio.abandonAudioFocusRequest(it) }
        else { @Suppress("DEPRECATION") audio.abandonAudioFocus(focusListener) }
        focus = null; mutable.value = DictionaryAudioState()
    }
    override fun close() = stop()
}
