package com.teameow.teawords

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** Switch only our launcher entry; never disable the activity that owns the running UI. */
internal object LauncherIconController {
    fun sync(context: Context, dynamicColor: Boolean) {
        val pm = context.packageManager
        val useMonet = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val entries = listOf(
            ComponentName(context.packageName, "com.teameow.teawords.MainActivity") to !useMonet,
            ComponentName(context.packageName, "com.teameow.teawords.MonetLauncher") to useMonet
        )
        val changes = entries.filter { (component, enabled) ->
            val state = pm.getComponentEnabledSetting(component)
            val isEnabled = when (state) {
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> component.className.endsWith(".MainActivity")
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                else -> false
            }
            isEnabled != enabled
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (changes.isNotEmpty()) pm.setComponentEnabledSettings(changes.map { (component, enabled) ->
                PackageManager.ComponentEnabledSetting(component, if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    else PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            })
        } else {
            // Enable the replacement first so there is always an available launcher entry.
            changes.sortedByDescending { it.second }.forEach { (component, enabled) ->
                pm.setComponentEnabledSetting(component, if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    else PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
        }
    }
}
