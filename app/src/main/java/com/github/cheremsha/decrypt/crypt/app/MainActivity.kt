package com.github.cheremsha.decrypt.crypt.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.cheremsha.decrypt.crypt.app.ui.HomeScreen
import com.github.cheremsha.decrypt.crypt.app.ui.MainViewModel
import com.github.cheremsha.decrypt.crypt.app.ui.SettingsScreen
import com.github.cheremsha.decrypt.crypt.app.ui.ThemeMode
import com.github.cheremsha.decrypt.crypt.app.ui.theme.AppTheme
import su.happ.proxyutility.util.protection.EncryptedSubUrlHelper

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        runCatching {
            val json = assets.open("keytable.json").bufferedReader().readText()
            EncryptedSubUrlHelper.init(json)
        }

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

            // Системная кнопка "назад" / жест-треугольник из настроек ведёт в меню,
            // а не закрывает приложение
            BackHandler(enabled = screen == "settings") {
                screen = "home"
            }

            AppTheme(darkTheme = darkTheme) {
                Crossfade(
                    targetState = screen,
                    animationSpec = tween(durationMillis = 280)
                ) { current ->
                    when (current) {
                        "settings" -> SettingsScreen(
                            vm, isDark = darkTheme,
                            onBack = { screen = "home" }
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
}
