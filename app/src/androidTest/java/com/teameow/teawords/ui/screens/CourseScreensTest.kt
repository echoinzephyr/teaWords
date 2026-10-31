package com.teameow.teawords.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.teameow.teawords.data.*
import com.teameow.teawords.ui.theme.TeaWordsTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CourseScreensTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var helper: DatabaseHelper? = null
    private var name = ""

    @After fun clean() {
        helper?.close()
        if (name.isNotEmpty()) compose.activity.deleteDatabase(name)
    }

    @Test fun lessonShowsImmediateFeedbackAndResumesAtSavedStep() {
        name = "course-ui-${System.nanoTime()}.db"
        val database = DatabaseHelper(compose.activity, name)
        helper = database
        database.ensureSchema()
        val lesson = CourseCatalog.postgraduate.first()
        compose.setContent {
            TeaWordsTheme { CourseLessonScreen(database, lesson, onBack = {}, onReviewWeak = {}) }
        }
        compose.onNodeWithText("开始练习").performClick()
        compose.waitUntil(5000) { CourseRepository(database).progress(lesson.id).step == 1 }
        compose.onNodeWithText("继续").performClick()
        compose.onNodeWithText(lesson.meaning).performClick()
        compose.waitUntil(5000) { CourseRepository(database).progress(lesson.id).step == 2 }
        compose.onNode(hasText("答对了。", substring = true)).assertIsDisplayed()
        assertEquals(2, CourseRepository(database).progress(lesson.id).step)
    }

    @Test fun directoryShowsUnitsAndCanFindALessonInAnotherTrack() {
        name = "course-directory-${System.nanoTime()}.db"
        val database = DatabaseHelper(compose.activity, name)
        helper = database
        compose.setContent {
            TeaWordsTheme { CourseListScreen(database, onBack = {}, onOpen = {}, onReviewWeak = {}) }
        }
        compose.onNodeWithText("学术与基础议题", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("查看课程")[0].performClick()
        compose.onNodeWithText("1. 研究与证据").assertExists()
        compose.onNodeWithText("雅思", useUnmergedTree = true).performClick()
        compose.onNodeWithText("社区与日常", substring = true).assertIsDisplayed()
        compose.onNodeWithText("找课程").performTextInput("旅游")
        compose.onNodeWithText("3. 旅游与居民").assertExists()
        compose.onNodeWithText("1. 社区花园").assertDoesNotExist()
    }

    @Test fun retryingALessonMistakeDoesNotCreateAnotherLearningAnswer() {
        name = "course-retry-${System.nanoTime()}.db"
        val database = DatabaseHelper(compose.activity, name)
        helper = database
        val lesson = CourseCatalog.postgraduate.first()
        val repository = CourseRepository(database)
        (0 until CourseRepository.STEPS).forEach { step ->
            repository.answer(lesson, step, correct = step != 1, elapsedMs = 100)
        }
        assertEquals(listOf(1), repository.wrongSteps(lesson.id))
        compose.setContent {
            TeaWordsTheme { CourseLessonScreen(database, lesson, onBack = {}, onReviewWeak = {}) }
        }
        compose.onNodeWithText("本课薄弱题型：词义选择").assertExists()
        compose.onNodeWithText("重练 1 道错题").performClick()
        compose.onNodeWithText(lesson.meaning).performClick()
        compose.onNodeWithText("本次重练不增加掌握次数", substring = true).assertExists()
        compose.onNodeWithText("返回本课总结").performClick()
        assertEquals(CourseRepository.STEPS, repository.progress(lesson.id).step)
        database.readableDatabase.rawQuery("SELECT COUNT(*) FROM course_answers WHERE lesson_id=?",
            arrayOf(lesson.id)).use { it.moveToFirst(); assertEquals(CourseRepository.STEPS, it.getInt(0)) }
    }
    @Test fun readingOptionsStayAvailableWhileLongPassageScrolls() {
        name = "course-reading-${System.nanoTime()}.db"
        val database = DatabaseHelper(compose.activity, name)
        helper = database
        database.ensureSchema()
        val lesson = CourseCatalog.postgraduate.first()
        val repository = CourseRepository(database)
        (0 until 5).forEach { repository.answer(lesson, it, true, 100) }
        compose.setContent { TeaWordsTheme {
            CourseLessonScreen(database, lesson, onBack = {}, onReviewWeak = {})
        } }
        compose.onNodeWithText(lesson.readingAnswer).assertIsDisplayed()
        val before = compose.onNodeWithTag("tea-exercise-actions").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("tea-exercise-content").performTouchInput { swipeUp() }
        assertEquals(before, compose.onNodeWithTag("tea-exercise-actions").getUnclippedBoundsInRoot())
        compose.onNodeWithText(lesson.readingAnswer).performClick()
        compose.waitUntil(5000) { repository.progress(lesson.id).step == 6 }
        compose.onNodeWithText("继续").assertIsDisplayed()
    }
}
