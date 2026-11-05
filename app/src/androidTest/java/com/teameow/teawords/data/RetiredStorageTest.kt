package com.teameow.teawords.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RetiredStorageTest {
    @Test fun reopeningRemovesOnlyRetiredStorageAndPreservesWordLearningAndNotes() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val name="retired-storage-${System.nanoTime()}.db"
        val unrelated=File(context.filesDir,"preserved-${System.nanoTime()}.txt")
        val retiredTables=listOf("course_progress","course_answers","course_weak_words","course_baseline",
            "course_v2_state","course_v2_answers")
        var original=emptyList<Knowledge>()
        try {
            DatabaseHelper(context,name).use { helper ->
                helper.ensureSchema()
                helper.addHistory("keep","保留查询")
                helper.addVocabulary("keep",null,"保留收藏")
                val learning=LearningRepository(helper)
                learning.importWords(listOf(ImportedWord("keep","保留单词")),"test","test")
                original=learning.states()
                helper.writableDatabase.execSQL("CREATE TABLE IF NOT EXISTS study_word_notes(word TEXT PRIMARY KEY,note TEXT NOT NULL)")
                helper.writableDatabase.execSQL("INSERT INTO study_word_notes VALUES('keep','保留笔记')")
                retiredTables.forEach { helper.writableDatabase.execSQL("CREATE TABLE $it (payload TEXT)") }
            }
            listOf("course_audio","course_recordings").forEach {
                File(context.filesDir,it).apply{mkdirs()}.resolve("retired.bin").writeText("retired")
            }
            unrelated.writeText("keep")
            DatabaseHelper(context,name).use { helper ->
                val db=helper.writableDatabase
                retiredTables.forEach { table ->
                    db.rawQuery("SELECT name FROM sqlite_master WHERE name=?",arrayOf(table)).use { assertFalse(it.moveToFirst()) }
                }
                assertEquals(original,LearningRepository(helper).states())
                assertTrue(helper.isInVocabulary("keep"))
                db.rawQuery("SELECT note FROM study_word_notes WHERE word='keep'",null).use {
                    assertTrue(it.moveToFirst());assertEquals("保留笔记",it.getString(0))
                }
                db.rawQuery("SELECT translation FROM history WHERE word='keep'",null).use {
                    assertTrue(it.moveToFirst());assertEquals("保留查询",it.getString(0))
                }
            }
            assertFalse(File(context.filesDir,"course_audio").exists())
            assertFalse(File(context.filesDir,"course_recordings").exists())
            assertEquals("keep",unrelated.readText())
        } finally {context.deleteDatabase(name);unrelated.delete()}
    }
}
