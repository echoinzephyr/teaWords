package com.teameow.teawords.data

import org.junit.Assert.*
import org.junit.Test

class StudyDictionaryTest {
    @Test fun onlineLookupDoesNotWriteNewDiskCache() = kotlinx.coroutines.runBlocking {
        org.junit.Assume.assumeTrue(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("onlineDictionary") == "true")
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val name = "online-dictionary-${System.nanoTime()}.db"
        val helper = DatabaseHelper(context, name)
        try {
            val entry = StudyDictionary(helper).lookup("access")
            assertTrue(entry.definition.isNotBlank())
            assertTrue(entry.uk.isNotBlank())
            assertTrue(entry.us.isNotBlank())
            assertTrue(entry.phrases.isNotEmpty())
            helper.close()
            val reopened = DatabaseHelper(context, name)
            try { assertNull(StudyDictionary(reopened).cached("ACCESS")) }
            finally { reopened.close() }
        } finally { helper.close(); context.deleteDatabase(name) }
    }

    @Test fun preservesRegionalPhoneticsAndBilingualDetails() {
        val entry = StudyDictionary.parse("""{
          "ec":{"word":[{"ukphone":"ˈækses","usphone":"ˈæksɛs",
            "trs":[{"tr":[{"l":{"i":["n. 入口；访问","v. 存取"]}}]}],
            "wfs":[{"wf":{"name":"过去式","value":"accessed"}}]}]},
          "phrs":{"phrs":[{"phr":{"headword":{"l":{"i":"access control"}},
            "trs":[{"tr":{"l":{"i":"访问控制"}}}]}}]}
        }""")
        assertEquals("ˈækses", entry.uk)
        assertEquals("ˈæksɛs", entry.us)
        assertEquals("n. 入口；访问\nv. 存取", entry.definition)
        assertEquals(listOf("access control" to "访问控制"), entry.phrases)
        assertEquals(listOf("过去式" to "accessed"), entry.forms)
    }

    @Test fun missingSectionsStayEmptyAndUntranslatedPhrasesAreOmitted() {
        val entry = StudyDictionary.parse("""{"phrs":{"phrs":[{"phr":{"headword":{"l":{"i":"unknown phrase"}}}}]}}""")
        assertTrue(entry.definition.isEmpty())
        assertTrue(entry.uk.isEmpty())
        assertTrue(entry.us.isEmpty())
        assertTrue(entry.phrases.isEmpty())
    }
}
