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

/** A single cancellable dictionary recording, with exact-dialect system speech as fallback. */
class DictionaryAudioPlayer(context: Context, private val fallback: PronunciationManager) : AutoCloseable {
    private val audio = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private var player: MediaPlayer? = null
    private var timeout: Runnable? = null
    private var focus: AudioFocusRequest? = null
    private val focusListener = AudioManager.OnAudioFocusChangeListener { if (it < 0) stop() }
    private val mutable = MutableStateFlow(false)
    val playing = mutable.asStateFlow()
    fun play(word: String, dialect: PronunciationDialect) {
        stop(); fallback.stop()
        val recording = MediaPlayer()
        player = recording; mutable.value = true
        fun failed() {
            if (player !== recording) return
            stop(); fallback.play(word, dialect)
        }
        timeout = Runnable { failed() }.also { handler.postDelayed(it, 8000) }
        try {
            recording.setAudioAttributes(attributes)
            val url = "https://dict.youdao.com/dictvoice".toHttpUrl().newBuilder()
                .addQueryParameter("type", if (dialect == PronunciationDialect.UK) "1" else "2")
                .addQueryParameter("audio", word).build()
            recording.setDataSource(url.toString())
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
                    if (granted != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { stop(); return@setOnPreparedListener }
                    timeout?.let(handler::removeCallbacks)
                    timeout = Runnable { failed() }.also { handler.postDelayed(it, 30000) }
                    runCatching { recording.start() }.onFailure { failed() }
                }
            }
            recording.setOnCompletionListener { if (player === recording) stop() }
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
        focus = null; mutable.value = false
    }
    override fun close() = stop()
}
