package com.teameow.teawords.data

import org.junit.Assert.*
import org.junit.Test

class CourseCatalogTest {
    @Test fun directoryKeepsEveryLessonAndFindsTopicsAcrossTracks() {
        CourseDirectory.tracks.forEach { track ->
            val inUnits = CourseDirectory.chapters(track).flatMap { it.lessons }.map { it.id }
            assertEquals(CourseDirectory.lessons(track).map { it.id }, inUnits)
        }
        assertEquals("ky-01", CourseDirectory.search("ky", "研究与证据").single().id)
        assertEquals("ielts-03", CourseDirectory.search("ielts", "tourism").single().id)
        assertEquals("toefl-01", CourseDirectory.search("toefl", "1").single().id)
    }

    @Test fun allTracksHaveStableUniqueIdsAndUsablePrompts() {
        assertEquals(40, CourseCatalog.postgraduateForty.size)
        assertEquals(10, CourseCatalog.ielts.size)
        assertEquals(10, CourseCatalog.toefl.size)
        assertEquals(60, CourseCatalog.all.map { it.id }.distinct().size)
        assertTrue(CourseCatalog.pilotWords.size >= 300)
        CourseCatalog.all.forEach { lesson ->
            assertTrue(lesson.focus in lesson.words)
            assertTrue(lesson.example.contains(lesson.focus, ignoreCase = true))
            assertTrue(lesson.orderedSentence.isNotBlank())
            assertTrue(lesson.passage.length >= 100)
            assertTrue(lesson.readingAnswer !in lesson.readingDistractors)
            assertTrue(lesson.modelWriting.isNotBlank())
        }
    }

    @Test fun typingRoundPrioritizesDueWordsAndRespectsNewBudget() {
        val words = listOf("alpha", "bravo", "charlie").associateWith {
            VocabularyItem(word = it, definition = "词义 $it", phonetic = null, timestamp = 0)
        }
        val states = listOf(Knowledge("alpha", attempts = 1, due = 1),
            Knowledge("bravo"), Knowledge("charlie"))
        val round = PocketPlanner.typingRound(states, words, 1, 100, wordLimit = 3,
            preferredWords = listOf("charlie"))!!
        assertEquals(listOf("alpha", "charlie"), round.tasks.map { it.word })
        assertTrue(round.tasks.all { it.kind == PocketKind.TYPING && it.answer == it.word })
        assertEquals(listOf("alpha"), PocketPlanner.typingRound(states, words, 0, 100)!!.tasks.map { it.word })
    }
}
