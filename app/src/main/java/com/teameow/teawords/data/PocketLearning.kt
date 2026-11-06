package com.teameow.teawords.data

import android.content.ContentValues
import com.teameow.teawords.algorithm.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class PocketKind(val label: String) {
    STUDY("认识一下"), MEANING("选词义"), LISTEN("听音选词"), MATCH("词义配对"), CLOZE("语境填空"), SPELLING("拼写填空"), TYPING("整词默写")
}

data class PocketTask(
    val word: String, val definition: String, val kind: PocketKind,
    val options: List<String> = emptyList(), val prompt: String = "",
    val taught: Boolean = false, val group: Int = 0
) {
    val answer: String get() = when (kind) {
        PocketKind.MEANING, PocketKind.MATCH -> definition
        PocketKind.SPELLING -> word.filterIndexed { index, _ -> prompt.getOrNull(index) == '_' }
        PocketKind.TYPING -> word
        else -> word
    }
}

data class PocketRound(
    val id: String, val tasks: List<PocketTask>, val done: Set<Int> = emptySet(),
    val correct: Int = 0, val elapsed: Long = 0, val missed: Set<Int> = emptySet(),
    val pairings: Map<Int, Int> = emptyMap()
) {
    val complete get() = done.size == tasks.size
    val position get() = tasks.indices.firstOrNull { it !in done } ?: tasks.size
    val wordCount get() = tasks.map { it.word }.distinct().size
    val exerciseCount get() = tasks.count { it.kind != PocketKind.STUDY }
}

