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

    @Test fun directoryShowsNewRouteAndSearchesAcrossUnits() {
        val db=database()
        compose.setContent { androidx.compose.runtime.CompositionLocalProvider(LocalContext provides testContext) { TeaWordsTheme { CourseListScreen(db,onBack={},onOpen={}) } } }
        compose.onNodeWithText("入门英语").assertExists()
        compose.onNodeWithText("找课程").performTextInput("family")
        compose.onNodeWithText("Meet My Family").assertExists()
        compose.onNodeWithText("Welcome!").assertDoesNotExist()
    }
    @Test fun freeTabsAndFeedbackPersistAndSummaryRetriesOnlyMistakes() {
        val db=database();val lesson=CourseCatalog.all.first();val repo=CourseRepository(db)
        lesson.questions.forEach { repo.submit(lesson,it.id,if(it==lesson.questions.first()) "wrong" else it.answers.first(),0) }
        compose.setContent { androidx.compose.runtime.CompositionLocalProvider(LocalContext provides testContext) { TeaWordsTheme { CourseLessonScreen(db,lesson,onBack={}) } } }
        compose.onNodeWithText("总结",useUnmergedTree=true).performClick()
        compose.onNodeWithText("本课已完成").assertExists()
        compose.onNodeWithText("重练 1 道错题").performClick()
        compose.onNodeWithText(lesson.questions.first().answers.first()).performClick()
        compose.onNodeWithText("提交答案").performScrollTo().performClick()
        compose.waitUntil(5000) { repo.progress(lesson.id).mistakes.isEmpty() }
        assertEquals(6,repo.progress(lesson.id).answers.size)
    }
    @Test fun unavailableAudioKeepsDictationAndDraftWithoutRevealingTranslation() {
        val db=database();val lesson=CourseCatalog.all.first();val repo=CourseRepository(db)
        repo.save(lesson.id,CourseRepository.Checkpoint(section=2,question=2,draft="I am",started=true))
        compose.setContent { androidx.compose.runtime.CompositionLocalProvider(LocalContext provides testContext) { TeaWordsTheme { CourseLessonScreen(db,lesson,onBack={}) } } }
        compose.onNodeWithText("你的答案").assertExists()
        compose.onNodeWithText("I am",substring=false).assertExists()
        compose.onNodeWithText("提交答案").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("请先下载本课原声再听写。").assertExists()
        assertTrue(repo.progress(lesson.id).answers.isEmpty())
    }

    @Test fun captureCoursePagesOnSmallScreenInLightDarkAndLargeType() {
        val db=database();val lesson=CourseCatalog.all.first();val repo=CourseRepository(db)
        val audio=File(testContext.filesDir,"course_audio/${lesson.audio.sha256}.mp3")
        audio.parentFile!!.mkdirs()
        InstrumentationRegistry.getInstrumentation().context.assets.open("course-welcome.mp3").use { input -> audio.outputStream().use { input.copyTo(it) } }
        lesson.questions.forEach { repo.submit(lesson,it.id,if(it==lesson.questions.first()) "wrong" else it.answers.first(),0) }
        var appearance by mutableStateOf("light")
        var page by mutableIntStateOf(0)
        compose.setContent { androidx.compose.runtime.CompositionLocalProvider(LocalContext provides testContext) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,if(appearance=="large")1.5f else 1f)) {
                TeaWordsTheme(darkTheme=appearance=="dark" || appearance=="black",pureBlack=appearance=="black",dynamicColor=appearance=="dynamic") {
                    Box(Modifier.widthIn(max=320.dp).height(640.dp)) { Surface {
                        key(page,appearance) {
                            if(page==0) CourseListScreen(db,onBack={},onOpen={}) else CourseLessonScreen(db,lesson,onBack={})
                        }
                    } }
                }
            }
        } }
        val directory=File(testContext.filesDir,"course-rebuild").apply{mkdirs()}
        fun capture(name:String) {
            compose.waitForIdle()
            val image=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            File(directory,"$name.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}
        }
        for(theme in listOf("light","dark","black","dynamic","large")) {
            compose.runOnIdle { appearance=theme;page=0 }
            compose.onNodeWithText("找课程").assertExists();capture("directory-$theme")
        }
        compose.runOnIdle { appearance="light";page=1 }
        capture("lesson-text-light")
        compose.onNodeWithText("讲解",useUnmergedTree=true).performClick();capture("lesson-explanation")
        compose.onNodeWithText("练习",useUnmergedTree=true).performClick();capture("lesson-practice")
        compose.onNodeWithText("总结",useUnmergedTree=true).performClick();capture("lesson-summary")
        compose.runOnIdle { appearance="dark" }
        capture("lesson-summary-dark")
        compose.runOnIdle { appearance="large" }
        compose.onNodeWithText("课文",useUnmergedTree=true).performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag("course-segment-s09"))
        compose.onNodeWithTag("course-segment-s09").assertIsDisplayed()
        capture("lesson-text-large-scrolled")
        audio.delete()
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
        compose.setContent { TeaWordsTheme { CourseLessonScreen(db,lesson,onBack={}) } }
        compose.waitUntil(10000) { compose.onAllNodes(hasText("整课播放") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("整课播放").performClick()
        compose.waitUntil(7000) { repo.progress(lesson.id).checkpoint.let { it.sentence>=1 && it.positionMs>2600 } }
        compose.onNodeWithTag("course-segment-s02").assertIsDisplayed()
        compose.onNodeWithText("讲解",useUnmergedTree=true).performClick()
        compose.onNodeWithText("课文",useUnmergedTree=true).performClick()
        compose.onNodeWithText("整课播放").assertIsEnabled()
        assertTrue(repo.progress(lesson.id).checkpoint.positionMs>0)
    }

    @Test fun keyboardAtLargeFontKeepsDraftAndSubmitReachableAfterBackground() {
        val db=database();val lesson=CourseCatalog.all.first();val repo=CourseRepository(db)
        repo.save(lesson.id,CourseRepository.Checkpoint(section=2,question=4,started=true))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,1.5f)) {
                TeaWordsTheme { Box(Modifier.widthIn(max=320.dp).height(640.dp)) { CourseLessonScreen(db,lesson,onBack={}) } }
            }
        }
        compose.onNodeWithText("你的答案").performClick().performTextInput("Nice to meet you")
        compose.onNodeWithText("提交答案").performScrollTo().assertIsDisplayed()
        assertEquals("Nice to meet you",repo.progress(lesson.id).checkpoint.draft)
        val directory=File(testContext.filesDir,"course-rebuild").apply{mkdirs()}
        val screenshot=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(directory,"lesson-keyboard-large.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG,100,it) }
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.onNodeWithText("Nice to meet you",substring=false).assertExists()
        compose.onNodeWithText("提交答案").performScrollTo().performClick()
        compose.waitUntil(5000) { repo.progress(lesson.id).answers[lesson.questions[4].id]?.correct==true }
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
