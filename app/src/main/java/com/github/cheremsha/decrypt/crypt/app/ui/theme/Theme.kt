package com.github.cheremsha.decrypt.crypt.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Cyan      = Color(0xFF00E5FF)
val CyanDim   = Color(0xFF003A40)
val Purple    = Color(0xFFAA00FF)
val Green     = Color(0xFF00E676)
val Orange    = Color(0xFFFF9800)
val RedProto  = Color(0xFFF44336)
val Card      = Color(0xFF0C0C0C)
val Surface   = Color(0xFF080808)

fun protoColor(p: String) = when(p) {
    "VLESS"  -> Cyan
    "VMESS"  -> Orange
    "TROJAN" -> RedProto
    "SS"     -> Green
    "SSR"    -> Color(0xFF9C27B0)
    else     -> Color(0xFF555555)
}

@Composable
fun AppTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(
        primary          = Cyan,
        onPrimary        = Color.Black,
        primaryContainer = CyanDim,
        secondary        = Purple,
        background       = Color.Black,
        surface          = Surface,
        surfaceVariant   = Card,
        onBackground     = Color.White,
        onSurface        = Color.White,
        onSurfaceVariant = Color(0xFF999999),
        error            = RedProto,
    ),
    content = content
)
