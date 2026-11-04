package com.teameow.teawords.data

import android.content.Context
import com.teameow.teawords.data.search.DictionaryGateway
import com.teameow.teawords.data.search.SearchFilter

/** Reads downloaded book metadata; no duplicate bundled TXT lists or speculative exam labels. */
class WordLevelProvider(@Suppress("UNUSED_PARAMETER") context: Context) {
    fun loadIfNeeded() = Unit
    fun getLevels(word: String): List<String> = DictionaryGateway.sourceOrNull()
        ?.exact(LexicalText.normalize(word), SearchFilter())?.firstOrNull()?.books.orEmpty()
        .map { LearningWordbook.fromId(it)?.subtitle ?: it }
    fun searchPrefix(prefix: String, limit: Int = 8): List<String> {
        val query = LexicalText.normalize(prefix)
        if (query.isBlank()) return emptyList()
        return DictionaryGateway.sourceOrNull()?.prefix(query, query + '\uffff', SearchFilter(limit = limit))
            .orEmpty().map { it.word }.distinct().take(limit)
    }
    fun getWordbookItems(bookIds: Set<String>, limitPerBook: Int = 80): List<VocabularyItem> =
        DictionaryGateway.sourceOrNull()?.learningWords(bookIds).orEmpty().map {
            VocabularyItem(word = it.word, phonetic = it.phonetic, definition = it.zh.ifBlank { it.en }.replace("\\n", "\n"), timestamp = 0L)
        }.shuffled()
}
