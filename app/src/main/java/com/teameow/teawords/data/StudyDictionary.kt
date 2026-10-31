package com.teameow.teawords.data

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.json.JSONArray
import java.util.concurrent.TimeUnit

data class StudyDictionaryEntry(
    val definition: String, val uk: String, val us: String,
    val phrases: List<Pair<String, String>>, val forms: List<Pair<String, String>>
)

/** Explicit online lookup; successful results remain available offline. */
class StudyDictionary(private val helper: DatabaseHelper) {
    init { helper.writableDatabase.execSQL("CREATE TABLE IF NOT EXISTS study_dictionary_cache(word TEXT PRIMARY KEY, payload TEXT NOT NULL)") }
    private val client = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS).build()
    fun cached(word: String): StudyDictionaryEntry? = helper.readableDatabase.rawQuery(
        "SELECT payload FROM study_dictionary_cache WHERE word=?", arrayOf(LexicalText.normalize(word))
    ).use { if (it.moveToFirst()) runCatching { parse(it.getString(0)) }.getOrNull() else null }

    fun lookup(word: String): StudyDictionaryEntry {
        cached(word)?.let { return it }
        val url = "https://dict.youdao.com/jsonapi".toHttpUrl().newBuilder().addQueryParameter("q", word).build()
        val payload = client.newCall(Request.Builder().url(url).build()).execute().use {
            check(it.isSuccessful) { "词典暂时不可用" }
            it.body?.string() ?: error("词典返回空结果")
        }
        val entry = parse(payload)
        check(entry.definition.isNotBlank()) { "未查询到词义" }
        check(helper.writableDatabase.insertWithOnConflict("study_dictionary_cache", null,
            android.content.ContentValues().apply { put("word", LexicalText.normalize(word)); put("payload", payload) },
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE) != -1L)
        return entry
    }

    companion object {
        fun parse(payload: String): StudyDictionaryEntry {
            val root = JSONObject(payload)
            val head = root.optJSONObject("ec")?.optJSONArray("word")?.optJSONObject(0) ?: JSONObject()
            val simple = root.optJSONObject("simple")?.optJSONArray("word")?.optJSONObject(0) ?: root.optJSONObject("base")
            val meanings = mutableListOf<String>()
            head.optJSONArray("trs").objects().forEach { tr ->
                tr.optJSONArray("tr").objects().forEach { line ->
                    val values = line.optJSONObject("l")?.optJSONArray("i")
                    if (values != null) for (i in 0 until values.length()) values.optString(i).trim().takeIf { it.isNotEmpty() }?.let(meanings::add)
                }
            }
            val phrases = root.optJSONObject("phrs")?.optJSONArray("phrs").objects().mapNotNull {
                val phrase = it.optJSONObject("phr") ?: return@mapNotNull null
                val text = phrase.optJSONObject("headword")?.optJSONObject("l")?.optString("i").orEmpty()
                val meaning = phrase.optJSONArray("trs").objects().mapNotNull { row ->
                    row.optJSONObject("tr")?.optJSONObject("l")?.optString("i")?.takeIf(String::isNotBlank)
                }.joinToString("；")
                if (text.isBlank() || meaning.isBlank()) null else text to meaning
            }.distinct().take(12)
            val forms = (head.optJSONArray("wfs").objects() + root.optJSONArray("wfs").objects()).mapNotNull {
                val form = it.optJSONObject("wf") ?: return@mapNotNull null
                val name = form.optString("name"); val value = form.optString("value")
                if (name.isBlank() || value.isBlank()) null else name to value
            }.distinct()
            return StudyDictionaryEntry(meanings.distinct().joinToString("\n"),
                head.optString("ukphone").ifBlank { simple?.optString("ukphone").orEmpty() },
                head.optString("usphone").ifBlank { simple?.optString("usphone").orEmpty() }, phrases, forms)
        }
        private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList()
            else (0 until length()).mapNotNull { optJSONObject(it) }
    }
}
