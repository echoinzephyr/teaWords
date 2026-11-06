package com.teameow.teawords

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.filters.SdkSuppress
import com.teameow.teawords.data.AppPreferences
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

@SdkSuppress(minSdkVersion = 31)
class LauncherMonetTest {
    @get:Rule val compose = createAndroidComposeRule<TeaWordsActivity>()

    private fun entry(): android.content.pm.ResolveInfo {
        val activity = compose.activity
        val entries = activity.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(activity.packageName), 0)
        assertEquals("Only one launcher icon must be enabled", 1, entries.size)
        assertEquals(TeaWordsActivity::class.java.name, entries.single().activityInfo.targetActivity)
        return entries.single()
    }

    @Test fun appDynamicSettingChangesOnlyOurLauncherAndCanBeTurnedOffWithoutRestarting() {
        val activity = compose.activity
        val prefs = AppPreferences(activity)
        val previous = prefs.dynamicColor
        val previousSeed = prefs.themeSeedColor
        val pid = android.os.Process.myPid()
        try {
            compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription("设置").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("设置").performClick()
            compose.onNodeWithText("外观模式").performClick()
            compose.onNodeWithContentDescription("壁纸取色").performScrollTo().performClick()
            compose.runOnIdle {
                assertTrue(prefs.dynamicColor)
                val info = entry()
                assertTrue(info.activityInfo.name.endsWith(".MonetLauncher"))
                val icon = info.loadIcon(activity.packageManager)
                val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                icon.setBounds(0, 0, 512, 512); icon.draw(Canvas(bitmap))
                val folder = File(activity.getExternalFilesDir(null), "icon-monet").apply { mkdirs() }
                File(folder, "launcher-monet.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                val pixels = IntArray(512 * 512)
                bitmap.getPixels(pixels, 0, 512, 0, 0, 512, 512)
                assertTrue(pixels.any { it == activity.getColor(R.color.launcher_monet_primary) })
                assertTrue(pixels.any { it == activity.getColor(R.color.launcher_monet_background) })
            }
            compose.onNodeWithContentDescription("海蓝").performClick()
            compose.runOnIdle {
                assertFalse(prefs.dynamicColor)
                assertTrue(entry().activityInfo.name.endsWith(".MainActivity"))
                assertEquals(pid, android.os.Process.myPid())
                assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                    activity.packageManager.getComponentEnabledSetting(android.content.ComponentName(activity, TeaWordsActivity::class.java)))
            }
            compose.onNodeWithText("主题").assertIsDisplayed()
        } finally {
            prefs.themeSeedColor = previousSeed
            prefs.dynamicColor = previous
            LauncherIconController.sync(activity, previous)
        }
    }
}
