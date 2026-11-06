package com.teameow.teawords.data

import kotlin.random.Random

enum class WordExerciseKind { INTRODUCTION, USAGE, REVERSE, SPELLING }

data class WordExercisePrompt(
    val kind: WordExerciseKind, val prompt: String, val answer: String,
    val choices: List<String> = emptyList(), val explanation: String,
    val choiceMatches: Map<String, String> = emptyMap()
)

/** Questions use actual dictionary definitions; missing phrases fall back to reverse recognition. */
object WordExercisePlanner {
    // Keep tiny personal imports usable without one-option "questions" or invented definitions.
    private val starterWords = listOf("book" to "书", "water" to "水", "run" to "跑", "music" to "音乐",
        "house" to "房屋", "apple" to "苹果", "train" to "火车", "happy" to "快乐的").map { WordLearningTask(it.first, it.second) }
    fun build(task: WordLearningTask, step: Int, bank: List<WordLearningTask>,
              phrases: List<Pair<String, String>> = emptyList(), phraseBank: List<Pair<String, String>> = emptyList()): WordExercisePrompt {
        require(step in 0..2)
        val available = bank.filter { it.word != task.word && it.word.isNotBlank() && it.definition.isNotBlank() }
        val others = (available + if (available.size < 3) starterWords else emptyList())
            .distinctBy { it.word.lowercase() }.filter { it.word != task.word && it.definition != task.definition }
        val random = Random(task.word.hashCode() * 31 + step)
        fun choices(answer: String, candidates: List<String>) =
            (candidates.distinct().filter { it.trim() != answer.trim() }.shuffled(random).take(3) + answer).shuffled(random)
        return when (step) {
            0 -> WordExercisePrompt(WordExerciseKind.INTRODUCTION, task.word, task.definition,
                explanation = "${task.word} · ${task.definition}")
            1 -> {
                fun isPhrase(item: Pair<String, String>) = item.first.trim().split(Regex("\\s+")).size >= 2 && item.second.isNotBlank()
                val phrase = phrases.firstOrNull { isPhrase(it) && !it.first.equals(task.word, ignoreCase = true) }
                val alternatives = (phraseBank + phrases).filter(::isPhrase)
                    .filter { !it.first.equals(phrase?.first, ignoreCase = true) && it.second.trim() != phrase?.second?.trim() }
                    .distinctBy { it.first.lowercase().trim() }.distinctBy { it.second.trim() }
                if (phrase != null && alternatives.size >= 3) WordExercisePrompt(WordExerciseKind.USAGE, phrase.first, phrase.second,
                    choices(phrase.second, alternatives.map { it.second }), "${phrase.first} · ${phrase.second}",
                    alternatives.associate { it.second to it.first } + (phrase.second to phrase.first))
                else WordExercisePrompt(WordExerciseKind.REVERSE, task.definition, task.word,
                    choices(task.word, others.map { it.word }), "${task.word} · ${task.definition}",
                    others.associate { it.word to it.definition } + (task.word to task.definition))
            }
            else -> WordExercisePrompt(WordExerciseKind.SPELLING, task.definition, task.word,
                explanation = "${task.word} · ${task.definition}")
        }
    }
}
