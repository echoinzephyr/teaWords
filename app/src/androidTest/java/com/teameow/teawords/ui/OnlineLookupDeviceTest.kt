package com.teameow.teawords.ui

import android.app.Application
import androidx.test.platform.app.InstrumentationRegistry
import com.teameow.teawords.data.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class OnlineLookupDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun answer(text: String) = """{"ec":{"word":[{"trs":[{"tr":[{"l":{"i":["$text"]}}]}]}]}}"""
    private fun waitFor(condition: () -> Boolean) {
        val until = System.currentTimeMillis() + 10_000
        while (!condition() && System.currentTimeMillis() < until) Thread.sleep(30)
        assertTrue(condition())
    }
    @Test fun changingInputCancelsDelayedAnswerAndPreservesNewQuery() {
        val service = OnlineDictionary { url ->
            if (url.contains("firsttoken")) withContext(NonCancellable) { delay(300); answer("old answer") }
            else answer("new answer")
        }
        lateinit var model: LookupViewModel
        instrumentation.runOnMainSync { model = LookupViewModel(instrumentation.targetContext.applicationContext as Application, service); model.open("firsttoken") }
        waitFor { model.state.value.onlineBusy }
        instrumentation.runOnMainSync { model.input("secondtoken"); model.search() }
        waitFor { model.state.value.onlineEntry?.detail?.definition == "new answer" }
        Thread.sleep(400)
        assertEquals("secondtoken", model.state.value.input)
        assertEquals("new answer", model.state.value.onlineEntry?.detail?.definition)
        instrumentation.runOnMainSync { model.cancel() }
    }
    @Test fun viewingOnlineAnswerDoesNotCreateKnowledgeOrCacheRows() {
        val helper = DatabaseHelper(instrumentation.targetContext)
        fun count(table: String) = helper.readableDatabase.rawQuery("SELECT count(*) FROM $table", null).use { it.moveToFirst(); it.getLong(0) }
        val knowledgeBefore = count("learning_knowledge")
        val evidenceBefore = count("test_records")
        lateinit var model: LookupViewModel
        try {
            instrumentation.runOnMainSync { model = LookupViewModel(instrumentation.targetContext.applicationContext as Application, OnlineDictionary { answer("test definition") }); model.open("uniquesearchtoken", external = true) }
            waitFor { model.state.value.onlineEntry != null }
            assertEquals(knowledgeBefore, count("learning_knowledge"))
            assertEquals(evidenceBefore, count("test_records"))
            assertEquals("uniquesearchtoken", model.state.value.input)
            assertNull(LocalDictionary(helper).exact("uniquesearchtoken"))
        } finally { instrumentation.runOnMainSync { model.cancel() }; helper.close() }
    }
}
