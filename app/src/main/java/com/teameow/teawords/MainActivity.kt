package com.teameow.teawords

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.teameow.teawords.data.AppPreferences
import com.teameow.teawords.data.AppThemeMode
import com.teameow.teawords.data.DatabaseHelper
import com.teameow.teawords.data.DictionaryApi
import com.teameow.teawords.data.WordLevelProvider
import com.teameow.teawords.data.search.DictionaryGateway
import com.teameow.teawords.ui.screens.MainScreen
import com.teameow.teawords.ui.screens.ProvidePronunciation
import com.teameow.teawords.ui.theme.TeaWordsTheme
import com.teameow.teawords.ui.theme.resolveDarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

class MainActivity : ComponentActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var levelProvider: WordLevelProvider
    private lateinit var api: DictionaryApi
    private var initialized by mutableStateOf(false)
    private var initializationError by mutableStateOf<String?>(null)

    private fun initializeStorage() {
        initializationError = null
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    dbHelper.ensureSchema()
                    check(DictionaryGateway.open(applicationContext)) { "离线词典暂时无法读取" }
                }
                initialized = true
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { initializationError = e.message ?: "本地资料暂时无法读取" }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        dbHelper = DatabaseHelper(this)
        levelProvider = WordLevelProvider(this)
        api = DictionaryApi()
        val preferences = AppPreferences(this)

        // Render first; migrations and the compact index must not hold the first UI frame.
        // MainScreen is mounted only after both are ready, so readers cannot race schema setup.
        initializeStorage()

        setContent {
            // Read on every composition so choosing a mode in settings re-themes immediately.
            var mode by remember { mutableStateOf(preferences.themeMode) }
            var dynamicColor by remember { mutableStateOf(preferences.dynamicColor) }
            var pureBlack by remember { mutableStateOf(preferences.pureBlack) }
            var seedColor by remember { mutableIntStateOf(preferences.themeSeedColor) }
            var fontScale by remember { mutableStateOf(preferences.customFontScale) }
            var strategy by remember { mutableStateOf(preferences.studyStrategy) }
            var dailyCap by remember { mutableIntStateOf(preferences.dailyNewCapOverride) }
            var retention by remember { mutableStateOf(preferences.targetRetentionOverride) }
            TeaWordsTheme(
                darkTheme = resolveDarkTheme(mode, isSystemInDarkTheme()),
                dynamicColor = dynamicColor,
                pureBlack = pureBlack,
                seedColor = seedColor,
                customFontScale = fontScale
            ) {
              if (!initialized) {
                  Surface(Modifier.fillMaxSize()) {
                      Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
                          horizontalAlignment = Alignment.CenterHorizontally) {
                          if (initializationError == null) CircularProgressIndicator()
                          Text(initializationError ?: "正在打开本地资料", style = MaterialTheme.typography.bodyLarge)
                          if (initializationError != null) Button(onClick = ::initializeStorage) { Text("重试") }
                      }
                  }
              } else {
              ProvidePronunciation {
                MainScreen(
                    dbHelper = dbHelper,
                    levelProvider = levelProvider,
                    api = api,
                    appPreferences = preferences,
                    themeMode = mode,
                    onThemeModeChange = {
                        preferences.themeMode = it
                        mode = it
                    },
                    dynamicColor = dynamicColor,
                    pureBlack = pureBlack,
                    themeSeedColor = seedColor,
                    onThemeSeedColorChange = { preferences.themeSeedColor = it; seedColor = it },
                    customFontScale = fontScale,
                    onCustomFontScaleChange = { preferences.customFontScale = it; fontScale = preferences.customFontScale },
                    onPureBlackChange = { preferences.pureBlack = it; pureBlack = it },
                    onDynamicColorChange = {
                        preferences.dynamicColor = it
                        dynamicColor = it
                    },
                    studyStrategy = strategy,
                    onStudyStrategyChange = {
                        preferences.studyStrategy = it
                        strategy = it
                    },
                    dailyNewCapOverride = dailyCap,
                    onDailyNewCapChange = {
                        preferences.dailyNewCapOverride = it
                        dailyCap = it
                    },
                    targetRetentionOverride = retention,
                    onTargetRetentionChange = {
                        preferences.targetRetentionOverride = it
                        retention = it
                    }
                )
              }
              }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        dbHelper.close()
    }
}
