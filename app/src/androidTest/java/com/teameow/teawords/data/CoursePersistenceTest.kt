package com.teameow.teawords.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test

class CoursePersistenceTest {
    @Test fun courseCheckpointSurvivesReopenWithoutChangingExistingLearning() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "course-${System.nanoTime()}.db"
        val lesson = CourseCatalog.postgraduate.first()
        try {
            DatabaseHelper(context, name).use { helper ->
                helper.ensureSchema()
                val learning = LearningRepository(helper)
                learning.importWords(listOf(ImportedWord(lesson.focus, lesson.meaning)), "test", "test")
                val before = learning.states().first { it.word == lesson.focus }
                val repo = CourseRepository(helper)
                assertEquals(1, repo.answer(lesson, 0, true, 500).step)
                try { repo.answer(lesson, 0, true, 500); fail("duplicate answer accepted") }
                catch (_: IllegalStateException) { }
                assertEquals(1, repo.answer(lesson, 1, false, 700).mistakes.size)
                assertEquals(before, learning.states().first { it.word == lesson.focus })
            }
            DatabaseHelper(context, name).use { helper ->
                val repo = CourseRepository(helper)
                assertEquals(2, repo.progress(lesson.id).step)
                assertEquals(listOf(lesson.focus), repo.weakWords())
                helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM course_answers WHERE lesson_id=?",
                    arrayOf(lesson.id)).use { it.moveToFirst(); assertEquals(2, it.getInt(0)) }
                assertEquals(0, LearningRepository(helper).states().first { it.word == lesson.focus }.attempts)
            }
        } finally { context.deleteDatabase(name) }
    }
}
