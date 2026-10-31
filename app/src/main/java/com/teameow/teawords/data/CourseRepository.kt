package com.teameow.teawords.data

import android.content.ContentValues

/** Course progress is user data. It lives in teawords.db, never in a replaceable content pack. */
class CourseRepository(private val helper: DatabaseHelper) {
    private val db get() = helper.writableDatabase

    init {
        db.execSQL("CREATE TABLE IF NOT EXISTS course_progress (lesson_id TEXT PRIMARY KEY, step INTEGER NOT NULL DEFAULT 0, mistakes TEXT NOT NULL DEFAULT '', updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS course_answers (lesson_id TEXT NOT NULL, step INTEGER NOT NULL, word TEXT NOT NULL, correct INTEGER NOT NULL, elapsed_ms INTEGER NOT NULL, timestamp INTEGER NOT NULL, PRIMARY KEY(lesson_id,step))")
        db.execSQL("CREATE TABLE IF NOT EXISTS course_weak_words (word TEXT PRIMARY KEY, mistakes INTEGER NOT NULL, updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS course_baseline (slot INTEGER PRIMARY KEY CHECK(slot=1), started_at INTEGER NOT NULL)")
        db.execSQL("INSERT OR IGNORE INTO course_baseline(slot,started_at) VALUES(1,${System.currentTimeMillis()})")
    }

    data class Progress(val step: Int = 0, val mistakes: Set<String> = emptySet()) {
        val complete: Boolean get() = step >= STEPS
    }

    fun progress(id: String): Progress = db.rawQuery(
        "SELECT step,mistakes FROM course_progress WHERE lesson_id=?", arrayOf(id)
    ).use { cursor ->
        if (!cursor.moveToFirst()) Progress() else Progress(cursor.getInt(0),
            cursor.getString(1).split(',').filter { it.isNotBlank() }.toSet())
    }

    fun allProgress(): Map<String, Progress> = db.rawQuery(
        "SELECT lesson_id,step,mistakes FROM course_progress", null
    ).use { cursor -> buildMap {
        while (cursor.moveToNext()) put(cursor.getString(0), Progress(cursor.getInt(1),
            cursor.getString(2).split(',').filter { it.isNotBlank() }.toSet()))
    } }

    /** Incorrect lesson questions in their original order; reading them does not alter review state. */
    fun wrongSteps(lessonId: String): List<Int> = db.rawQuery(
        "SELECT step FROM course_answers WHERE lesson_id=? AND step BETWEEN 1 AND 7 AND correct=0 ORDER BY step",
        arrayOf(lessonId)
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getInt(0)) } }

    /** One answer and its cursor are committed together; reopening cannot double-count a step. */
    fun answer(lesson: CourseLesson, expectedStep: Int, correct: Boolean, elapsedMs: Long): Progress {
        require(expectedStep in 0 until STEPS)
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            val old = progress(lesson.id)
            check(old.step == expectedStep) { "课程进度已变化，请重新打开" }
            val assessed = expectedStep != 0
            val mistakes = if (assessed && !correct) old.mistakes + lesson.focus else old.mistakes
            db.insertWithOnConflict("course_answers", null, ContentValues().apply {
                put("lesson_id", lesson.id); put("step", expectedStep); put("word", lesson.focus)
                put("correct", if (correct) 1 else 0); put("elapsed_ms", elapsedMs.coerceAtLeast(0))
                put("timestamp", now)
            }, android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT)
            db.insertWithOnConflict("course_progress", null, ContentValues().apply {
                put("lesson_id", lesson.id); put("step", expectedStep + 1)
                put("mistakes", mistakes.joinToString(",")); put("updated_at", now)
            }, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
            if (assessed && !correct) {
                db.execSQL("INSERT OR IGNORE INTO course_weak_words(word,mistakes,updated_at) VALUES(?,0,?)",
                    arrayOf<Any>(lesson.focus, now))
                db.execSQL("UPDATE course_weak_words SET mistakes=mistakes+1,updated_at=? WHERE word=?",
                    arrayOf<Any>(now, lesson.focus))
            }
            db.setTransactionSuccessful()
            return Progress(expectedStep + 1, mistakes)
        } finally { db.endTransaction() }
    }

    fun weakWords(limit: Int = 20): List<String> = db.rawQuery(
        "SELECT word FROM course_weak_words ORDER BY mistakes DESC,updated_at DESC LIMIT ?",
        arrayOf(limit.toString())
    ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    data class RecallComparison(val beforeCorrect: Int, val beforeTotal: Int,
                                val afterCorrect: Int, val afterTotal: Int) {
        val beforePercent: Int get() = if (beforeTotal == 0) 0 else beforeCorrect * 100 / beforeTotal
        val afterPercent: Int get() = if (afterTotal == 0) 0 else afterCorrect * 100 / afterTotal
    }

    /** Same active-recall answer definition on both sides of first course use; local and descriptive. */
    fun recallComparison(): RecallComparison {
        val start = db.rawQuery("SELECT started_at FROM course_baseline WHERE slot=1", null).use {
            it.moveToFirst(); it.getLong(0)
        }
        fun counts(from: Long, to: Long): Pair<Int, Int> = db.rawQuery(
            "SELECT COALESCE(SUM(correct),0),COUNT(*) FROM learning_events " +
                "WHERE mode='RECALL' AND correct IS NOT NULL AND timestamp>=? AND timestamp<?",
            arrayOf(from.toString(), to.toString())
        ).use { c -> c.moveToFirst(); c.getInt(0) to c.getInt(1) }
        val before = counts(start - 7L * 86_400_000L, start)
        val after = counts(start, System.currentTimeMillis() + 1)
        return RecallComparison(before.first, before.second, after.first, after.second)
    }

    companion object { const val STEPS = 8 }
}
