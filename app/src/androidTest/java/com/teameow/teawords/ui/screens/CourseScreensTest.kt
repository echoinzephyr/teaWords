package com.teameow.teawords.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.teameow.teawords.data.*
import com.teameow.teawords.ui.theme.TeaWordsTheme
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import android.graphics.Bitmap

class CourseScreensTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val testContext get() = compose.activity
    private var helper: DatabaseHelper? = null
    private var name = ""
    private var preservedAudio: ByteArray? = null
    private fun cachedAudio() = File(testContext.filesDir,"course_audio/${CourseCatalog.all.first().audio.sha256}.mp3")
    @Before fun showTestActivity() {
        val audio = cachedAudio()
        preservedAudio = audio.takeIf { it.exists() }?.readBytes()
        audio.delete()
        compose.activityRule.scenario.onActivity { activity ->
            @Suppress("DEPRECATION")
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
    }
    private fun database(): DatabaseHelper {
        name="course-ui-${System.nanoTime()}.db"
        return DatabaseHelper(testContext,name).also { helper=it }
    }
    @After fun cleanup() {
        helper?.close(); if(name.isNotEmpty()) testContext.deleteDatabase(name)
        cachedAudio().delete()
        preservedAudio?.let { cachedAudio().apply { parentFile!!.mkdirs() }.writeBytes(it) }
    }

    private fun seedAudio() {
        val audio=cachedAudio().apply { parentFile!!.mkdirs() }
        InstrumentationRegistry.getInstrumentation().context.assets.open("course-welcome.mp3").use { input -> audio.outputStream().use { input.copyTo(it) } }
    }
    private fun capture(name:String) {
        compose.waitForIdle()
        val directory=File(testContext.filesDir,"course-teaching").apply{mkdirs()}
        val image=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(directory,"$name.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun directoryShowsMissionAndSearchesAcrossUnits() {
        val db=database()
        compose.setContent { TeaWordsTheme { CourseListScreen(db,onBack={},onOpen={}) } }
        compose.onNodeWithText("入门英语").assertExists()
        compose.onNodeWithText("找课程").performTextInput("family")
        compose.onNodeWithText("Meet My Family").assertExists()
    }
    @Test fun lessonTeachesBeforeTransferAndFinishesWithAnHonestReviewPlan() {
        val db=database();val lesson=CourseCatalog.all.first();val repo=CourseRepository(db)
        seedAudio()
        compose.setContent { TeaWordsTheme { CourseLessonScreen(db,lesson,onBack={}) } }
        compose.onNodeWithText("开始学习").performClick()
        fun answerQuestion(q:CourseQuestion) {
            if(q.type=="dictation") {
                compose.onNodeWithTag("tea-exercise-content").performScrollToNode(hasText("听题目原声"))
                compose.onNodeWithText("听题目原声").performClick()
            }
            if(q.options.isEmpty()) {
                compose.onNodeWithTag("tea-exercise-content").performScrollToNode(hasText("你的答案"))
                compose.onNodeWithText("你的答案").performTextInput(q.answers.first())
            } else {
                compose.onNodeWithTag("tea-exercise-content").performScrollToNode(hasText(q.answers.first()))
                compose.onNodeWithText(q.answers.first()).performClick()
            }
            compose.onNodeWithText("提交答案").performClick()
            compose.onNodeWithText("这次答对了").assertExists()
            compose.onNodeWithText("继续").performClick()
        }
        lesson.questions.filter{it.phase=="understand"}.forEach(::answerQuestion)
        compose.onNodeWithText(lesson.teaching.points.first().expression).assertExists()
        repeat(2){compose.onNodeWithText("下一个表达").performClick()}
        compose.onNodeWithText("带着提示试一试").performClick()
        lesson.questions.filter{it.phase in setOf("guided","transfer","listen")}.forEach(::answerQuestion)
        compose.onNodeWithText("本轮已过关").assertExists()
        assertTrue(repo.progress(lesson.id).complete)
        compose.waitUntil(5000){repo.progress(lesson.id).checkpoint.reviewAt>System.currentTimeMillis()}
    }
    @Test fun allWrongAnswersRemainPendingAndWrongQuestionRetryCanRepairThem() {
        val db=database();val lesson=CourseCatalog.all.first();val repo=CourseRepository(db)
        lesson.questions.forEach { repo.submit(lesson,it.id,if(it==lesson.questions.first())"wrong" else it.answers.first(),0) }
        compose.setContent { TeaWordsTheme { CourseLessonScreen(db,lesson,onBack={}) } }
        compose.onNodeWithText("练过了，再巩固一下").assertExists()
        assertFalse(repo.progress(lesson.id).complete)
        compose.onNodeWithText("巩固 1 道错题").performClick()
        compose.onNodeWithTag("tea-exercise-content").performScrollToNode(hasText(lesson.questions.first().answers.first()))
        compose.onNodeWithText(lesson.questions.first().answers.first()).performClick()
        compose.onNodeWithText("提交答案").performClick()
        compose.onNodeWithText("继续").performClick()
        compose.onNodeWithText("本轮已过关").assertExists()
        assertEquals(8,repo.progress(lesson.id).answers.size)
    }
    @Test fun missingAudioKeepsListeningDraftAndNeverSubstitutesChinese() {
        val db=database();val lesson=CourseCatalog.all.first();val repo=CourseRepository(db)
        repo.save(lesson.id,CourseRepository.Checkpoint(guideVersion=2,guidedStage=5,
            drafts=mapOf("listen-1" to "I am"),started=true))
        compose.setContent { TeaWordsTheme { CourseLessonScreen(db,lesson,onBack={}) } }
        compose.onNodeWithText("提交答案").assertIsNotEnabled()
        compose.onNodeWithTag("tea-exercise-content").performScrollToNode(hasText("你的答案"))
        compose.onNodeWithText("I am",substring=false).assertExists()
        assertTrue(repo.progress(lesson.id).answers.isEmpty())
    }
    @Test fun referenceReturnsToTheSameTransferDraft() {
        val db=database();val lesson=CourseCatalog.all.first();val repo=CourseRepository(db)
        repo.save(lesson.id,CourseRepository.Checkpoint(guideVersion=2,guidedStage=4,
            drafts=mapOf("transfer-1" to "I am M"),started=true))
        compose.setContent { TeaWordsTheme { CourseLessonScreen(db,lesson,onBack={}) } }
        compose.onNodeWithText("原文与词句笔记").performClick()
        compose.onNodeWithText("词句笔记",useUnmergedTree=true).assertExists()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("tea-exercise-content").performScrollToNode(hasText("你的答案"))
        compose.onNodeWithText("I am M",substring=false).assertExists()
        assertTrue("transfer-1" in repo.progress(lesson.id).checkpoint.hinted)
    }
    @Test fun captureGuidedLearningOnSmallScreensAndThemes() {
        val db=database();val lesson=CourseCatalog.all.first();val repo=CourseRepository(db)
        seedAudio()
        var appearance by mutableStateOf("light")
        var targetStage by mutableIntStateOf(0)
        var generation by mutableIntStateOf(0)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,if(appearance=="large")1.5f else 1f)) {
                TeaWordsTheme(darkTheme=appearance in listOf("dark","black"),pureBlack=appearance=="black",dynamicColor=appearance=="dynamic") {
                    Box(Modifier.widthIn(max=320.dp).height(640.dp)) { Surface {
                        key(appearance,generation) { CourseLessonScreen(db,lesson,onBack={}) }
                    } }
                }
            }
        }
        for(theme in listOf("light","dark","black","dynamic","large")) {
            compose.runOnIdle { appearance=theme }
            capture("intro-$theme")
        }
        for(stage in listOf(1,2,3,4,5)) {
            compose.runOnIdle {
                repo.save(lesson.id,repo.progress(lesson.id).checkpoint.copy(guideVersion=2,guidedStage=stage,question=0))
                appearance="light";targetStage=stage;generation++
            }
            capture("activity-$stage")
        }
    }

    @Test fun nativePlaybackSupportsSentenceLoopSpeedAndPausesOnBackground() {
        val lesson=CourseCatalog.all.first()
        val file=File(testContext.cacheDir,"course-native-test.mp3")
        InstrumentationRegistry.getInstrumentation().context.assets.open("course-welcome.mp3").use{input->file.outputStream().use{input.copyTo(it)}}
        lateinit var player: CourseAudioPlayer
        var savedPosition=0
        compose.setContent { androidx.compose.runtime.CompositionLocalProvider(LocalContext provides testContext) {
            val current=rememberCourseAudioPlayer(lesson){savedPosition=it.position}
            SideEffect { player=current }
            LaunchedEffect(current) { current.prepare(file,4000) }
        } }
        compose.waitUntil(10000){player.ready}
        // Prepared is not Started: changing tabs must never call MediaPlayer.pause in this state.
        compose.runOnIdle { player.pause(); assertNull(player.error); assertTrue(player.ready) }
        compose.runOnIdle { player.changeSpeed(0.75f);player.loop=true;assertTrue(player.play(2)) }
        compose.waitUntil(5000){player.position>=lesson.segments[2].startMs && player.playing}
        compose.waitUntil(5000){player.position > lesson.segments[2].startMs + 300}
        compose.waitUntil(5000){player.position < lesson.segments[2].startMs + 200}
        compose.runOnIdle { assertEquals(0.75f,player.speed);player.pause();assertFalse(player.playing);player.play() }
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        assertFalse(player.playing);assertTrue(savedPosition>0)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        assertFalse(player.playing)
        file.delete()
    }

    @Test fun invalidAudioReportsOneRecoverableErrorWithoutCallingNativePauseAgain() {
        val file=File(testContext.cacheDir,"course-invalid.mp3").apply { writeText("invalid audio") }
        lateinit var player: CourseAudioPlayer
        compose.setContent { androidx.compose.runtime.CompositionLocalProvider(LocalContext provides testContext) {
            val current=rememberCourseAudioPlayer(CourseCatalog.all.first()){}
            SideEffect { player=current }
            LaunchedEffect(current) { current.prepare(file,0) }
        } }
        compose.waitUntil(10000){player.error != null}
        compose.runOnIdle { player.pause();player.tick();assertFalse(player.playing);assertFalse(player.ready) }
        // Waiting for idle also detects a recursive native error callback storm.
        compose.waitForIdle()
        file.delete()
    }

    @Test fun playbackFollowsSentenceAndTabChangesKeepItPaused() {
        val db=database();val lesson=CourseCatalog.all.first();val repo=CourseRepository(db)
        val audio=cachedAudio().apply { parentFile!!.mkdirs() }
        InstrumentationRegistry.getInstrumentation().context.assets.open("course-welcome.mp3").use { input -> audio.outputStream().use { input.copyTo(it) } }
        compose.setContent { TeaWordsTheme { CourseReferenceScreen(db,lesson,onBack={}) } }
        compose.waitUntil(10000) { compose.onAllNodes(hasText("整课播放") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("整课播放").performClick()
        compose.waitUntil(7000) { repo.progress(lesson.id).checkpoint.let { it.sentence>=1 && it.positionMs>2600 } }
        compose.onNodeWithTag("course-segment-s02").assertIsDisplayed()
        compose.onNodeWithText("词句笔记",useUnmergedTree=true).performClick()
        compose.onNodeWithText("课文",useUnmergedTree=true).performClick()
        compose.onNodeWithText("整课播放").assertIsEnabled()
        assertTrue(repo.progress(lesson.id).checkpoint.positionMs>0)
    }

    @Test fun keyboardAtLargeFontKeepsDraftAndSubmitReachableAfterBackground() {
        val db=database();val lesson=CourseCatalog.all.first();val repo=CourseRepository(db)
        repo.save(lesson.id,CourseRepository.Checkpoint(guideVersion=2,guidedStage=4,question=0,started=true))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,1.5f)) {
                TeaWordsTheme { Box(Modifier.widthIn(max=320.dp).height(640.dp)) { CourseLessonScreen(db,lesson,onBack={}) } }
            }
        }
        compose.onNodeWithText("你的答案").performClick().performTextInput("I am Mei")
        compose.onNodeWithText("提交答案").performScrollTo().assertIsDisplayed()
        assertEquals("I am Mei",repo.progress(lesson.id).checkpoint.drafts["transfer-1"])
        val directory=File(testContext.filesDir,"course-teaching").apply{mkdirs()}
        val screenshot=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(directory,"lesson-keyboard-large.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG,100,it) }
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.onNodeWithText("I am Mei",substring=false).assertExists()
        compose.onNodeWithText("提交答案").performScrollTo().performClick()
        compose.waitUntil(5000) { repo.progress(lesson.id).answers["transfer-1"]?.correct==true }
    }

    @Test fun localRecordingCanBeSavedReplayedAndDeletedAfterBackground() {
        val id="verification-${System.nanoTime()}"
        val file=File(testContext.filesDir,"course_recordings/$id.m4a")
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.executeShellCommand("pm grant ${testContext.packageName} android.permission.RECORD_AUDIO").close()
        var recorded=false
        compose.setContent { TeaWordsTheme {
            androidx.compose.foundation.layout.Column { CourseVoiceRecorder(id,onHasRecordingChange={recorded=it}) }
        } }
        try {
            compose.onNodeWithText("开始录音").performClick()
            compose.onNodeWithText("结束录音").assertExists()
            android.os.SystemClock.sleep(1200)
            compose.onNodeWithText("结束录音").performClick()
            compose.waitUntil(5000){recorded && file.length()>0}
            android.media.MediaMetadataRetriever().use { media ->
                media.setDataSource(file.absolutePath)
                assertTrue(media.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()>500)
            }
            compose.onNodeWithText("回听").performClick()
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            assertTrue("Recording must survive background",file.exists())
            compose.waitUntil(5000) { compose.onAllNodes(hasText("删除录音")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("删除录音").performClick()
            compose.waitUntil(5000){!recorded && !file.exists()}
        } finally { file.delete() }
    }
}