/** Snapshot both prompts and choices so a resumed round survives changing books. */
object PocketPlanner {
    const val DEFAULT_WORD_LIMIT = 5
    const val MAX_WORD_LIMIT = 100
    /** A full-word typing round uses the same due-first selection and daily new-word cap. */
    fun typingRound(states: List<Knowledge>, words: Map<String, VocabularyItem>, newBudget: Int,
                    now: Long, wordLimit: Int = 10, preferredWords: List<String> = emptyList()): PocketRound? {
        val eligible = states.filter { !words[it.word]?.definition.isNullOrBlank() }
        val due = eligible.filter { it.attempts > 0 && it.due <= now }.sortedBy { it.due }.take(wordLimit)
        val rank = preferredWords.withIndex().associate { it.value to it.index }
        val fresh = eligible.filter { it.attempts == 0 && it.report != SelfReport.KNOWN }
            .sortedWith(compareBy<Knowledge> { rank[it.word] ?: Int.MAX_VALUE }.thenBy { it.word })
            .take(minOf(wordLimit - due.size, newBudget.coerceAtLeast(0)))
        val tasks = (due + fresh).mapNotNull { state -> words[state.word]?.let { item ->
            PocketTask(item.word, LexicalText.splitSenses(item.definition).firstOrNull()?.text ?: item.definition,
                PocketKind.TYPING)
        } }
        return tasks.takeIf { it.isNotEmpty() }?.let { PocketRound(UUID.randomUUID().toString(), it) }
    }
    fun spellingPrompt(word: String): String {
        val letters = word.indices.filter { word[it].isLetter() }
        val holes = letters.shuffled().take(minOf(letters.size, if (letters.size < 5) 1 else if (letters.size < 9) 2 else 3)).toSet()
        return word.mapIndexed { index, char -> if (index in holes) '_' else char }.joinToString("")
    }
    fun build(states: List<Knowledge>, words: Map<String, VocabularyItem>, newBudget: Int, now: Long,
              preferredNewWords: List<String> = emptyList(), wordLimit: Int = DEFAULT_WORD_LIMIT): PocketRound? {
        val limit = wordLimit.coerceIn(1, MAX_WORD_LIMIT)
        val rank = preferredNewWords.withIndex().associate { it.value to it.index }
        val eligible = states.filter { !words[it.word]?.definition.isNullOrBlank() }
        val reviews = eligible.filter { it.attempts > 0 && it.due <= now }
            .sortedWith(compareBy<Knowledge> { it.due }.thenBy { it.streak }).take(limit)
        val fresh = eligible.filter { it.attempts == 0 && it.report != SelfReport.KNOWN }
            .sortedWith(compareBy<Knowledge> { if (it.report == SelfReport.UNKNOWN || it.report == SelfReport.FUZZY) 0 else 1 }
                .thenBy { rank[it.word] ?: Int.MAX_VALUE })
            .take(minOf(limit - reviews.size, newBudget.coerceAtLeast(0)))
        fun primary(item: VocabularyItem) = item.copy(definition =
            LexicalText.splitSenses(item.definition).firstOrNull()?.text ?: item.definition)
        val selected = (reviews + fresh).mapNotNull { words[it.word]?.let(::primary) }
        if (selected.isEmpty()) return null
        val taught = fresh.map { it.word }.toSet()
        val bank = words.values.filter { it.definition.isNotBlank() }.map(::primary).distinctBy { it.definition }
        val tasks = mutableListOf<PocketTask>()
        selected.filter { it.word in taught }.forEach {
            tasks += PocketTask(it.word, it.definition, PocketKind.STUDY, taught = true)
        }
        val match = selected.take(3).distinctBy { it.definition }.takeIf { it.size >= 2 }.orEmpty()
        match.forEach { tasks += PocketTask(it.word, it.definition, PocketKind.MATCH,
            taught = it.word in taught, group = 1) }
        selected.forEachIndexed { index, item ->
            val example = OfflineQuestionBank().getQuestionByWord(item.word)
            val kind = when {
                index % 3 == 2 && example != null && example.correctAnswer.equals(item.word, true) -> PocketKind.CLOZE
                index % 3 == 0 -> PocketKind.SPELLING
                index % 2 == 1 -> PocketKind.LISTEN
                else -> PocketKind.MEANING
            }
            val decoys = bank.filter { it.word != item.word && it.definition != item.definition }.shuffled().take(3)
            // A one-word personal book can be studied; it cannot supply a meaningful choice question.
            if (kind == PocketKind.SPELLING) {
                tasks += PocketTask(item.word, item.definition, kind, prompt = spellingPrompt(item.word), taught = item.word in taught)
            } else if (decoys.isNotEmpty()) {
                val options = (if (kind == PocketKind.MEANING) decoys.map { it.definition } + item.definition
                    else decoys.map { it.word } + item.word).distinct().shuffled()
                tasks += PocketTask(item.word, item.definition, kind, options,
                    example?.blankedSentence.orEmpty(), item.word in taught)
            } else if (item.word !in taught) {
                tasks += PocketTask(item.word, item.definition, PocketKind.STUDY)
            }
        }
        return PocketRound(UUID.randomUUID().toString(), tasks)
    }
}

/** One checkpoint; answer evidence and cursor commit in the same SQLite transaction. */
class PocketLearningRepository(private val helper: DatabaseHelper) {
    private val db by lazy {
        helper.writableDatabase.also {
            it.execSQL("CREATE TABLE IF NOT EXISTS pocket_round (slot INTEGER PRIMARY KEY CHECK(slot=1), payload TEXT NOT NULL)")
            it.execSQL("CREATE TABLE IF NOT EXISTS typing_attempts (round_id TEXT NOT NULL, task_index INTEGER NOT NULL, word TEXT NOT NULL, errors INTEGER NOT NULL, elapsed_ms INTEGER NOT NULL, chars INTEGER NOT NULL, timestamp INTEGER NOT NULL, PRIMARY KEY(round_id, task_index))")
        }
    }

    data class TypingStats(val words: Int, val errors: Int, val chars: Int, val elapsedMs: Long) {
        val accuracy: Int get() = if (chars + errors == 0) 0 else chars * 100 / (chars + errors)
        val wordsPerMinute: Int get() = if (elapsedMs <= 0) 0 else ((chars / 5.0) * 60_000 / elapsedMs).toInt()
    }

