package com.teameow.teawords.data

import com.google.gson.Gson
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

data class CourseSource(val publisher: String, val url: String, val rightsUrl: String, val credit: String)
data class CourseAudio(val url: String, val sha256: String, val bytes: Long, val durationMs: Int)
data class CourseSegment(val id: String, val speaker: String, val english: String, val chinese: String,
                         val startMs: Int, val endMs: Int)
data class CourseKeyPhrase(val english: String, val chinese: String, val segmentId: String)
data class CourseExplanation(val title: String, val text: String, val example: String, val segmentId: String)
data class CourseQuestion(val id: String, val type: String, val prompt: String, val options: List<String>,
                          val answers: List<String>, val explanation: String, val segmentId: String) {
    fun accepts(input: String): Boolean = answers.any { CourseAnswers.normalize(it) == CourseAnswers.normalize(input) }
}
data class CourseLesson(val id: String, val number: Int, val unit: Int, val title: String, val subtitle: String,
                        val objectives: List<String>, val prerequisites: List<String>, val skills: List<String>,
                        val minutes: Int, val source: CourseSource, val audio: CourseAudio,
                        val segments: List<CourseSegment>, val keyPhrases: List<CourseKeyPhrase>,
                        val explanations: List<CourseExplanation>, val questions: List<CourseQuestion>)
data class CoursePack(val schemaVersion: Int, val contentVersion: String, val title: String,
                      val lessons: List<CourseLesson>)

object CourseAnswers {
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(Regex("[’'‘]"), "")
        .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ").trim().replace(Regex("\\s+"), " ")
}

/** Fixed material. No dictionary download or network connection is required to open a lesson. */
object CourseCatalog {
    val pack: CoursePack by lazy {
        val bytes = checkNotNull(javaClass.getResourceAsStream("/courses/beginner-v1.json")).use { it.readBytes() }
        val digest = checkNotNull(javaClass.getResourceAsStream("/courses/beginner-v1.sha256"))
            .bufferedReader().use { it.readText().trim() }
        check(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } == digest)
        Gson().fromJson(bytes.toString(Charsets.UTF_8), CoursePack::class.java).also(::validate)
    }
    val all get() = pack.lessons
    fun validate(pack: CoursePack) {
        require(pack.schemaVersion == 1 && pack.contentVersion.isNotBlank())
        require(pack.lessons.size == 12 && pack.lessons.map { it.id }.distinct().size == 12)
        pack.lessons.forEachIndexed { index, lesson ->
            require(lesson.number == index + 1 && lesson.unit == index / 4 + 1 && lesson.minutes in 8..12)
            require(lesson.source.url.startsWith("https://learningenglish.voanews.com/") && lesson.source.credit.isNotBlank())
            require(lesson.source.rightsUrl.startsWith("https://") && lesson.objectives.isNotEmpty() && lesson.skills.isNotEmpty())
            require(lesson.prerequisites.all { id -> pack.lessons.take(index).any { it.id == id } })
            require(lesson.audio.url.startsWith("https://") && lesson.audio.sha256.matches(Regex("[a-f0-9]{64}")))
            require(lesson.audio.bytes > 0 && lesson.audio.durationMs > 0 && lesson.segments.isNotEmpty())
            val ids = lesson.segments.map { it.id }.toSet()
            require(ids.size == lesson.segments.size)
            var previousEnd = 0
            lesson.segments.forEach {
                require(it.english.isNotBlank() && it.chinese.isNotBlank())
                require(it.startMs >= previousEnd && it.endMs > it.startMs && it.endMs <= lesson.audio.durationMs)
                previousEnd = it.endMs
            }
            require(lesson.keyPhrases.size in 6..10 && lesson.explanations.size in 1..2)
            require(lesson.keyPhrases.all { it.segmentId in ids && it.english.isNotBlank() && it.chinese.isNotBlank() })
            require(lesson.explanations.all { it.segmentId in ids && it.text.isNotBlank() && it.example.isNotBlank() })
            require(lesson.questions.map { it.id }.distinct().size == lesson.questions.size)
            listOf("comprehension", "dictation", "expression").forEach { type ->
                require(lesson.questions.count { it.type == type } >= 2)
            }
            lesson.questions.forEach { q ->
                require(q.type in setOf("comprehension", "dictation", "expression") && q.segmentId in ids)
                require(q.prompt.isNotBlank() && q.answers.isNotEmpty() && q.answers.all { CourseAnswers.normalize(it).isNotBlank() })
                require(q.explanation.isNotBlank() && (q.options.isEmpty() || q.answers.first() in q.options))
                require(q.options.distinct().size == q.options.size)
                if (q.type == "dictation") require(q.options.isEmpty() && q.accepts(lesson.segments.single { it.id == q.segmentId }.english))
            }
        }
    }
}
