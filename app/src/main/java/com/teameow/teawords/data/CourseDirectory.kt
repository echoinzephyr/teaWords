package com.teameow.teawords.data

/** Learner-facing course units. Lesson ids stay stable so changing the directory never resets progress. */
data class CourseChapter(val title: String, val description: String, val lessons: List<CourseLesson>)

object CourseDirectory {
    val tracks = listOf("ky", "ielts", "toefl")

    fun trackName(id: String) = when (id) {
        "ky" -> "考研英语"
        "ielts" -> "雅思"
        "toefl" -> "托福"
        else -> id
    }

    fun lessons(track: String): List<CourseLesson> = CourseCatalog.all.filter { it.bookId == track }

    fun chapters(track: String): List<CourseChapter> {
        val units = when (track) {
            "ky" -> listOf(
                Triple(1..4, "学术与基础议题", "研究、教育、环境、经济"),
                Triple(5..8, "科技与社会", "技术、健康、公共服务、媒体"),
                Triple(9..12, "城市与文化", "交通、工作、规则、历史"),
                Triple(13..20, "研究与变化", "研究方法、气候、消费与技术"),
                Triple(21..30, "公共生活", "健康、机会、城市、职业与学习"),
                Triple(31..40, "信息与发展", "媒体、文化、政策、科学与农业")
            )
            "ielts" -> listOf(
                Triple(1..5, "社区与日常", "社区、交通、旅游、媒体与饮食"),
                Triple(6..10, "学习与城市", "教育、建筑、工作、艺术与公园")
            )
            "toefl" -> listOf(
                Triple(1..5, "自然与历史", "生态、地质、天文与古代工具"),
                Triple(6..10, "科学与文化", "经济、记忆、文学、化学与地貌")
            )
            else -> emptyList()
        }
        val byNumber = lessons(track).associateBy { it.id.substringAfter('-').toIntOrNull() }
        return units.mapIndexed { index, (range, name, description) ->
            CourseChapter("单元 ${index + 1} · $name", description, range.mapNotNull(byNumber::get))
        }
    }

    fun search(track: String, query: String): List<CourseLesson> {
        val key = query.trim()
        if (key.isEmpty()) return emptyList()
        return lessons(track).filter { lesson ->
            lesson.title.contains(key, ignoreCase = true) ||
                lesson.focus.contains(key, ignoreCase = true) ||
                lesson.words.any { it.contains(key, ignoreCase = true) } ||
                lesson.id.substringAfter('-').trimStart('0') == key.trimStart('0')
        }
    }
}