    fun typingStats(roundId: String): TypingStats = db.rawQuery(
        "SELECT COUNT(*), COALESCE(SUM(errors),0), COALESCE(SUM(chars),0), COALESCE(SUM(elapsed_ms),0) FROM typing_attempts WHERE round_id=?",
        arrayOf(roundId)
    ).use { c -> c.moveToFirst(); TypingStats(c.getInt(0), c.getInt(1), c.getInt(2), c.getLong(3)) }

    fun load(): PocketRound? = db.rawQuery("SELECT payload FROM pocket_round WHERE slot=1", null).use {
        if (!it.moveToFirst()) return@use null
        val json = JSONObject(it.getString(0))
        val rows = json.getJSONArray("tasks")
        val tasks = (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            val choices = row.getJSONArray("options")
            PocketTask(row.getString("word"), row.getString("definition"), PocketKind.valueOf(row.getString("kind")),
                (0 until choices.length()).map { choices.getString(it) }, row.getString("prompt"),
                row.getBoolean("taught"), row.getInt("group"))
        }
        val done = json.getJSONArray("done")
        val missed = json.optJSONArray("missed") ?: JSONArray()
        val pairings = json.optJSONObject("pairings") ?: JSONObject()
        PocketRound(json.getString("id"), tasks, (0 until done.length()).map { done.getInt(it) }.toSet(),
            json.getInt("correct"), json.getLong("elapsed"), (0 until missed.length()).map { missed.getInt(it) }.toSet(),
            pairings.keys().asSequence().associate { it.toInt() to pairings.getInt(it) })
    }

