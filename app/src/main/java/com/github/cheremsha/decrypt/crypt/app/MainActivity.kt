package com.github.cheremsha.decrypt.crypt.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.cheremsha.decrypt.crypt.app.ui.HomeScreen
import com.github.cheremsha.decrypt.crypt.app.ui.MainViewModel
import com.github.cheremsha.decrypt.crypt.app.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Запросить MANAGE_EXTERNAL_STORAGE на Android 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
            }
        }

        setContent {
            AppTheme {
                val vm: MainViewModel = viewModel()
                HomeScreen(vm)
            }
        }
    }
}
