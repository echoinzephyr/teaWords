package com.teameow.teawords.data

import org.junit.Assert.*
import org.junit.Test

class CourseContentDeviceTest {
    @Test fun packagedJsonLoadsWithoutDictionaryAndHasNinetySixExercises() {
        CourseCatalog.validate(CourseCatalog.pack)
        assertEquals(12, CourseCatalog.all.size)
        assertEquals(96, CourseCatalog.all.sumOf { it.questions.size })
        assertEquals(listOf("listening", "reading", "speaking", "writing"), CourseCatalog.all.first().skills)
    }
}
