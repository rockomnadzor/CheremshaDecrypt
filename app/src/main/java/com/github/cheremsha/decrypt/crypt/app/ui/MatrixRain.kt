package com.github.cheremsha.decrypt.crypt.app.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlin.random.Random

private const val CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789@#%&<>{}アイウエオカキクケコ"
private const val CELL = 16f
private const val FRAME_MS = 90L  // ~11 FPS — достаточно для эффекта, экономит CPU

@Composable
fun MatrixRain(modifier: Modifier = Modifier, isDark: Boolean = true) {
    val config  = LocalConfiguration.current
    val density = LocalDensity.current
    val w = with(density) { config.screenWidthDp.dp.toPx() }
    val h = with(density) { config.screenHeightDp.dp.toPx() }

    // Все данные капель — примитивные массивы, без аллокаций на тик
    val cols = remember(w) { (w / CELL).toInt().coerceAtLeast(1) }
    val ys     = remember(cols) { FloatArray(cols) { -Random.nextInt(2000).toFloat() } }
    val speeds = remember(cols) { FloatArray(cols) { 1.1f + Random.nextFloat() * 2.6f } }
    val lens   = remember(cols) { IntArray(cols) { 10 + Random.nextInt(22) } }
    val chars  = remember(cols) { Array(cols) { CharArray(32) { CHARS.random() } } }

    var tick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(cols, h) {
        while (isActive) {
            kotlinx.coroutines.delay(FRAME_MS)
            for (c in 0 until cols) {
                ys[c] += speeds[c] * CELL * 0.30f
                if (ys[c] > h + lens[c] * CELL) {
                    ys[c] = -Random.nextInt(200).toFloat()
                }
                if (Random.nextFloat() < 0.09f) {
                    chars[c][Random.nextInt(32)] = CHARS.random()
                }
            }
            tick++  // триггерит перерисовку Canvas без новых аллокаций
        }
    }

    val headColor = if (isDark) Color.White else Color(0xFF003B40)
    val nearColor = if (isDark) Color(0xFF00E5FF) else Color(0xFF00838F)
    val tailColor = if (isDark) Color(0xFF00E676) else Color(0xFF1B5E20)

    val paint = remember {
        Paint().apply {
            isAntiAlias = false  // пиксельный стиль уместен для матрицы + быстрее
            typeface = Typeface.MONOSPACE
            textSize = with(density) { 13.dp.toPx() }
        }
    }

    Canvas(modifier) {
        @Suppress("UNUSED_EXPRESSION") tick  // читаем state, чтобы триггерить redraw

        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            for (col in 0 until cols) {
                val x = col * CELL
                val y = ys[col]
                val len = lens[col]
                val colChars = chars[col]

                for (i in 0 until len) {
                    val cy = y - i * CELL
                    if (cy < -CELL || cy > size.height) continue

                    val idx = ((y / CELL).toInt().coerceAtLeast(0) + i) % colChars.size
                    val ch = colChars[idx]

                    val color = when {
                        i == 0 -> headColor
                        i <= 2 -> nearColor
                        else -> tailColor.copy(
                            alpha = maxOf(0.12f, (1f - i.toFloat() / len) * 0.95f)
                        )
                    }
                    paint.color = color.toArgb()
                    paint.alpha = (color.alpha * 255).toInt()
                    nc.drawText(ch.toString(), x, cy, paint)
                }
            }
        }
    }
}
