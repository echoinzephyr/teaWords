package com.teameow.teawords.algorithm

import org.junit.Assert.*
import org.junit.Test

class MemoryAlgorithmRegressionTest {
    private val now = 1_800_000_000_000L

    @Test fun tenDayReviewMatchesPublishedFsrs45ReferenceValues() {
        // Independent fixture from FSRS-4.5 formulas: D=5, S=10, R=0.9 after ten days.
        val state = MemoryState(5.0, 10.0, now - 864_000_000L, 3, 0)
        val good = FsrsScheduler.review(state, ReviewGrade.GOOD, now)
        assertEquals(5.0050158, good.difficulty, 1e-8)
        assertEquals(35.08389427030439, good.stability, 1e-8)
        val failed = FsrsScheduler.review(state, ReviewGrade.AGAIN, now)
        assertEquals(2.5603830063191944, failed.stability, 1e-8)
    }

    @Test fun immediateLapseDoesNotCollapseToMinimumOrIncreaseStability() {
        val state = MemoryState(5.0, 10.0, now, 3, 0)
        val failed = FsrsScheduler.review(state, ReviewGrade.AGAIN, now)
        assertTrue(failed.stability > .1)
        assertTrue(failed.stability <= state.stability)
    }

    @Test fun abilityCountsThePriorOnceRatherThanOncePerAnswer() {
        var estimate = AbilityEstimator.initial
        repeat(12) { estimate = AbilityEstimator.update(estimate, ResponseObservation(0.0, it % 2 == 0)) }
        assertTrue("One Bernoulli item contributes at most 0.25 Fisher information", estimate.totalInformation <= 12 * .25 + 1e-9)
        assertEquals(1.0 / kotlin.math.sqrt(estimate.totalInformation + 1.0 / 2.25), estimate.standardError, 1e-9)
    }

    @Test fun alreadyMasteredButDueItemIsStillScheduledForReview() {
        val unit = StudyUnit(1, "word", 1, "词义", PracticeMode.RECALL, 0.0, 1.0,
            KnowledgeBelief(.95, .9, true, 3, 6, now - 864_000_000, MasterySource.TEST_EVIDENCE),
            MemoryState(5.0, 5.0, now - 864_000_000, 6, 0), .95, false, now - 1)
        val result = LearningDecisionEngine.decide(DecisionInput(listOf(unit), LearningStrategy.BALANCED, now))
        assertEquals(StudyAction.REVIEW, result.single().action)
    }

    @Test fun failedRecallReturnsInTenMinutesAndInitialSuccessWithinADay() {
        val coordinator = ReviewCoordinator()
        fun grade(correct: Boolean) = coordinator.grade(KnowledgeBelief.unseen(now), MemoryState.unseen,
            AbilityEstimator.initial, 0.0, correct, false, 3000, now)
        assertEquals(10.0 / 1440.0, grade(false).intervalDays!!, 1e-9)
        assertTrue(grade(true).intervalDays!! <= 1.0)
        assertEquals(ReviewGrade.GOOD, grade(true).grade)
    }
}
