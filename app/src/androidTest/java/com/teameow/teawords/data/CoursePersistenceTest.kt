package com.teameow.teawords.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test

class CoursePersistenceTest {
    @Test fun retiredQuestionAnswersCannotCompleteTheNewLearningPath() {
        val context=ApplicationProvider.getApplicationContext<Context>();val name="course-retired-${System.nanoTime()}.db"
        try { DatabaseHelper(context,name).use { helper ->
            val repo=CourseRepository(helper);val lesson=CourseCatalog.all.first()
            helper.writableDatabase.execSQL("INSERT INTO course_v2_answers VALUES(?, 'q01','old answer',1,1)",arrayOf(lesson.id))
            assertTrue(repo.progress(lesson.id).answers.isEmpty())
            lesson.questions.forEach{repo.submit(lesson,it.id,"wrong",0)}
            val state=repo.progress(lesson.id)
            assertTrue(state.attemptedAll);assertFalse(state.complete);assertEquals(8,state.mistakes.size)
        } } finally {context.deleteDatabase(name)}
    }
    @Test fun checkpointAnswersRetriesAndDraftSurviveReopenWithoutTouchingVocabulary() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "course-${System.nanoTime()}.db"; val lesson = CourseCatalog.all.first()
        try {
            DatabaseHelper(context, name).use { helper ->
                helper.ensureSchema()
                val learning = LearningRepository(helper)
                learning.importWords(listOf(ImportedWord("untouched", "保留")), "test", "test")
                helper.addVocabulary("untouched", null, "收藏保留")
                helper.writableDatabase.execSQL("CREATE TABLE IF NOT EXISTS study_word_notes(word TEXT PRIMARY KEY,note TEXT NOT NULL)")
                helper.writableDatabase.execSQL("INSERT INTO study_word_notes VALUES('untouched','原有笔记')")
                val before = learning.states()
                // Seed actual retired tables, then prove only course-owned data is removed.
                helper.writableDatabase.execSQL("CREATE TABLE course_progress(lesson_id TEXT)")
                helper.writableDatabase.execSQL("INSERT INTO course_progress VALUES('ky-01')")
                val repo = CourseRepository(helper)
                helper.readableDatabase.rawQuery("SELECT name FROM sqlite_master WHERE name='course_progress'", null).use { assertFalse(it.moveToFirst()) }
                val q = lesson.questions.first()
                repo.save(lesson.id, CourseRepository.Checkpoint(section=2,sentence=3,positionMs=6000,question=2,draft="my unfinished",started=true))
                assertEquals(1, repo.submit(lesson,q.id,"wrong",0).answers.size)
                assertEquals(1, repo.submit(lesson,q.id,q.answers.first(),0).answers[q.id]!!.attempt)
                assertFalse(repo.progress(lesson.id).answers[q.id]!!.correct)
                repo.submit(lesson,q.id,q.answers.first(),1)
                lesson.questions.drop(1).forEach { repo.submit(lesson,it.id,it.answers.first(),0) }
                assertTrue(repo.progress(lesson.id).complete)
                assertTrue(repo.progress(lesson.id).mistakes.isEmpty())
                repo.save(lesson.id, repo.progress(lesson.id).checkpoint.copy(draft="offline draft",speed=0.75f,guideVersion=2,guidedStage=4,drafts=mapOf("transfer-1" to "my saved answer"),roundIds=listOf("transfer-1"),reviewAt=123456789L))
                assertEquals(before, learning.states())
                assertTrue(helper.isInVocabulary("untouched"))
                helper.readableDatabase.rawQuery("SELECT note FROM study_word_notes WHERE word='untouched'",null).use {
                    assertTrue(it.moveToFirst());assertEquals("原有笔记",it.getString(0))
                }
            }
            DatabaseHelper(context,name).use { helper ->
                val p = CourseRepository(helper).progress(lesson.id)
                assertTrue(p.complete); assertEquals(lesson.questions.size,p.answers.size)
                assertEquals(2,p.answers[lesson.questions.first().id]!!.attempt)
                assertEquals("my saved answer",p.checkpoint.drafts["transfer-1"]);assertEquals(4,p.checkpoint.guidedStage)
                assertEquals("offline draft",p.checkpoint.draft); assertEquals(6000,p.checkpoint.positionMs)
                assertEquals(0,LearningRepository(helper).states().single().attempts)
            }
        } finally { context.deleteDatabase(name) }
    }
}
