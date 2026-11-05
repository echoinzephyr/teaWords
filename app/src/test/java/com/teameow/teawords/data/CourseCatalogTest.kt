package com.teameow.teawords.data

import org.junit.Assert.*
import org.junit.Test

class CourseCatalogTest {
    @Test fun twelveLessonsAreCompleteOrderedAndIndependentOfExamWordbooks() {
        CourseCatalog.validate(CourseCatalog.pack)
        assertEquals((1..12).toList(), CourseCatalog.all.map { it.number })
        assertEquals(listOf(4, 4, 4), CourseDirectory.chapters().map { it.lessons.size })
        assertEquals(CourseCatalog.all.map { it.id }, CourseDirectory.chapters().flatMap { it.lessons }.map { it.id })
        assertEquals("voa1-12", CourseDirectory.search("12").single().id)
        assertEquals("voa1-09", CourseDirectory.search("forecast").single().id)
    }
    @Test fun dictationEquivalentsIgnoreCaseWhitespaceAndNoncriticalPunctuation() {
        val q = CourseCatalog.all[7].questions.first { it.type == "dictation" }
        assertTrue(q.accepts("  I'M a little busy!!!  "))
        assertTrue(q.accepts("I am a little busy."))
        assertTrue(q.accepts("I’m a LITTLE busy"))
        assertFalse(q.accepts("I busy"))
        assertFalse(q.accepts("I'm not busy"))
        assertFalse(q.accepts(""))
    }
    @Test fun everyQuestionHasAnAnswerAndARealTextLocator() {
        CourseCatalog.all.forEach { lesson ->
            lesson.questions.forEach { q ->
                assertTrue(q.accepts(q.answers.first()))
                val segment = lesson.segments.single { it.id == q.segmentId }
                if (q.type == "dictation") assertTrue(q.accepts(segment.english))
                assertTrue(q.explanation.isNotBlank())
            }
        }
    }
    @Test fun invalidAudioTimelineAndSourceAreRejected() {
        val pack = CourseCatalog.pack; val first = pack.lessons.first()
        val bad = first.copy(segments = first.segments.mapIndexed { i,s -> if (i==0) s.copy(endMs=first.audio.durationMs+1) else s })
        try { CourseCatalog.validate(pack.copy(lessons = listOf(bad) + pack.lessons.drop(1))); fail("invalid timestamp") }
        catch (_: IllegalArgumentException) { }
        try { CourseCatalog.validate(pack.copy(lessons = pack.lessons.drop(1))); fail("missing lesson") }
        catch (_: IllegalArgumentException) { }
    }
}
