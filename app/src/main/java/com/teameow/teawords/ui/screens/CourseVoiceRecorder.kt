package com.teameow.teawords.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.io.File

/** Local recording only: no upload, recognition, or automated pronunciation score. */
@Composable
internal fun CourseVoiceRecorder(lessonId: String, onHasRecordingChange: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val file = remember(lessonId) {
        File(File(context.filesDir, "course_recordings").apply { mkdirs() }, "$lessonId.m4a")
    }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var hasRecording by remember(lessonId) { mutableStateOf(file.exists() && file.length() > 0) }
    LaunchedEffect(hasRecording) { onHasRecordingChange(hasRecording) }
    var error by remember { mutableStateOf<String?>(null) }

    fun start() {
        error = null
        try {
            player?.release(); player = null
            val next = MediaRecorder()
            recorder = next
            next.setAudioSource(MediaRecorder.AudioSource.MIC)
            next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            next.setOutputFile(file.absolutePath)
            next.prepare()
            next.start()
            hasRecording = false
        } catch (e: Exception) {
            recorder?.release(); recorder = null
            file.delete()
            error = "录音无法开始：${e.message}"
        }
    }

    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start() else error = "未获得麦克风权限；可以跳过录音并自评。"
    }
    DisposableEffect(lessonId) { onDispose {
        recorder?.let { runCatching { it.stop() }; it.release() }
        player?.release()
    } }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = {
            if (recorder != null) {
                try {
                    recorder?.stop()
                    hasRecording = file.exists() && file.length() > 0
                } catch (e: Exception) { file.delete(); error = "录音未保存：${e.message}" }
                finally { recorder?.release(); recorder = null }
            } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) start()
            else requestPermission.launch(Manifest.permission.RECORD_AUDIO)
        }) { Text(if (recorder == null) "开始录音" else "结束录音") }
        if (hasRecording && recorder == null) OutlinedButton(onClick = {
            try {
                player?.release()
                player = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    prepare()
                    setOnCompletionListener { it.release(); player = null }
                    start()
                }
            } catch (e: Exception) { error = "回听失败：${e.message}" }
        }) { Text("回听") }
    }
    if (hasRecording && recorder == null) TextButton(onClick = {
        player?.release(); player = null
        file.delete(); hasRecording = false
    }) { Text("删除录音") }
    error?.let { TeaCaption(it) }
}
