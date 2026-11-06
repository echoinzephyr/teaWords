package com.teameow.teawords.data

import androidx.test.platform.app.InstrumentationRegistry
import com.teameow.teawords.algorithm.*
import org.junit.Assert.*
import org.junit.Test

class BeliefPersistenceTest {
    @Test fun recallPosteriorSurvivesReopeningAndRecognitionDoesNotInflateConfidence() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "belief-${System.nanoTime()}.db"
        val helper = DatabaseHelper(context, name)
        try {
            helper.ensureSchema()
            val repository = LearningRepository(helper)
            repository.importWords(listOf(ImportedWord("apple", "苹果")), "test", "test")
            val (wordId, senseId) = SenseRepository(helper).ensurePrimarySense("apple")
            val state = repository.states().single()
            val before = repository.beliefFor(state, senseId, 10000)
            val verdict = ReviewCoordinator().grade(before, MemoryState.unseen, AbilityEstimator.initial,
                0.0, true, false, 8000, 10000)
            val saved = repository.saveReview(state, verdict, wordId, senseId, true, false, 8000, 10000, 0.0)
            helper.close()
            val reopened = LearningRepository(helper)
            val restored = reopened.beliefFor(saved, senseId, 20000)
            assertEquals(verdict.knowledge.mastery, restored.mastery, 1e-9)
            assertEquals(1, restored.totalAnswers)
            val practice = ReviewCoordinator().gradeRecognition(restored, verdict.memory, verdict.ability, true, 20000)
            val recognition = reopened.saveReview(saved, practice, wordId, senseId, true, true, 1000, 20000, 0.0, mode = "RECOGNITION")
            val after = reopened.beliefFor(recognition, senseId, 30000)
            assertEquals(restored.totalAnswers, after.totalAnswers)
            assertEquals(restored.confidence, after.confidence, 1e-9)
            assertEquals(restored.mastery, after.mastery, 1e-9)
        } finally { helper.close(); context.deleteDatabase(name) }
    }
}
