package com.teameow.teawords.data

import androidx.test.platform.app.InstrumentationRegistry
import com.teameow.teawords.algorithm.*
import org.junit.Assert.*
import org.junit.Test

class WordLearningSessionTest {
    private fun withDatabase(test: (DatabaseHelper) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "word-session-${System.nanoTime()}.db"
        val helper = DatabaseHelper(context, name)
        try {
            helper.ensureSchema()
            LearningRepository(helper).importWords(
                listOf(ImportedWord("apple", "苹果"), ImportedWord("book", "书")), "test", "test")
            test(helper)
        } finally { helper.close(); context.deleteDatabase(name) }
    }

    private fun review(helper: DatabaseHelper, word: String, correct: Boolean, revealed: Boolean): Knowledge {
        val repo = LearningRepository(helper)
        val state = repo.states(listOf(word)).first { it.word == word }
        val ids = SenseRepository(helper).ensurePrimarySense(word)
        val verdict = ReviewCoordinator().grade(KnowledgeBelief.unseen(0),
            MemoryState.unseen, AbilityEstimator.initial, 0.0, correct, revealed, 1000, 10000)
        return repo.saveReview(state, verdict, ids.first, ids.second, correct, revealed, 1000, 10000, 0.0)
    }

    @Test fun snapshotAndProgressSurviveReopeningWithAccurateEvidence() = withDatabase { helper ->
        val repo = WordLearningSessionRepository(helper)
        val session = repo.start(listOf(WordLearningTask("apple", "苹果"), WordLearningTask("book", "书")))
        repo.answer(session.id, 0, true, true, 1000) { review(helper, it, true, true) }
        helper.close()
        val reopened = WordLearningSessionRepository(helper)
        val paused = reopened.load()!!
        assertEquals(session.tasks, paused.tasks)
        assertEquals(1, paused.position)
        assertEquals(0, paused.correct)
        assertEquals(1, paused.practiced)
        assertFalse(paused.complete)
        val finished = reopened.answer(session.id, 1, false, false, 2000) { review(helper, it, false, false) }.first
        assertTrue(finished.complete)
        assertEquals(listOf("book"), finished.missed)
        assertEquals(3000L, finished.elapsed)
        assertEquals(2, LearningRepository(helper).totals().tests)
    }

    @Test fun cursorFailureRollsBackReviewAndRetryWritesOnlyOnce() = withDatabase { helper ->
        val repo = WordLearningSessionRepository(helper)
        val session = repo.start(listOf(WordLearningTask("apple", "苹果")))
        helper.writableDatabase.execSQL("CREATE TRIGGER fail_cursor BEFORE INSERT ON word_learning_session BEGIN SELECT RAISE(ABORT, 'simulated failure'); END")
        try {
            repo.answer(session.id, 0, true, false, 1000) { review(helper, it, true, false) }
            fail("Cursor write must fail")
        } catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(session, repo.load())
        assertEquals(0, LearningRepository(helper).totals().tests)
        assertEquals(0, LearningRepository(helper).states().first { it.word == "apple" }.attempts)
        helper.writableDatabase.execSQL("DROP TRIGGER fail_cursor")
        repo.answer(session.id, 0, true, false, 1000) { review(helper, it, true, false) }
        try {
            repo.answer(session.id, 0, true, false, 1000) { review(helper, it, true, false) }
            fail("Duplicate answer must be rejected")
        } catch (_: IllegalStateException) { }
        assertEquals(1, LearningRepository(helper).totals().tests)
        assertEquals(1, repo.load()!!.correct)
    }

    @Test fun unfinishedSessionCannotBeOverwritten() = withDatabase { helper ->
        val repo = WordLearningSessionRepository(helper)
        val active = repo.start(listOf(WordLearningTask("apple", "苹果")))
        try {
            repo.start(listOf(WordLearningTask("book", "书")))
            fail("Unfinished progress must be retained")
        } catch (_: IllegalStateException) { }
        assertEquals(active, repo.load())
    }

    @Test fun recognitionPreservesExistingVerificationWithoutAddingARecallPass() = withDatabase { helper ->
        repeat(3) { review(helper, "apple", true, false) }
        val learning = LearningRepository(helper)
        val senses = SenseRepository(helper)
        val ids = senses.ensurePrimarySense("apple")
        val sessions = WordLearningSessionRepository(helper)
        val session = sessions.start(listOf(WordLearningTask("apple", "苹果")))
        sessions.answer(session.id, 0, true, true, 1000) { word ->
            val known = learning.states().first { it.word == word }
            val verdict = ReviewCoordinator().gradeRecognition(
                BeliefMapper.from(known.report.name, known.attempts, known.streak, known.updated, 20000),
                MemoryState.unseen, AbilityEstimator.initial, true, 20000)
            learning.saveReview(known, verdict, ids.first, ids.second, true, true, 1000, 20000, 0.0,
                mode = "RECOGNITION")
        }
        assertEquals(3, learning.states().first { it.word == "apple" }.streak)
        assertEquals(3, senses.knowledgeFor(listOf(ids.second)).getValue(ids.second).correctStreak)
        assertEquals(0, sessions.load()!!.correct)
        assertEquals(1, sessions.load()!!.practiced)
        assertEquals(1, learning.newWordsSince(0))
    }
}
