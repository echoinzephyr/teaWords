package com.teameow.teawords.ui.screens

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.teameow.teawords.data.CourseLesson
import kotlinx.coroutines.delay
import java.io.File

/** One native player per visible lesson. Focus/background never resumes playback automatically. */
internal class CourseAudioPlayer(context: Context, private val lesson: CourseLesson) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private val focusListener = AudioManager.OnAudioFocusChangeListener { if (it != AudioManager.AUDIOFOCUS_GAIN) pause() }
    private var player: MediaPlayer? = null
    var ready by mutableStateOf(false); private set
    var playing by mutableStateOf(false); private set
    var position by mutableIntStateOf(0); private set
    var sentence by mutableIntStateOf(0); private set
    var error by mutableStateOf<String?>(null); private set
    var speed by mutableFloatStateOf(1f)
    var loop by mutableStateOf(false)
    private var stopAt: Int? = null
    private var segmentIndex: Int? = null
    private var loopStart = 0
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()

    fun prepare(file: File, savedPosition: Int) {
        release(); error = null
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(attributes); setDataSource(file.absolutePath)
                setOnPreparedListener { prepared ->
                    if (player === prepared) { ready = true; seek(savedPosition) }
                }
                setOnCompletionListener { completed ->
                    if (player === completed) { playing = false; abandonFocus() }
                }
                setOnErrorListener { failed, _, _ ->
                    if (player === failed) {
                        error = "原声暂时无法播放，请重试"
                        ready = false; playing = false; abandonFocus()
                    }
                    true
                }
                prepareAsync()
            }
        } catch (_: Exception) { error = "原声暂时无法播放，请重试"; ready = false }
    }
    fun play(segment: Int? = null): Boolean {
        val current = player ?: return false
        if (!ready) return false
        if (!requestFocus()) { error = "其他应用正在使用音频，请稍后重试"; return false }
        return try {
            error = null
            stopAt = segment?.let { lesson.segments[it].endMs }
            segmentIndex = segment
            if (segment != null) {
                sentence = segment; loopStart = lesson.segments[segment].startMs; seekTo(loopStart)
            } else if (position >= lesson.audio.durationMs - 150) seek(0)
            current.playbackParams = current.playbackParams.setSpeed(speed).setPitch(1f)
            current.start(); playing = true; true
        } catch (_: Exception) { error = "原声暂时无法播放，请重试"; pause(); false }
    }
    fun seek(value: Int) {
        segmentIndex = null; stopAt = null
        seekTo(value)
    }
    private fun seekTo(value: Int) {
        position = value.coerceIn(0, lesson.audio.durationMs)
        if (ready) runCatching { player?.seekTo(position) }
    }
    fun tick() {
        if (!ready) return
        position = runCatching { player?.currentPosition ?: position }.getOrDefault(position)
        if (playing && stopAt?.let { position >= it } == true) {
            if (loop) seekTo(loopStart) else pause()
        }
        val active = segmentIndex ?: lesson.segments.indexOfLast { position >= it.startMs }
        if (active >= 0) sentence = active
    }
    fun changeSpeed(value: Float) {
        speed = value
        if (playing) runCatching { player?.let { it.playbackParams = it.playbackParams.setSpeed(value).setPitch(1f) } }
            .onFailure { error = "此设备暂不支持该倍速"; pause() }
    }
    fun pause() {
        if (ready && playing) runCatching { player?.pause() }
        playing = false; abandonFocus()
    }
    fun release() {
        pause(); player?.release(); player = null; ready = false
    }
    private fun requestFocus(): Boolean = if (Build.VERSION.SDK_INT >= 26) {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes).setOnAudioFocusChangeListener(focusListener).build()
        focusRequest = request
        audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    } else {
        @Suppress("DEPRECATION")
        (audio.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
    }
    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= 26) focusRequest?.let { audio.abandonAudioFocusRequest(it) }
        else { @Suppress("DEPRECATION") audio.abandonAudioFocus(focusListener) }
        focusRequest = null
    }
}

@Composable
internal fun rememberCourseAudioPlayer(lesson: CourseLesson, onPause: (CourseAudioPlayer) -> Unit): CourseAudioPlayer {
    val context = LocalContext.current
    val player = remember(lesson.id) { CourseAudioPlayer(context, lesson) }
    val owner = LocalLifecycleOwner.current
    val save by rememberUpdatedState(onPause)
    DisposableEffect(player, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) { player.tick(); player.pause(); save(player) }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { player.tick(); save(player); player.release(); owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(player) { while (true) { player.tick(); delay(60) } }
    return player
}
