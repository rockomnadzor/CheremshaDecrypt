package com.github.cheremsha.decrypt.crypt.app.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.random.Random

// В основном цифры — как на классическом "цифровом дожде"
private const val CHARS = "0123456789013456789234567890"
private const val CELL = 22f
private const val FRAME_MS = 80L

@Composable
fun MatrixRain(modifier: Modifier = Modifier, isDark: Boolean = true) {
    val config  = LocalConfiguration.current
    val density = LocalDensity.current
    val w = with(density) { config.screenWidthDp.dp.toPx() }
    val h = with(density) { config.screenHeightDp.dp.toPx() }

    val cols = remember(w) { (w / CELL).toInt().coerceAtLeast(1) }
    val ys     = remember(cols) { FloatArray(cols) { -Random.nextInt(2000).toFloat() } }
    val speeds = remember(cols) { FloatArray(cols) { 0.6f + Random.nextFloat() * 1.6f } }
    val lens   = remember(cols) { IntArray(cols) { 6 + Random.nextInt(14) } }
    val chars  = remember(cols) { Array(cols) { CharArray(32) { CHARS.random() } } }
    val active = remember(cols) { BooleanArray(cols) { Random.nextFloat() < 0.65f } }

    var tick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(cols, h) {
        while (isActive) {
            delay(FRAME_MS)
            for (c in 0 until cols) {
                if (!active[c]) {
                    if (Random.nextFloat() < 0.002f) active[c] = true
                    continue
                }
                ys[c] += speeds[c] * CELL * 0.30f
                if (ys[c] > h + lens[c] * CELL) {
                    ys[c] = -Random.nextInt(300).toFloat()
                    if (Random.nextFloat() < 0.3f) active[c] = false
                }
                if (Random.nextFloat() < 0.10f) {
                    chars[c][Random.nextInt(32)] = CHARS.random()
                }
            }
            tick++
        }
    }

    // === Цвета для тёмной темы (классический Matrix) ===
    val headColor: Color
    val nearColor: Color
    val tailColor: Color
    val bgColor: Color
    val glowAlpha: Float

    if (isDark) {
        headColor = Color(0xFFEFFFEF)   // Почти белая "голова"
        nearColor = Color(0xFF5CFF7A)   // Ярко-зелёная зона
        tailColor = Color(0xFF12B34A)   // Тёмно-зелёный хвост
        bgColor   = Color.Black
        glowAlpha = 0.35f
    } else {
        // === Цвета для светлой темы (инвертированные, но сохраняющие эффект) ===
        headColor = Color(0xFF002200)   // Почти чёрная "голова"
        nearColor = Color(0xFF008822)   // Насыщенный зелёный
        tailColor = Color(0xFF44BB66)   // Светло-зелёный хвост
        bgColor   = Color(0xFFF5F5F5)   // Светло-серый фон
        glowAlpha = 0.25f
    }

    val paint = remember {
        Paint().apply {
            isAntiAlias = true
            typeface = Typeface.MONOSPACE
            textSize = with(density) { 16.dp.toPx() }
        }
    }
    val glowPaint = remember {
        Paint().apply {
            isAntiAlias = true
            typeface = Typeface.MONOSPACE
            textSize = with(density) { 16.dp.toPx() }
        }
    }

    Canvas(modifier) {
        // Заливаем фон
        drawRect(color = bgColor, size = size)

        @Suppress("UNUSED_EXPRESSION") tick

        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            for (col in 0 until cols) {
                if (!active[col]) continue
                val x = col * CELL
                val y = ys[col]
                val len = lens[col]
                val colChars = chars[col]

                for (i in 0 until len) {
                    val cy = y - i * CELL
                    if (cy < -CELL || cy > size.height) continue

                    val idx = ((y / CELL).toInt().coerceAtLeast(0) + i) % colChars.size
                    val ch = colChars[idx].toString()

                    val color = when {
                        i == 0 -> headColor
                        i <= 2 -> nearColor
                        else -> tailColor.copy(
                            alpha = maxOf(0.08f, (1f - i.toFloat() / len) * 0.9f)
                        )
                    }

                    // Псевдо-свечение: мягкий подслой чуть большего размера и ниже альфы под основным символом
                    if (i <= 2) {
                        glowPaint.color = color.copy(alpha = color.alpha * glowAlpha).toArgb()
                        nc.drawText(ch, x - 1.5f, cy + 1.5f, glowPaint)
                        nc.drawText(ch, x + 1.5f, cy - 1.5f, glowPaint)
                    }

                    paint.color = color.toArgb()
                    paint.alpha = (color.alpha * 255).toInt()
                    nc.drawText(ch, x, cy, paint)
                }
            }
        }
    }
}
