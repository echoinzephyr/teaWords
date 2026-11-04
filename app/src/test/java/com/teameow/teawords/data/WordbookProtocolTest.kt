package com.teameow.teawords.data

import org.junit.Assert.*
import org.junit.Test

class WordbookProtocolTest {
    private val payload = """{"schemaVersion":1,"id":"cet4","wordCount":1,"words":[{"word":"abandon","translations":["v. 放弃"],"phonetic":"test","tags":["cet4"]}]}""".toByteArray()
    private fun book(hash: String = WordbookProtocol.sha256(payload)) = DownloadableBook("cet4", "cet4", "四级", "college", "", 1, "/api/wordbooks/cet4.json", hash)
    @Test fun verifiedBookRetainsSourceDefinitionsAndCanonicalIds() {
        val words = WordbookProtocol.detail(payload, book())
        assertEquals("abandon", words.single().word)
        assertEquals("v. 放弃", words.single().definition)
        assertEquals("zk", WordbookProtocol.canonicalId("middle_school_zk"))
        assertEquals("ky", WordbookProtocol.canonicalId("postgraduate_ky"))
    }
    @Test(expected = IllegalArgumentException::class) fun corruptDownloadIsRejected() {
        WordbookProtocol.detail(payload + byteArrayOf(32), book())
    }
    @Test(expected = IllegalArgumentException::class) fun wrongBookCannotBeInstalledWithAValidHash() {
        WordbookProtocol.detail(payload, book().copy(remoteId = "cet6"))
    }
    @Test(expected = IllegalArgumentException::class) fun truncatedBookCannotBeInstalled() {
        WordbookProtocol.detail(payload, book().copy(count = 2))
    }
    @Test(expected = IllegalArgumentException::class) fun unsafeBookIdsAreRejected() {
        WordbookProtocol.manifest("""{"schemaVersion":1,"wordbooks":[{"id":"../secret","jsonSha256":"${"a".repeat(64)}"}]}""")
    }
}
