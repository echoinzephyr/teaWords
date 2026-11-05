package com.teameow.teawords.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.google.gson.Gson
import java.io.File

/** Course-only storage. Never writes vocabulary, knowledge, notes or learning_events. */
class CourseRepository(private val helper: DatabaseHelper) {
    private val db get() = helper.writableDatabase
    private val gson = Gson()
    init {
        db.beginTransaction()
        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS course_v2_state (lesson_id TEXT PRIMARY KEY,payload TEXT NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS course_v2_answers (lesson_id TEXT NOT NULL,question_id TEXT NOT NULL,input TEXT NOT NULL,correct INTEGER NOT NULL,attempt INTEGER NOT NULL,PRIMARY KEY(lesson_id,question_id))")
            // DEV reset: only the four tables owned by the retired course feature.
            listOf("course_progress", "course_answers", "course_weak_words", "course_baseline").forEach {
                db.execSQL("DROP TABLE IF EXISTS $it")
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        File(helper.appContext.filesDir, "course_recordings").listFiles()?.filter {
            it.isFile && it.name.matches(Regex("(ky|ielts|toefl)-[0-9]+\\.m4a"))
        }?.forEach { it.delete() }
    }

    data class Checkpoint(val section: Int = 0, val sentence: Int = 0, val positionMs: Int = 0,
                          val question: Int = 0, val draft: String = "", val started: Boolean = false,
                          val speed: Float = 1f, val follow: Boolean = true,
                          val listIndex: Int = 0, val listOffset: Int = 0,
                          val guideVersion: Int = 0, val guidedStage: Int = 0, val point: Int = 0,
                          val drafts: Map<String, String> = emptyMap(), val hinted: List<String> = emptyList(),
                          val roundIds: List<String> = emptyList(), val roundDone: List<String> = emptyList(),
                          val reviewAt: Long = 0, val reviewCount: Int = 0, val reviewRound: Boolean = false)
    data class Answer(val input: String, val correct: Boolean, val attempt: Int)
    data class Progress(val checkpoint: Checkpoint = Checkpoint(), val answers: Map<String, Answer> = emptyMap(),
                        val total: Int = 0) {
        val attemptedAll get() = total > 0 && answers.size == total
        val complete get() = attemptedAll && mistakes.isEmpty()
        val step get() = if (checkpoint.started) answers.size + 1 else 0
        val mistakes get() = answers.filterValues { !it.correct }.keys
    }
    fun progress(id: String): Progress {
        val checkpoint = db.rawQuery("SELECT payload FROM course_v2_state WHERE lesson_id=?", arrayOf(id)).use {
            if (it.moveToFirst()) gson.fromJson(it.getString(0), Checkpoint::class.java) else Checkpoint()
        }
        val answers = db.rawQuery("SELECT question_id,input,correct,attempt FROM course_v2_answers WHERE lesson_id=?", arrayOf(id)).use {
            buildMap { while (it.moveToNext()) put(it.getString(0), Answer(it.getString(1), it.getInt(2) != 0, it.getInt(3))) }
        }
        val lesson = CourseCatalog.all.firstOrNull { it.id == id }
        val active = lesson?.questions?.map { it.id }?.toSet().orEmpty()
        return Progress(checkpoint, answers.filterKeys { it in active }, active.size)
    }
    fun allProgress(): Map<String, Progress> = CourseCatalog.all.associate { it.id to progress(it.id) }
    fun save(lessonId: String, checkpoint: Checkpoint) {
        require(CourseCatalog.all.any { it.id == lessonId })
        db.insertWithOnConflict("course_v2_state", null, ContentValues().apply {
            put("lesson_id", lessonId); put("payload", gson.toJson(checkpoint))
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }
    /** Revision check makes rapid duplicate submits idempotent, including during retries. */
    fun submit(lesson: CourseLesson, questionId: String, input: String, expectedAttempt: Int): Progress {
        val question = lesson.questions.single { it.id == questionId }
        require(input.isNotBlank() && expectedAttempt >= 0)
        db.beginTransaction()
        try {
            val old = progress(lesson.id)
            if ((old.answers[questionId]?.attempt ?: 0) == expectedAttempt) {
                db.insertWithOnConflict("course_v2_answers", null, ContentValues().apply {
                    put("lesson_id", lesson.id); put("question_id", questionId); put("input", input)
                    put("correct", if (question.accepts(input)) 1 else 0); put("attempt", expectedAttempt + 1)
                }, SQLiteDatabase.CONFLICT_REPLACE)
                save(lesson.id, old.checkpoint.copy(started = true, draft = ""))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return progress(lesson.id)
    }
}

/** Short-course retrieval reminders only; separate from the word learning scheduler. */
object CourseReviewTiming {
    fun next(now: Long, completedReviews: Int) = now + when (completedReviews) {
        0 -> 1L; 1 -> 3L; else -> 7L
    } * 24 * 60 * 60 * 1000
    fun due(progress: CourseRepository.Progress, now: Long) = progress.complete &&
        progress.checkpoint.reviewAt in 1..now
}
