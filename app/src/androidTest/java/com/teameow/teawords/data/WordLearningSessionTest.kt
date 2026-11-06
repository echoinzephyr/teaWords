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

    @Test fun threeDifferentStepsPersistAndCountAsOneMemoryExposure() = withDatabase { helper ->
        val sessions = WordLearningSessionRepository(helper)
        val initial = sessions.start(listOf(WordLearningTask("apple", "苹果")), threeStep = true)
        sessions.introduce(initial.id, 0, false)
        sessions.exercise(initial.id, 0, 0, 0, true, false, 1500)
        assertEquals(1, WordLearningSessionRepository(helper).load()!!.exercises.getValue("apple").completed)
        assertEquals(0, sessions.load()!!.position)
        try { sessions.exercise(initial.id, 0, 0, 0, true, false, 1500); fail("Repeated submission must not award credit") }
        catch (_: IllegalStateException) { }
        sessions.exercise(initial.id, 0, 1, 1, false, false, 1500)
        assertEquals(1, sessions.load()!!.exercises.getValue("apple").completed)
        sessions.exercise(initial.id, 0, 1, 2, true, false, 1500)
        assertEquals(1, sessions.load()!!.phase)
        sessions.retryLater(initial.id, 0)
        sessions.exercise(initial.id, 0, 2, 3, true, true, 1500)
        assertEquals(2, sessions.load()!!.exercises.getValue("apple").completed)
        assertEquals(0, LearningRepository(helper).totals().tests)
        sessions.exercise(initial.id, 0, 2, 4, true, false, 1500)
        val progress = sessions.load()!!.exercises.getValue("apple")
        assertEquals(3, progress.completed)
        assertTrue(progress.assisted)
        val result = sessions.answer(initial.id, 0, true, progress.assisted, progress.elapsed) {
            review(helper, it, true, progress.assisted)
        }.first
        assertTrue(result.complete)
        assertEquals(1, LearningRepository(helper).totals().tests)
        assertEquals(0, LearningRepository(helper).states().first { it.word == "apple" }.streak)
        assertEquals(1, result.practiced)
    }

    @Test fun unfinishedThreeStepWordCannotAdvanceOrLoseProgressOnWriteFailure() = withDatabase { helper ->
        val sessions = WordLearningSessionRepository(helper)
        val started = sessions.start(listOf(WordLearningTask("apple", "苹果")), threeStep = true)
        val initial = sessions.introduce(started.id, 0, true)
        try { sessions.answer(initial.id, 0, true, false, 1000) { review(helper, it, true, false) }; fail("Three steps are required") }
        catch (_: IllegalStateException) { }
        helper.writableDatabase.execSQL("CREATE TRIGGER fail_steps BEFORE INSERT ON word_learning_session BEGIN SELECT RAISE(ABORT, 'simulated failure'); END")
        try { sessions.exercise(initial.id, 0, 0, 0, true, false, 1000); fail("Save should fail") }
        catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(initial, sessions.load())
        assertEquals(0, LearningRepository(helper).totals().tests)
        assertTrue(helper.isInVocabulary("book"))
        helper.writableDatabase.execSQL("DROP TRIGGER fail_steps")
    }

    @Test fun selfReportIsDurableTeachingAndCannotSkipToATest() = withDatabase { helper ->
        val sessions = WordLearningSessionRepository(helper)
        val initial = sessions.start(listOf(WordLearningTask("apple", "苹果")), threeStep = true)
        try { sessions.exercise(initial.id, 0, 0, 0, true, false, 1000); fail("A new word must be introduced first") }
        catch (_: IllegalStateException) { }
        sessions.introduce(initial.id, 0, false)
        sessions.introduce(initial.id, 0, true) // Duplicate taps must not replace the original self-report.
        helper.close()
        val progress = WordLearningSessionRepository(helper).load()!!.exercises.getValue("apple")
        assertEquals(false, progress.introductionKnown)
        assertEquals(0, progress.completed)
        assertTrue(progress.assisted)
        assertEquals(0, LearningRepository(helper).totals().tests)
    }

    @Test fun wordsAreInterleavedAcrossThreePassesAndResumeAtTheSameTurn() = withDatabase { helper ->
        val sessions = WordLearningSessionRepository(helper)
        val session = sessions.start(listOf(WordLearningTask("apple", "苹果"), WordLearningTask("book", "书")), threeStep = true)
        val order = mutableListOf<String>()
        repeat(3) { phase ->
            repeat(2) { index ->
                val before = WordLearningSessionRepository(helper).load()!!
                assertEquals(phase, before.phase)
                assertEquals(phase * 2 + index, before.completedStages)
                assertEquals(index, before.position)
                assertEquals(if (phase < 2) 0 else index, before.completedWords)
                val task = before.tasks[index]
                order += task.word
                if (phase == 0) sessions.introduce(session.id, index, true)
                val progress = sessions.load()!!.exercises[task.word] ?: WordExerciseProgress()
                sessions.exercise(session.id, index, phase, progress.attempts, true, false, 1000)
                if (phase == 1) sessions.retryLater(session.id, index)
                if (phase == 2) sessions.answer(session.id, index, true, true, 3000) { review(helper, it, true, true) }
            }
        }
        assertEquals(listOf("apple", "book", "apple", "book", "apple", "book"), order)
        assertTrue(sessions.load()!!.complete)
        assertEquals(2, sessions.load()!!.completedWords)
        assertEquals(6, sessions.load()!!.completedStages)
        assertEquals(2, LearningRepository(helper).totals().tests)
    }

    @Test fun missedWordsReturnAfterOtherWordsBeforeAdvancingThePass() = withDatabase { helper ->
        val sessions = WordLearningSessionRepository(helper)
        val round = sessions.start(listOf(WordLearningTask("apple", "苹果"), WordLearningTask("book", "书")), threeStep = true)
        repeat(2) { index ->
            sessions.introduce(round.id, index, false)
            sessions.exercise(round.id, index, 0, 0, true, false, 1000)
        }
        sessions.exercise(round.id, 0, 1, 1, false, false, 1000)
        sessions.retryLater(round.id, 0)
        assertEquals(1, sessions.load()!!.position)
        sessions.exercise(round.id, 1, 1, 1, true, false, 1000)
        assertEquals(1, sessions.load()!!.position)
        sessions.retryLater(round.id, 1)
        assertEquals(0, sessions.load()!!.position)
        assertEquals(1, sessions.load()!!.phase)
        sessions.exercise(round.id, 0, 1, 2, true, false, 1000)
        sessions.retryLater(round.id, 0)
        assertEquals(2, sessions.load()!!.phase)
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
