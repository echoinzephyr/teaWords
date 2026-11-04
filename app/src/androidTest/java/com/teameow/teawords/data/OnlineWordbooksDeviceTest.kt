package com.teameow.teawords.data

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.teameow.teawords.data.search.WordSearchRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class OnlineWordbooksDeviceTest {
    private val target get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun isolated(): Context = object : ContextWrapper(target) {
        private val name = "download-test-${System.nanoTime()}.db"
        override fun getDatabasePath(name: String): File = File(cacheDir, this.name)
    }
    @Test fun freshInstallOnlyShipsStableIdentityAndAnEmptyDictionary() {
        val context = isolated()
        val installer = BundledDictionaryInstaller(context)
        try {
            installer.install()
            SQLiteDatabase.openDatabase(installer.targetFile().path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                assertEquals(0, db.rawQuery("SELECT count(*) FROM lex_words", null).use { it.moveToFirst(); it.getInt(0) })
                assertEquals(16L, db.rawQuery("SELECT id FROM word_identity WHERE normalized='abandon'", null).use { it.moveToFirst(); it.getLong(0) })
            }
            assertEquals(8, OnlineWordbooks(context).catalog().size)
            assertFalse(OnlineWordbooks(context).isDownloaded("cet4"))
        } finally { installer.targetFile().delete() }
    }
    @Test fun actualBookDownloadIsVerifiedResumableAndReusableOffline() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("wordbookServer") == "true")
        val context = isolated()
        val installer = BundledDictionaryInstaller(context)
        val books = OnlineWordbooks(context, "http://127.0.0.1:8765")
        val summary = books.catalog().first { it.id == "cet4" }
        val partial = File(context.cacheDir, "book-cet4-${summary.sha256}.part")
        try {
            // Leave a genuine interrupted prefix, then require the server to resume from it.
            val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open("api/wordbooks/cet4.json").use { it.readBytes() }
            partial.writeBytes(bytes.copyOf(1024))
            var progress = 0L
            books.ensureDownloaded(setOf("cet4")) { _, done, _ -> progress = done }
            assertTrue(progress > 1024)
            assertTrue(books.isDownloaded("cet4"))
            assertFalse(partial.exists())
            val repository = WordSearchRepository(installer)
            try {
                assertTrue(repository.open())
                assertEquals(summary.count, repository.learningWords(setOf("cet4")).size)
                assertEquals(16L, repository.learningWords(setOf("cet4")).first { it.word == "abandon" }.wordId)
                assertTrue(repository.byForm("abandoned", com.teameow.teawords.data.search.SearchFilter()).any { it.word == "abandon" })
                val count = repository.wordCount()
                // An invalid offline endpoint must never be touched when the selected book is already installed.
                OnlineWordbooks(context, "https://offline.invalid").ensureDownloaded(setOf("cet4"))
                assertEquals(count, repository.wordCount())
            } finally { repository.close() }
        } finally { partial.delete(); installer.targetFile().delete() }
    }
    @Test fun legacyDeviceSettingFallsBackOnceWithoutLosingOtherSettings() {
        val prefs = target.getSharedPreferences("lookup_privacy", 0)
        val before = prefs.all
        try {
            prefs.edit().putString("translation_engine", "DEVICE").putBoolean("history", false).commit()
            val preferences = TranslationPreferences(target)
            assertEquals(TranslationEngine.MYMEMORY, preferences.load().engine)
            assertTrue(preferences.consumeMigrationNotice())
            assertFalse(preferences.consumeMigrationNotice())
            assertFalse(prefs.getBoolean("history", true))
        } finally {
            val edit = prefs.edit().clear()
            before.forEach { (key, value) -> when (value) {
                is String -> edit.putString(key, value); is Boolean -> edit.putBoolean(key, value)
                is Int -> edit.putInt(key, value); is Long -> edit.putLong(key, value)
                is Float -> edit.putFloat(key, value)
            } }
            edit.commit()
        }
    }

    @Test fun compactIdentityRebindsLegacyProgressBeforeAnyBookIsDownloaded() {
        val context = isolated()
        val installer = BundledDictionaryInstaller(context)
        val name = "compact-progress-${System.nanoTime()}.db"
        val helper = DatabaseHelper(target, name)
        try {
            installer.install()
            val local = LocalDictionary(helper, com.teameow.teawords.data.search.ShippedDictionarySource.localOnly)
            local.upsert("abandon", "v. 放弃")
            local.mark(checkNotNull(local.exact("abandon")), SelfReport.UNKNOWN)
            val sense = SenseRepository(helper).ensurePrimarySense("abandon")
            WordIdMigration(helper).apply(installer.targetFile(), "compact-identity-test")
            assertEquals(16L, helper.readableDatabase.rawQuery("SELECT word_id FROM learning_knowledge WHERE word='abandon'", null).use { it.moveToFirst(); it.getLong(0) })
            assertEquals(sense.second, helper.readableDatabase.rawQuery("SELECT id FROM sense_units WHERE word_id=16", null).use { it.moveToFirst(); it.getLong(0) })
        } finally { helper.close(); target.deleteDatabase(name); installer.targetFile().delete() }
    }
}
