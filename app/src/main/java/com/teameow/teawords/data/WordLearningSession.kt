package com.teameow.teawords.data

import android.content.ContentValues
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class WordLearningTask(val word: String, val definition: String)

data class WordExerciseProgress(
    val completed: Int = 0, val attempts: Int = 0, val assisted: Boolean = false,
    val elapsed: Long = 0, val spellingMillis: Long = 0, val introductionKnown: Boolean? = null,
    val settled: Boolean = false
)

data class WordLearningSession(
    val id: String = UUID.randomUUID().toString(),
    val tasks: List<WordLearningTask>,
    val position: Int = 0,
    val correct: Int = 0,
    val practiced: Int = 0,
    val missed: List<String> = emptyList(),
    val elapsed: Long = 0,
    val exerciseVersion: Int = 1,
    val exercises: Map<String, WordExerciseProgress> = emptyMap(),
    val phase: Int = 0
) {
    val complete: Boolean get() = position >= tasks.size
    val completedWords: Int get() = if (exerciseVersion >= 3) exercises.values.count { it.settled } else position
    val completedStages: Int get() = exercises.values.sumOf { it.completed }
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
        val exercises = json.optJSONObject("exercises")
        WordLearningSession(json.getString("id"), (0 until tasks.length()).map {
            val task = tasks.getJSONObject(it)
            WordLearningTask(task.getString("word"), task.getString("definition"))
        }, json.getInt("position"), json.getInt("correct"), json.getInt("practiced"),
            (0 until missed.length()).map { missed.getString(it) }, json.getLong("elapsed"),
            json.optInt("exerciseVersion", 1), exercises?.keys()?.asSequence()?.associateWith { word ->
                val step = exercises.getJSONObject(word)
                WordExerciseProgress(step.getInt("completed"), step.getInt("attempts"), step.getBoolean("assisted"),
                    step.getLong("elapsed"), step.optLong("spellingMillis", 0),
                    if (step.has("introductionKnown")) step.getBoolean("introductionKnown") else null,
                    step.optBoolean("settled", false))
            }.orEmpty(), json.optInt("phase", 0))
    }

    fun start(tasks: List<WordLearningTask>, threeStep: Boolean = false): WordLearningSession {
        require(tasks.isNotEmpty() && tasks.all { it.definition.isNotBlank() })
        require(tasks.map { it.word }.distinct().size == tasks.size)
        db.beginTransaction()
        try {
            check(load()?.complete != false) { "请先继续未完成的单词学习" }
            val session = WordLearningSession(tasks = tasks, exerciseVersion = if (threeStep) 3 else 1)
            persist(session)
            db.setTransactionSuccessful()
            return session
        } finally { db.endTransaction() }
    }

    /** Upgrade just the unfinished part of an old round; all completed words stay completed. */
    fun enableThreeSteps(id: String): WordLearningSession = mutate(id) { session ->
        if (session.exerciseVersion >= 3) session else {
            val done = session.tasks.take(session.position).associate { task ->
                task.word to (session.exercises[task.word] ?: WordExerciseProgress()).copy(completed = 3, settled = true)
            }
            nextTurn(session.copy(exerciseVersion = 3, exercises = session.exercises + done, phase = 0), from = 0)
        }
    }

    /** Each pass visits the whole group before a word returns for its next kind of practice. */
    private fun nextTurn(session: WordLearningSession, from: Int): WordLearningSession {
        for (phase in session.phase..2) {
            val start = if (phase == session.phase) from else 0
            val next = ((start until session.tasks.size) + (0 until start.coerceAtMost(session.tasks.size))).firstOrNull { index ->
                val progress = session.exercises[session.tasks[index].word] ?: WordExerciseProgress()
                !progress.settled && (phase == 2 || progress.completed <= phase)
            }
            if (next != null) return session.copy(position = next, phase = phase)
        }
        return session.copy(position = session.tasks.size, phase = 2)
    }

    fun retryLater(id: String, position: Int): WordLearningSession = mutate(id) { session ->
        check(session.exerciseVersion >= 3 && session.position == position && !session.complete)
        nextTurn(session, position + 1)
    }

    fun markAssisted(id: String, position: Int): WordLearningSession = mutate(id) { session ->
        check(session.position == position && !session.complete)
        val word = session.tasks[position].word
        val progress = session.exercises[word] ?: WordExerciseProgress()
        session.copy(exercises = session.exercises + (word to progress.copy(assisted = true)))
    }

    /** Self-report opens teaching material; it is neither a test nor independent recall evidence. */
    fun introduce(id: String, position: Int, known: Boolean): WordLearningSession = mutate(id) { session ->
        check(session.exerciseVersion >= 2 && session.position == position && !session.complete)
        val word = session.tasks[position].word
        val progress = session.exercises[word] ?: WordExerciseProgress()
        check(progress.completed == 0)
        if (progress.introductionKnown != null) session else session.copy(
            exercises = session.exercises + (word to progress.copy(introductionKnown = known, assisted = true)))
    }

    /** A replayed tap cannot grant another point; credit belongs to a distinct exercise step. */
    fun exercise(id: String, position: Int, step: Int, attempt: Int, correct: Boolean,
                 assisted: Boolean, elapsed: Long): WordLearningSession = mutate(id) { session ->
        check(session.exerciseVersion >= 2 && session.position == position && !session.complete)
        val word = session.tasks[position].word
        val progress = session.exercises[word] ?: WordExerciseProgress()
        check(step in 0..2 && step == progress.completed && attempt == progress.attempts) { "题目已更新，请继续当前练习" }
        check(session.exerciseVersion < 3 || step == session.phase) { "请先确认当前题目，再继续下一轮" }
        check(step != 0 || progress.introductionKnown != null) { "请先认读本词，再开始巩固" }
        val time = elapsed.coerceIn(0, 300_000)
        val passed = correct && !assisted
        val next = progress.copy(completed = progress.completed + if (passed) 1 else 0,
            attempts = progress.attempts + 1, assisted = progress.assisted || assisted || !correct,
            elapsed = progress.elapsed + time,
            spellingMillis = if (step == 2 && passed) time else progress.spellingMillis)
        val updated = session.copy(exercises = session.exercises + (word to next),
            missed = if (!correct || assisted) (session.missed + word).distinct() else session.missed)
        // Recognition advances immediately; a choice answer stays visible until explicit confirmation.
        if (session.exerciseVersion >= 3 && passed && step == 0) nextTurn(updated, position + 1) else updated
    }

    private fun mutate(id: String, change: (WordLearningSession) -> WordLearningSession): WordLearningSession {
        db.beginTransaction()
        try {
            val session = checkNotNull(load())
            check(session.id == id) { "学习轮次已更新" }
            val next = change(session)
            persist(next)
            db.setTransactionSuccessful()
            return next
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
            if (session.exerciseVersion >= 2) check(session.exercises[word]?.completed == 3) { "请先完成本词的三项练习" }
            check(session.exerciseVersion < 3 || session.phase == 2)
            val updated = saveReview(word)
            val updatedSession = session.copy(
                position = position + 1,
                correct = session.correct + if (correct && !revealed) 1 else 0,
                practiced = session.practiced + if (correct && revealed) 1 else 0,
                missed = if (correct) session.missed else session.missed + word,
                elapsed = session.elapsed + elapsed.coerceIn(0, 300_000),
                exercises = if (session.exerciseVersion >= 3) session.exercises +
                    (word to session.exercises.getValue(word).copy(settled = true)) else session.exercises
            )
            val next = if (session.exerciseVersion >= 3) nextTurn(updatedSession, position + 1) else updatedSession
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
            put("exerciseVersion", session.exerciseVersion)
            put("phase", session.phase)
            put("exercises", JSONObject().apply { session.exercises.forEach { (word, progress) ->
                put(word, JSONObject().put("completed", progress.completed).put("attempts", progress.attempts)
                    .put("assisted", progress.assisted).put("elapsed", progress.elapsed).put("spellingMillis", progress.spellingMillis)
                    .put("settled", progress.settled)
                    .apply { progress.introductionKnown?.let { put("introductionKnown", it) } })
            } })
        }
        db.insertWithOnConflict("word_learning_session", null, ContentValues().apply {
            put("slot", 1); put("payload", json.toString())
        }, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE).also {
            check(it != -1L) { "学习进度保存失败" }
        }
    }
}
