package com.teameow.teawords.data

import org.junit.Assert.*
import org.junit.Test

class WordExercisePlannerTest {
    private val task = WordLearningTask("take", "拿；取")
    private val bank = listOf(task, WordLearningTask("book", "书"), WordLearningTask("run", "跑"), WordLearningTask("build", "建造"))

    @Test fun firstMeetTeachesBeforeUsageAndSpellingTests() {
        val meanings = WordExercisePlanner.build(task, 0, bank)
        assertEquals(WordExerciseKind.INTRODUCTION, meanings.kind)
        assertTrue(meanings.choices.isEmpty())
        assertEquals(meanings, WordExercisePlanner.build(task, 0, bank))
        val phrase = WordExercisePlanner.build(task, 1, bank, listOf("take care" to "当心；保重"))
        assertEquals(WordExerciseKind.USAGE, phrase.kind)
        assertEquals("take care", phrase.prompt)
        assertEquals("当心；保重", phrase.answer)
        assertEquals(WordExerciseKind.SPELLING, WordExercisePlanner.build(task, 2, bank).kind)
    }

    @Test fun absentPhrasesUseActualReverseVocabularyWithoutInventingExamples() {
        val prompt = WordExercisePlanner.build(task, 1, bank)
        assertEquals(WordExerciseKind.REVERSE, prompt.kind)
        assertEquals(task.definition, prompt.prompt)
        assertEquals(1, prompt.choices.count { it == task.word })
        assertTrue(prompt.choices.all { choice -> bank.any { it.word == choice } })
    }

    @Test fun aSingleImportedWordStillHasRealDistractors() {
        val prompt = WordExercisePlanner.build(task, 1, listOf(task))
        assertEquals(4, prompt.choices.size)
        assertEquals(1, prompt.choices.count { it == task.word })
    }
}
