package com.teameow.teawords.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.teameow.teawords.data.search.DictionaryGateway
import org.junit.Assert.*
import org.junit.Test

class CourseContentDeviceTest {
    @Test fun everyFocusExistsInItsBookAndPilotCoversAtLeastThreeHundredWords() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(DictionaryGateway.open(context))
        val dictionary = checkNotNull(DictionaryGateway.sourceOrNull())
        val byBook = listOf("ky", "ielts", "toefl").associateWith { id ->
            dictionary.learningWords(setOf(id)).map { it.word }.toSet()
        }
        CourseCatalog.all.forEach { lesson ->
            assertTrue("${lesson.id}: ${lesson.focus} missing from ${lesson.bookId}",
                lesson.focus in checkNotNull(byBook[lesson.bookId]))
        }
        val pilot = CourseCatalog.pilotWords.toSet().intersect(checkNotNull(byBook["ky"]))
        assertTrue("Only ${pilot.size} tagged words in the 12-lesson pilot", pilot.size >= 300)
    }
}
