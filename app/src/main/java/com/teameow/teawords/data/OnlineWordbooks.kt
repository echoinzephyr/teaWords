package com.teameow.teawords.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.google.gson.JsonParser
import com.teameow.teawords.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class DownloadableBook(val id: String, val remoteId: String, val title: String, val category: String,
    val description: String, val count: Int, val path: String, val sha256: String)
data class DownloadedWord(val word: String, val phonetic: String, val definition: String, val english: String,
    val tags: String, val forms: String, val frequency: String)

/** Same manifest/detail wire format as WordMoment. A shipped manifest pins each download's content. */
object WordbookProtocol {
    fun canonicalId(id: String) = when (id) {
        "middle_school_zk" -> "zk"; "high_school_gk" -> "gk"; "postgraduate_ky" -> "ky"; else -> id
    }
    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    fun manifest(json: String): List<DownloadableBook> {
        val root = JsonParser.parseString(json).asJsonObject
        require(root.get("schemaVersion")?.asInt == 1) { "词书目录版本不兼容，请更新应用" }
        val books = root.array("wordbooks").objects().map { book ->
            val id = book.string("id")
            require(id.matches(Regex("[a-zA-Z0-9_-]{1,80}"))) { "词书编号无效" }
            val hash = book.string("jsonSha256").lowercase()
            require(hash.matches(Regex("[a-f0-9]{64}"))) { "词书缺少有效校验值" }
            DownloadableBook(canonicalId(id), id, book.string("title"), book.string("stage"), book.string("description"),
                book.get("wordCount")?.asInt ?: 0, book.string("jsonUrl"), hash)
        }
        require(books.isNotEmpty() && books.size <= 200 && books.map { it.id }.distinct().size == books.size) { "词书目录无效" }
        return books
    }
    fun detail(bytes: ByteArray, book: DownloadableBook): List<DownloadedWord> {
        require(sha256(bytes) == book.sha256) { "词书校验失败，请重试下载" }
        val root = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
        require(root.get("schemaVersion")?.asInt == 1 && root.string("id") == book.remoteId) { "词书内容与目录不一致" }
        val entries = root.array("words").objects()
        require(entries.size == book.count && root.get("wordCount")?.asInt == book.count && entries.size in 1..100_000) { "词书词数不一致" }
        val words = entries.map { row ->
            val word = row.string("word").trim()
            require(word.isNotBlank() && word.length <= 160) { "词书包含无效词条" }
            DownloadedWord(word, row.string("phoneticUk").ifBlank { row.string("phonetic") },
                row.array("translations").strings().joinToString("\n"), row.string("english"),
                row.array("tags").strings().joinToString(" "), row.string("forms"), row.string("frequency"))
        }
        require(words.map { LexicalText.normalize(it.word) }.distinct().size == words.size) { "词书包含重复词条" }
        return words
    }
}

class OnlineWordbooks(private val context: Context, private val baseUrl: String = BuildConfig.WORDBOOK_BASE_URL) {
    private val seed by lazy { context.assets.open("wordbooks-manifest.json").bufferedReader().use { it.readText() } }
    fun catalog() = WordbookProtocol.manifest(seed)
    val configured get() = baseUrl.isNotBlank()

    suspend fun refreshCatalog(): List<DownloadableBook> {
        val payload = DictionaryHttp.get(url("/api/wordbooks/manifest.json"))
        require(WordbookProtocol.sha256(payload.toByteArray(Charsets.UTF_8)) == WordbookProtocol.sha256(seed.toByteArray(Charsets.UTF_8))) {
            "服务器词书版本已变化，请先更新应用；已下载内容仍可使用"
        }
        return WordbookProtocol.manifest(payload)
    }

    fun isDownloaded(id: String): Boolean = runCatching {
        SQLiteDatabase.openDatabase(BundledDictionaryInstaller(context).targetFile().path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT 1 FROM word_book_entries WHERE book_id=? LIMIT 1", arrayOf(id)).use { it.moveToFirst() }
        }
    }.getOrDefault(false)

    suspend fun ensureDownloaded(ids: Set<String>, onProgress: (String, Long, Long) -> Unit = { _, _, _ -> }) = gate.withLock {
        withContext(Dispatchers.IO) {
            BundledDictionaryInstaller(context).install()
            val wanted = catalog().filter { it.id in ids }
            require(wanted.size == ids.size) { "所选词书不在当前下载目录中" }
            val missing = wanted.filterNot { isDownloaded(it.id) }
            if (missing.isEmpty()) return@withContext
            refreshCatalog()
            for (book in missing) {
                currentCoroutineContext().ensureActive()
                val part = File(context.cacheDir, "book-${book.id}-${book.sha256}.part")
                if (!part.exists() || WordbookProtocol.sha256(part.readBytes()) != book.sha256) {
                    download(book, part) { done, total -> onProgress(book.title, done, total) }
                }
                currentCoroutineContext().ensureActive()
                val words = try { WordbookProtocol.detail(part.readBytes(), book) }
                catch (e: Exception) { part.delete(); throw e }
                importBook(book, words)
                part.delete()
            }
        }
    }

    private fun url(path: String): String {
        check(configured) { "词书下载服务尚未配置；已下载词书和个人词库仍可使用" }
        val base = baseUrl.trimEnd('/').toHttpUrl()
        require(base.isHttps || (BuildConfig.DEBUG && base.host in setOf("127.0.0.1", "localhost"))) { "词书下载需要 HTTPS 地址" }
        val result = base.resolve(path) ?: error("下载地址无效")
        require(result.host == base.host && result.scheme == base.scheme && result.port == base.port) { "词书地址超出下载服务范围" }
        return result.toString()
    }

