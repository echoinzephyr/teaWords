package com.teameow.teawords.data

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class OnlineDictionaryTest {
    private val answer = """{"ec":{"word":[{"ukphone":"UK","usphone":"US","trs":[{"tr":[{"l":{"i":["n. 测试"]}}]}],"wfs":[{"wf":{"name":"复数","value":"tests"}}]}]}}"""
    @Test fun parsesDetailsWithoutGivingThemDatabaseIdentity() {
        val detail = OnlineDictionary.parse(answer)
        assertEquals("n. 测试", detail.definition)
        assertEquals("UK", detail.uk); assertEquals("US", detail.us)
        assertEquals(listOf("复数" to "tests"), detail.forms)
    }
    @Test fun onlineHeadwordUsesCanonicalIdentityWithoutSavingTheResponse() = runBlocking {
        val service = OnlineDictionary { answer.replace("\"ukphone\"", "\"return-phrase\":\"test\",\"ukphone\"") }
        assertEquals("test", service.lookup("tests").entry!!.word)
        assertEquals("query", OnlineDictionary.headword("{}", "query"))
    }
    @Test fun cacheNormalizesQueriesAndEvictsLeastRecentlyReadAt64() = runBlocking {
        var requests = 0
        val service = OnlineDictionary { requests++; answer }
        repeat(64) { service.lookup("word$it") }
        service.lookup(" WORD0 ")
        service.lookup("word64")
        service.lookup("word0")
        assertEquals(65, requests)
        service.lookup("word1")
        assertEquals(66, requests)
    }
    @Test fun suggestsOnlyWhenDefinitionIsMissingAndDeduplicates() = runBlocking {
        val paths = mutableListOf<String>()
        val service = OnlineDictionary { path -> paths += path
            if (path.contains("/suggest")) """{"data":{"entries":[{"entry":"hello","explain":"你好"},{"entry":"HELLO"},{"entry":""}]}}""" else "{}"
        }
        val result = service.lookup("helo")
        assertNull(result.entry); assertEquals(listOf(OnlineSuggestion("hello", "你好")), result.suggestions)
        assertEquals(2, paths.size)
    }
    @Test fun failedQueriesAreNotCachedAndCanBeRetried() = runBlocking {
        var offline = true
        val service = OnlineDictionary { if (offline) throw IOException("offline") else answer }
        try { service.lookup("hello"); fail("must fail") } catch (_: IOException) { }
        offline = false
        assertNotNull(service.lookup("hello").entry)
    }
    @Test fun cancellationDoesNotLeaveAnAnswerInCache() = runBlocking {
        var requests = 0
        val service = OnlineDictionary { requests++; delay(150); answer }
        val pending = launch { service.lookup("hello") }
        delay(20); pending.cancelAndJoin()
        assertNotNull(service.lookup("hello").entry)
        assertEquals(2, requests)
    }
}