    fun start(round: PocketRound) {
        db.beginTransaction()
        try {
            check(load()?.complete != false) { "还有未完成的小练习，请先继续" }
            persist(round)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    private fun persist(round: PocketRound) {
        val rows = JSONArray()
        round.tasks.forEach { task -> rows.put(JSONObject().apply {
            put("word", task.word); put("definition", task.definition); put("kind", task.kind.name)
            put("options", JSONArray(task.options)); put("prompt", task.prompt)
            put("taught", task.taught); put("group", task.group)
        }) }
        val payload = JSONObject().apply {
            put("id", round.id); put("tasks", rows); put("done", JSONArray(round.done.toList()))
            put("correct", round.correct); put("elapsed", round.elapsed)
            put("missed", JSONArray(round.missed.toList()))
            put("pairings", JSONObject().apply { round.pairings.forEach { (left, right) -> put(left.toString(), right) } })
        }
        db.delete("pocket_round", "slot=1", null)
        db.insertOrThrow("pocket_round", null, ContentValues().apply {
            put("slot", 1); put("payload", payload.toString())
        })
    }

    /** Switching modality is not an answer or a skip; keep the same unfinished slot. */
    fun replaceListening(roundId: String, index: Int, wordBank: Map<String, VocabularyItem>): PocketRound {
        db.beginTransaction()
        try {
            val round = checkNotNull(load())
            check(round.id == roundId && index !in round.done)
            val task = round.tasks[index]
            check(task.kind == PocketKind.LISTEN)
            val definitions = (task.options.mapNotNull { word -> wordBank[word]?.definition } +
                wordBank.values.map { it.definition }).map {
                LexicalText.splitSenses(it).firstOrNull()?.text ?: it
            }.filter { it.isNotBlank() && it != task.definition }.distinct().take(3)
            val replacement = if (definitions.isEmpty()) task.copy(kind = PocketKind.STUDY, options = emptyList())
                else task.copy(kind = PocketKind.MEANING, options = (definitions + task.definition).shuffled())
            val next = round.copy(tasks = round.tasks.mapIndexed { i, old -> if (i == index) replacement else old })
            persist(next)
            db.setTransactionSuccessful()
            return next
        } finally { db.endTransaction() }
    }

    fun answer(roundId: String, index: Int, correct: Boolean, skipped: Boolean, elapsed: Long,
               ability: AbilityEstimate, retention: Double, pairedTo: Int? = null,
               typingErrors: Int = 0): PocketRound {
        db.beginTransaction()
        try {
            val round = checkNotNull(load())
            check(round.id == roundId && index in round.tasks.indices && index !in round.done) { "练习已更新，请重新打开" }
            val task = round.tasks[index]
            if (pairedTo != null) {
                check(task.kind == PocketKind.MATCH && pairedTo in round.tasks.indices &&
                    round.tasks[pairedTo].kind == PocketKind.MATCH && round.tasks[pairedTo].group == task.group)
                val used = round.pairings.values + round.done.filter {
                    round.tasks[it].kind == PocketKind.MATCH && it !in round.pairings
                }
                check(pairedTo !in used) { "这个词义已经连过了" }
                check(correct == (pairedTo == index))
            }
            val repo = LearningRepository(helper)
            val state = repo.states(listOf(task.word)).first { it.word == task.word }
            val senses = SenseRepository(helper)
            val ids = senses.ensurePrimarySense(task.word)
            val memoryRecord = senses.memoryFor(ids.second)
            val memory = memoryRecord?.let {
                MemoryState(it.difficulty, it.stability, it.lastReview, it.reps, it.lapses)
            } ?: MemoryState.unseen
            val now = System.currentTimeMillis()
            val revealed = skipped || task.taught || task.kind == PocketKind.STUDY
            // Choice prompts are useful memory practice, not calibrated free-recall evidence.
            val earlierMiss = round.missed.any { round.tasks[it].word == task.word }
            val grade = if (correct && !revealed && !earlierMiss && typingErrors == 0) ReviewGrade.HARD else ReviewGrade.AGAIN
            // Several activities in one sitting are one memory exposure, not several spaced reviews.
            val lastForWord = round.tasks.indices.none {
                it != index && it !in round.done && round.tasks[it].word == task.word
            }
            val nextMemory = if (lastForWord) FsrsScheduler.review(memory, grade, now) else memory
            val interval = if (lastForWord) FsrsScheduler.nextIntervalDays(nextMemory, retention)?.coerceAtMost(1.0)
                else memoryRecord?.intervalDays?.takeIf { it > 0 } ?: (10.0 / 1440)
            val belief = BeliefMapper.from(state.report.name, state.attempts, state.streak, state.updated, now)
            val verdict = ReviewVerdict(grade, belief, nextMemory, ability, interval,
                if (!lastForWord && memoryRecord != null && memoryRecord.dueAt > 0) memoryRecord.dueAt
                else interval?.let { now + (it * MemoryState.MILLIS_PER_DAY).toLong() })
            repo.saveReview(state, verdict, ids.first, ids.second, correct, revealed,
                elapsed, now, ability.theta, mode = "POCKET_${task.kind.name}",
                countReview = lastForWord)
            if (task.kind == PocketKind.TYPING) {
                db.insertOrThrow("typing_attempts", null, ContentValues().apply {
                    put("round_id", roundId); put("task_index", index); put("word", task.word)
                    put("errors", typingErrors.coerceAtLeast(0)); put("elapsed_ms", elapsed.coerceAtLeast(0))
                    put("chars", if (correct && !skipped) task.word.length else 0); put("timestamp", now)
                })
            }
            val next = round.copy(done = round.done + index,
                correct = round.correct + if (correct && !skipped && task.kind != PocketKind.STUDY) 1 else 0,
                elapsed = round.elapsed + elapsed.coerceIn(0, 300_000),
                missed = if (task.kind != PocketKind.STUDY && (!correct || skipped)) round.missed + index else round.missed,
                pairings = if (pairedTo == null) round.pairings else round.pairings + (index to pairedTo))
            persist(next)
            db.setTransactionSuccessful()
            return next
        } finally { db.endTransaction() }
    }
}
