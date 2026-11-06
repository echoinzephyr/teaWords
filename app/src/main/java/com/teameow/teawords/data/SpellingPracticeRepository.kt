package com.teameow.teawords.data

import android.content.ContentValues
import com.teameow.teawords.algorithm.*

data class SpellingPracticeRecord(val state: Knowledge, val verdict: ReviewVerdict)

/** Optional spelling updates memory once, independently of the three-pass round cursor. */
class SpellingPracticeRepository(private val helper: DatabaseHelper) {
    fun record(token: String, word: String, correct: Boolean, assisted: Boolean, elapsed: Long,
               now: Long, ability: AbilityEstimate, retention: Double): SpellingPracticeRecord? {
        require(token.isNotBlank())
        val db = helper.writableDatabase
        db.execSQL("CREATE TABLE IF NOT EXISTS spelling_practice_receipts (token TEXT PRIMARY KEY, word TEXT NOT NULL)")
        db.beginTransaction()
        try {
            val duplicate = db.rawQuery("SELECT word FROM spelling_practice_receipts WHERE token=?", arrayOf(token)).use {
                if (it.moveToFirst()) it.getString(0) else null
            }
            if (duplicate != null) { check(duplicate == word); db.setTransactionSuccessful(); return null }
            val learning = LearningRepository(helper)
            val senses = SenseRepository(helper)
            val ids = senses.ensurePrimarySense(word)
            val state = learning.states(listOf(word)).first { it.word == word }
            val previous = senses.memoryFor(ids.second)
            val memory = previous?.let { MemoryState(it.difficulty, it.stability, it.lastReview, it.reps, it.lapses) } ?: MemoryState.unseen
            val belief = learning.beliefFor(state, ids.second, now)
            val coordinator = ReviewCoordinator()
            val verdict = if (assisted) coordinator.gradeRecognition(belief, memory, ability, correct, now, retention)
                else coordinator.grade(belief, memory, ability, 0.0, correct, false, elapsed, now, retention)
            val saved = learning.saveReview(state, verdict, ids.first, ids.second, correct, assisted,
                elapsed.coerceIn(0, 300_000), now, ability.theta, mode = if (assisted) "POCKET_SPELLING" else "SPELLING")
            check(db.insertOrThrow("spelling_practice_receipts", null, ContentValues().apply {
                put("token", token); put("word", word)
            }) != -1L)
            db.setTransactionSuccessful()
            return SpellingPracticeRecord(saved, verdict)
        } finally { db.endTransaction() }
    }
}
