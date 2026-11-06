package com.teameow.teawords.data

import android.content.ContentValues
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class WordLearningTask(val word: String, val definition: String)

data class WordLearningSession(
    val id: String = UUID.randomUUID().toString(),
    val tasks: List<WordLearningTask>,
    val position: Int = 0,
    val correct: Int = 0,
    val practiced: Int = 0,
    val missed: List<String> = emptyList(),
    val elapsed: Long = 0
) {
    val complete: Boolean get() = position >= tasks.size
}

/** Recall evidence and the durable cursor advance together, including after a failed save. */
class WordLearningSessionRepository(private val helper: DatabaseHelper) {
    private val db by lazy {
        helper.writableDatabase.also {
            it.execSQL("CREATE TABLE IF NOT EXISTS word_learning_session (slot INTEGER PRIMARY KEY CHECK(slot=1), payload TEXT NOT NULL)")
        }
    }

    fun load(): WordLearningSession? = db.rawQuery(
        "SELECT payload FROM word_learning_session WHERE slot=1", null
    ).use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val json = JSONObject(cursor.getString(0))
        val tasks = json.getJSONArray("tasks")
        val missed = json.getJSONArray("missed")
        WordLearningSession(json.getString("id"), (0 until tasks.length()).map {
            val task = tasks.getJSONObject(it)
            WordLearningTask(task.getString("word"), task.getString("definition"))
        }, json.getInt("position"), json.getInt("correct"), json.getInt("practiced"),
            (0 until missed.length()).map { missed.getString(it) }, json.getLong("elapsed"))
    }

    fun start(tasks: List<WordLearningTask>): WordLearningSession {
        require(tasks.isNotEmpty() && tasks.all { it.definition.isNotBlank() })
        require(tasks.map { it.word }.distinct().size == tasks.size)
        db.beginTransaction()
        try {
            check(load()?.complete != false) { "请先继续未完成的单词学习" }
            val session = WordLearningSession(tasks = tasks)
            persist(session)
            db.setTransactionSuccessful()
            return session
        } finally { db.endTransaction() }
    }

    fun answer(id: String, position: Int, correct: Boolean, revealed: Boolean, elapsed: Long,
               saveReview: (String) -> Knowledge): Pair<WordLearningSession, Knowledge> {
        db.beginTransaction()
        try {
            val session = checkNotNull(load())
            check(session.id == id && session.position == position && !session.complete) {
                "学习进度已更新，请重新打开"
            }
            val word = session.tasks[position].word
            val updated = saveReview(word)
            val next = session.copy(
                position = position + 1,
                correct = session.correct + if (correct && !revealed) 1 else 0,
                practiced = session.practiced + if (correct && revealed) 1 else 0,
                missed = if (correct) session.missed else session.missed + word,
                elapsed = session.elapsed + elapsed.coerceIn(0, 300_000)
            )
            persist(next)
            db.setTransactionSuccessful()
            return next to updated
        } finally { db.endTransaction() }
    }

    private fun persist(session: WordLearningSession) {
        val json = JSONObject().apply {
            put("id", session.id)
            put("tasks", JSONArray().apply { session.tasks.forEach {
                put(JSONObject().put("word", it.word).put("definition", it.definition))
            } })
            put("position", session.position); put("correct", session.correct)
            put("practiced", session.practiced); put("missed", JSONArray(session.missed))
            put("elapsed", session.elapsed)
        }
        db.insertWithOnConflict("word_learning_session", null, ContentValues().apply {
            put("slot", 1); put("payload", json.toString())
        }, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE).also {
            check(it != -1L) { "学习进度保存失败" }
        }
    }
}
