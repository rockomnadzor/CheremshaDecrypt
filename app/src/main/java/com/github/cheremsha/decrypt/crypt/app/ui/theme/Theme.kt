package com.github.cheremsha.decrypt.crypt.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val Cyan      = Color(0xFF00E5FF)
val CyanDim   = Color(0xFF003A40)
val Purple    = Color(0xFFAA00FF)
val Green     = Color(0xFF00E676)
val Orange    = Color(0xFFFF9800)
val RedProto  = Color(0xFFF44336)

fun protoColor(p: String) = when (p) {
    "VLESS"  -> Cyan
    "VMESS"  -> Orange
    "TROJAN" -> RedProto
    "SS"     -> Green
    "SSR"    -> Color(0xFF9C27B0)
    else     -> Color(0xFF555555)
}

data class AppColors(
    val bg: Color,
    val cardBg: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDim: Color,
    val border: Color,
)

private val DarkAppColors = AppColors(
    bg            = Color(0xFF000000),
    cardBg        = Color(0xCC0A0A0A),
    textPrimary   = Color(0xFFE0E0E0),
    textSecondary = Color(0xFF888888),
    textDim       = Color(0xFF3A3A3A),
    border        = Color(0xFF1A1A1A),
)

private val LightAppColors = AppColors(
    bg            = Color(0xFFF2F2F5),
    cardBg        = Color(0xFFFFFFFF),
    textPrimary   = Color(0xFF111111),
    textSecondary = Color(0xFF555555),
    textDim       = Color(0xFF999999),
    border        = Color(0xFFDADADA),
)

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

@Composable
fun AppTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = Cyan, onPrimary = Color.Black, primaryContainer = CyanDim,
            secondary = Purple, background = Color.Black, surface = Color(0xFF080808),
            surfaceVariant = Color(0xFF0C0C0C), onBackground = Color.White, onSurface = Color.White,
            onSurfaceVariant = Color(0xFF999999), error = RedProto,
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF0097A7), onPrimary = Color.White, primaryContainer = Color(0xFFB2EBF2),
            secondary = Purple, background = Color(0xFFF2F2F5), surface = Color.White,
            surfaceVariant = Color(0xFFEDEDED), onBackground = Color(0xFF111111), onSurface = Color(0xFF111111),
            onSurfaceVariant = Color(0xFF555555), error = RedProto,
        )
    }

    CompositionLocalProvider(
        LocalAppColors provides if (darkTheme) DarkAppColors else LightAppColors
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
