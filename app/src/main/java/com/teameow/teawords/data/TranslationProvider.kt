package com.teameow.teawords.data

interface TranslationProvider {
    suspend fun translate(text: String, source: String, target: String, onProgress: (TranslationProgress) -> Unit = {}): String
    suspend fun isAvailable(): Boolean
    suspend fun prepare()
    fun release()
}
