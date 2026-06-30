package com.github.cheremsha.decrypt.crypt.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.cheremsha.decrypt.crypt.app.ui.theme.*

private const val GITHUB_URL   = "https://github.com/rockomnadzor/CheremshaDecrypt"
private const val TELEGRAM_URL = "https://t.me/cheremshaprojects"

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val colors  = LocalAppColors.current
    val mode    by vm.themeMode.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null, tint = colors.textPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                "НАСТРОЙКИ", color = colors.textPrimary, fontSize = 18.sp,
                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "ТЕМА", color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace, letterSpacing = 2.sp
        )
        Spacer(Modifier.height(10.dp))

        ThemeOption("Светлая", Icons.Default.LightMode, ThemeMode.LIGHT, mode, vm, colors)
        Spacer(Modifier.height(8.dp))
        ThemeOption("Тёмная", Icons.Default.DarkMode, ThemeMode.DARK, mode, vm, colors)
        Spacer(Modifier.height(8.dp))
        ThemeOption("Авто (системная)", Icons.Default.BrightnessAuto, ThemeMode.AUTO, mode, vm, colors)

        Spacer(Modifier.weight(1f))

        HorizontalDivider(color = colors.border)
        Spacer(Modifier.height(14.dp))

        LinkRow("Исходный код", GITHUB_URL, Icons.Default.Code, Cyan, colors) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))
        }
        Spacer(Modifier.height(8.dp))
        LinkRow("ТГК", TELEGRAM_URL, Icons.Default.Send, Purple, colors) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_URL)))
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ThemeOption(
    label: String, icon: ImageVector, value: ThemeMode,
    current: ThemeMode, vm: MainViewModel, colors: AppColors
) {
    val selected = value == current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Cyan.copy(alpha = 0.1f) else colors.cardBg)
            .clickable { vm.setThemeMode(value) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (selected) Cyan else colors.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            label, color = if (selected) Cyan else colors.textPrimary, fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        RadioButton(
            selected = selected, onClick = { vm.setThemeMode(value) },
            colors = RadioButtonDefaults.colors(selectedColor = Cyan, unselectedColor = colors.textDim)
        )
    }
}

@Composable
private fun LinkRow(
    label: String, url: String, icon: ImageVector,
    accent: Color, colors: AppColors, onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.cardBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(url, color = colors.textDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        Icon(Icons.Default.OpenInNew, null, tint = colors.textDim, modifier = Modifier.size(15.dp))
    }
}