    private suspend fun download(book: DownloadableBook, part: File, progress: (Long, Long) -> Unit): Unit = suspendCancellableCoroutine { continuation ->
        val offset = part.takeIf { it.exists() }?.length() ?: 0L
        val request = Request.Builder().url(url(book.path)).header("Accept-Encoding", "identity")
            .apply { if (offset > 0) header("Range", "bytes=$offset-") }.build()
        val call = downloadClient.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use {
                        if (it.code == 416) { part.delete(); throw IOException("下载片段已失效，请重试") }
                        if (it.code !in setOf(200, 206)) throw IOException("词书下载失败（${it.code}）")
                        val append = it.code == 206 && offset > 0
                        if (it.code == 206) require(it.header("Content-Range")?.startsWith("bytes $offset-") == true) { "服务器续传范围无效" }
                        val body = it.body ?: throw IOException("词书返回空内容")
                        val total = body.contentLength().takeIf { size -> size >= 0 }?.plus(if (append) offset else 0L) ?: 0L
                        require(total <= MAX_BYTES) { "词书文件过大" }
                        synchronized(fileLock) {
                            if (!continuation.isActive) return
                            var received = if (append) offset else 0L
                            body.byteStream().use { input ->
                                java.io.FileOutputStream(part, append).use { output ->
                                    val buffer = ByteArray(64 * 1024)
                                    while (continuation.isActive) {
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        received += count
                                        require(received <= MAX_BYTES) { "词书文件过大" }
                                        output.write(buffer, 0, count)
                                        progress(received, total)
                                    }
                                }
                            }
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                    }
                } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
            }
        })
    }

    private fun importBook(book: DownloadableBook, words: List<DownloadedWord>) {
        SQLiteDatabase.openDatabase(BundledDictionaryInstaller(context).targetFile().path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.beginTransaction()
            try {
                db.execSQL("CREATE TABLE IF NOT EXISTS word_identity(id INTEGER PRIMARY KEY, normalized TEXT NOT NULL UNIQUE)")
                db.execSQL("INSERT OR IGNORE INTO word_identity SELECT id,normalized FROM lex_words")
                db.execSQL("CREATE TABLE IF NOT EXISTS downloaded_books(book_id TEXT PRIMARY KEY, sha256 TEXT NOT NULL, downloaded_at INTEGER NOT NULL)")
                var next = db.rawQuery("SELECT coalesce(max(id),0)+1 FROM word_identity", null).use { it.moveToFirst(); it.getLong(0) }
                db.execSQL("DELETE FROM word_book_entries WHERE book_id=?", arrayOf(book.id))
                for (word in words) {
                    val normalized = LexicalText.normalize(word.word)
                    val id = db.rawQuery("SELECT id FROM word_identity WHERE normalized=?", arrayOf(normalized)).use {
                        if (it.moveToFirst()) it.getLong(0) else next++.also { newId ->
                            db.execSQL("INSERT INTO word_identity VALUES(?,?)", arrayOf<Any>(newId, normalized))
                        }
                    }
                    val values = ContentValues().apply {
                        put("id", id); put("normalized", normalized); put("word", word.word); put("phonetic", word.phonetic)
                        put("zh", word.definition); put("en", word.english); put("tags", word.tags); put("forms", word.forms)
                        put("frequency", word.frequency); put("source", "ECDICT")
                    }
                    val inserted = db.insertWithOnConflict("lex_words", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
                    if (inserted) {
                        db.execSQL("INSERT INTO lex_fts(docid,lemma,definition) VALUES(?,?,?)", arrayOf<Any>(id, normalized, word.english))
                        Regex("([a-zA-Z0-9]+):([^\\s]+)").findAll(word.forms).forEach { form ->
                            db.execSQL("INSERT INTO lex_forms(form,word_id,kind) VALUES(?,?,?)",
                                arrayOf<Any>(LexicalText.normalize(form.groupValues[2]), id, form.groupValues[1]))
                        }
                        word.definition.filter { it in '\u3400'..'\u9fff' }.toSet().forEach { token ->
                            db.execSQL("INSERT INTO lex_zh(token,word_id,freq_rank) VALUES(?,?,0)", arrayOf<Any>(token.toString(), id))
                        }
                        if (word.definition.isNotBlank()) db.execSQL("INSERT INTO lex_senses(word_id,source,language,ordinal,definition,confidence) VALUES(?,'ECDICT','zh',0,?,0)", arrayOf<Any>(id, word.definition))
                    }
                    db.execSQL("INSERT OR IGNORE INTO word_book_entries(book_id,word_id,source) VALUES(?,?,'ECDICT')", arrayOf<Any>(book.id, id))
                }
                db.execSQL("UPDATE word_books SET total_word_count=?,updated_at=? WHERE id=?", arrayOf<Any>(words.size, System.currentTimeMillis(), book.id))
                db.execSQL("INSERT OR REPLACE INTO lex_books_meta VALUES(?,?)", arrayOf<Any>(book.id, words.size))
                db.execSQL("INSERT OR REPLACE INTO downloaded_books VALUES(?,?,?)", arrayOf<Any>(book.id, book.sha256, System.currentTimeMillis()))
                db.execSQL("INSERT OR REPLACE INTO dictionary_meta VALUES('word_count',(SELECT count(*) FROM lex_words))")
                db.execSQL("DELETE FROM lex_token_stats")
                db.execSQL("INSERT INTO lex_token_stats SELECT token,count(DISTINCT word_id) FROM lex_zh GROUP BY token")
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
    }
    companion object {
        private const val MAX_BYTES = 25L * 1024 * 1024
        private val gate = Mutex()
        private val fileLock = Any()
        private val downloadClient = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    }
}
