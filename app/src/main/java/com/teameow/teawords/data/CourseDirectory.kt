package com.teameow.teawords.data

data class CourseChapter(val title: String, val description: String, val lessons: List<CourseLesson>)
object CourseDirectory {
    fun chapters(): List<CourseChapter> = listOf(
        "初次见面" to "打招呼、介绍自己与身边物品",
        "日常生活" to "房间、方位与正在进行的活动",
        "身边的世界" to "天气、出行、社区与家人"
    ).mapIndexed { index, (title, description) ->
        CourseChapter("单元 ${index + 1} · $title", description, CourseCatalog.all.filter { it.unit == index + 1 })
    }
    fun search(query: String): List<CourseLesson> {
        val key = query.trim()
        return CourseCatalog.all.filter { lesson ->
            key.isNotEmpty() && (lesson.number.toString() == key ||
                (listOf(lesson.title, lesson.subtitle) + lesson.objectives + lesson.keyPhrases.map { it.english })
                    .any { it.contains(key, ignoreCase = true) })
        }
    }
}
