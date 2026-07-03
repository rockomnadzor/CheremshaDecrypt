package com.github.cheremsha.decrypt.crypt.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.cheremsha.decrypt.crypt.app.ui.HomeScreen
import com.github.cheremsha.decrypt.crypt.app.ui.LogsScreen
import com.github.cheremsha.decrypt.crypt.app.ui.MainViewModel
import com.github.cheremsha.decrypt.crypt.app.ui.SettingsScreen
import com.github.cheremsha.decrypt.crypt.app.ui.ThemeMode
import com.github.cheremsha.decrypt.crypt.app.ui.theme.AppTheme
import com.github.cheremsha.decrypt.crypt.app.util.AppLogger

class MainActivity : ComponentActivity() {

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* результат не критичен */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestStoragePermissions()

        AppLogger.init(this)
        AppLogger.log("INIT", "Приложение запущено")

        setContent {
            val vm: MainViewModel = viewModel()
            val mode by vm.themeMode.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (mode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK  -> true
                ThemeMode.AUTO  -> systemDark
            }
            var screen by remember { mutableStateOf("home") }

            BackHandler(enabled = screen != "home") {
                screen = when (screen) {
                    "logs"     -> "settings"
                    "settings" -> "home"
                    else       -> "home"
                }
            }

            AppTheme(darkTheme = darkTheme) {
                Crossfade(
                    targetState = screen,
                    animationSpec = tween(durationMillis = 280)
                ) { current ->
                    when (current) {
                        "logs" -> LogsScreen(onBack = { screen = "settings" })
                        "settings" -> SettingsScreen(
                            vm, isDark = darkTheme,
                            onBack = { screen = "home" },
                            onLogs = { screen = "logs" }
                        )
                        else -> HomeScreen(
                            vm, isDark = darkTheme,
                            onSettings = { screen = "settings" }
                        )
                    }
                }
            }
        }
    }

    private fun requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                runCatching {
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:$packageName")
                        }
                    )
                }
            }
        } else {
            permLauncher.launch(arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ))
        }
    }
}
