package com.github.cheremsha.decrypt.crypt.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.cheremsha.decrypt.crypt.app.ui.HomeScreen
import com.github.cheremsha.decrypt.crypt.app.ui.MainViewModel
import com.github.cheremsha.decrypt.crypt.app.ui.SettingsScreen
import com.github.cheremsha.decrypt.crypt.app.ui.ThemeMode
import com.github.cheremsha.decrypt.crypt.app.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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

            AppTheme(darkTheme = darkTheme) {
                when (screen) {
                    "settings" -> SettingsScreen(vm, onBack = { screen = "home" })
                    else       -> HomeScreen(vm, isDark = darkTheme, onSettings = { screen = "settings" })
                }
            }
        }
    }
}
