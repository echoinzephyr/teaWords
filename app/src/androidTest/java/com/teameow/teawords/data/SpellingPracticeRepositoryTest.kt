package com.teameow.teawords.data

import androidx.test.platform.app.InstrumentationRegistry
import com.teameow.teawords.algorithm.*
import org.junit.Assert.*
import org.junit.Test

class SpellingPracticeRepositoryTest {
    private fun database(test: (DatabaseHelper) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "optional-spelling-${System.nanoTime()}.db"
        val helper = DatabaseHelper(context, name)
        try {
            helper.ensureSchema()
            LearningRepository(helper).importWords(listOf(ImportedWord("apple", "苹果"), ImportedWord("book", "书")), "test", "test")
            test(helper)
        } finally { helper.close(); context.deleteDatabase(name) }
    }

    @Test fun optionalSpellingUpdatesMemoryOnceWithoutAdvancingTheRoundAndRollsBackOnFailure() = database { helper ->
        val sessions = WordLearningSessionRepository(helper)
        val round = sessions.start(listOf(WordLearningTask("apple", "苹果")), threeStep = true)
        val practice = SpellingPracticeRepository(helper)
        val first = practice.record("attempt-a", "apple", true, true, 2000, 10000, AbilityEstimator.initial, .9)!!
        assertEquals(1, first.verdict.memory.reps)
        assertEquals(0, first.state.streak)
        assertEquals(round, sessions.load())
        assertNull(practice.record("attempt-a", "apple", false, true, 2000, 20000, AbilityEstimator.initial, .9))
        assertEquals(1, LearningRepository(helper).totals().tests)
        helper.writableDatabase.execSQL("CREATE TRIGGER fail_practice_receipt BEFORE INSERT ON spelling_practice_receipts BEGIN SELECT RAISE(ABORT, 'simulated failure'); END")
        try {
            practice.record("attempt-b", "apple", false, true, 3000, 30000, AbilityEstimator.initial, .9)
            fail("Practice and receipt must commit together")
        } catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(1, LearningRepository(helper).totals().tests)
        assertEquals(round, sessions.load())
        helper.writableDatabase.execSQL("DROP TRIGGER fail_practice_receipt")
        val missed = practice.record("attempt-b", "apple", false, true, 3000, 30000, AbilityEstimator.initial, .9)!!
        assertEquals(2, missed.verdict.memory.reps)
        assertEquals(1, missed.verdict.memory.lapses)
        assertTrue(missed.verdict.intervalDays!! <= 10.0 / 1440)
        assertEquals(0, LearningRepository(helper).states().first { it.word == "book" }.attempts)
    }

    @Test fun guidedSpellingPreservesIndependentEvidenceAndDoesNotInflateAbility() = database { helper ->
        val practice = SpellingPracticeRepository(helper)
        var ability = AbilityEstimator.initial
        repeat(3) { index ->
            ability = practice.record("recall-$index", "apple", true, false, 5000,
                10000L + index * 86_400_000L, ability, .9)!!.verdict.ability
        }
        val guided = practice.record("guided", "apple", true, true, 2000, 300_000_000L, ability, .9)!!
        assertEquals(3, guided.state.streak)
        assertEquals(ability, guided.verdict.ability)
        assertEquals(4, guided.verdict.memory.reps)
        val id = SenseRepository(helper).ensurePrimarySense("apple").second
        assertEquals(3, LearningRepository(helper).beliefFor(guided.state, id, 400_000_000L).totalAnswers)
    }
}
