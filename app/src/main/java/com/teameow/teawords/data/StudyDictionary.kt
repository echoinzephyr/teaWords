package com.teameow.teawords.data

data class StudyDictionaryEntry(
    val definition: String, val uk: String, val us: String,
    val phrases: List<Pair<String, String>>, val forms: List<Pair<String, String>>
)

/** Legacy disk entries remain readable. New answers only live in the shared memory cache. */
class StudyDictionary(private val helper: DatabaseHelper) {
    fun cached(word: String): StudyDictionaryEntry? = runCatching {
        val exists = helper.readableDatabase.rawQuery("SELECT 1 FROM sqlite_master WHERE name='study_dictionary_cache'", null).use { it.moveToFirst() }
        if (!exists) return@runCatching null
        helper.readableDatabase.rawQuery("SELECT payload FROM study_dictionary_cache WHERE word=?",
            arrayOf(LexicalText.normalize(word))).use { if (it.moveToFirst()) parse(it.getString(0)) else null }
    }.getOrNull()
    suspend fun lookup(word: String): StudyDictionaryEntry = cached(word)
        ?: OnlineDictionary.shared.lookup(word).entry?.detail ?: error("未查询到词义")
    companion object { fun parse(payload: String) = OnlineDictionary.parse(payload) }
}
