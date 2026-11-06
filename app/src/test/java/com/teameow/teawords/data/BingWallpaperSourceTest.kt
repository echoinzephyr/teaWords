package com.teameow.teawords.data

import org.junit.Assert.*
import org.junit.Test

class BingWallpaperSourceTest {
    @Test fun archiveUsesOriginalInsteadOfDefault1080pLandscape() {
        assertEquals("https://www.bing.com/th?id=OHR.Example_EN-UHD_UHD.jpg",
            BingWallpaperSource.imageUrl("""{"images":[{"urlbase":"/th?id=OHR.Example_EN-UHD","url":"/th?id=OHR.Example_EN-UHD_1920x1080.jpg&rf=example"}]}"""))
    }
    @Test fun missingOriginalKeepsArchiveImageAndEmptyArchiveHasNoImage() {
        assertEquals("https://www.bing.com/th?id=fallback.jpg", BingWallpaperSource.imageUrl("""{"images":[{"url":"/th?id=fallback.jpg"}]}"""))
        assertNull(BingWallpaperSource.imageUrl("""{"images":[]}"""))
        assertNull(BingWallpaperSource.imageUrl("""{"images":[{"url":"https://unrelated.example/image"}]}"""))
    }
}
