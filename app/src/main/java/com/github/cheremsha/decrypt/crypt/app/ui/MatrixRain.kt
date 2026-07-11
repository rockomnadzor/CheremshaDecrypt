package com.github.cheremsha.decrypt.crypt.app.ui

import android.graphics.BlurMaskFilter
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

private const val CHARS = "0123456789"
private const val CELL = 22f
private const val FRAME_MS = 100L
private const val DENSITY_FRACTION = 0.5f // доля активных колонок — меньше = быстрее

@Composable
fun MatrixRain(modifier: Modifier = Modifier, isDark: Boolean = true) {
    val config  = LocalConfiguration.current
    val density = LocalDensity.current
    val w = with(density) { config.screenWidthDp.dp.toPx() }
    val h = with(density) { config.screenHeightDp.dp.toPx() }

    val allCols = remember(w) { (w / CELL).toInt().coerceAtLeast(1) }
    // Активные колонки — заранее отобранное подмножество, не пересчитывается каждый кадр
    val activeCols = remember(allCols) {
        (0 until allCols).filter { Random.nextFloat() < DENSITY_FRACTION }
    }
    val n = activeCols.size

    val ys     = remember(n) { FloatArray(n) { -Random.nextInt(2000).toFloat() } }
    val speeds = remember(n) { FloatArray(n) { 0.8f + Random.nextFloat() * 1.8f } }
    val lens   = remember(n) { IntArray(n) { 6 + Random.nextInt(10) } }
    val chars  = remember(n) { Array(n) { CharArray(20) { CHARS.random() } } }

    var tick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(n, h) {
        while (isActive) {
            delay(FRAME_MS)
            for (i in 0 until n) {
                ys[i] += speeds[i] * CELL * 0.35f
                if (ys[i] > h + lens[i] * CELL) {
                    ys[i] = -Random.nextInt(300).toFloat()
                }
                if (Random.nextFloat() < 0.08f) {
                    chars[i][Random.nextInt(20)] = CHARS.random()
                }
            }
            tick++
        }
    }

    val headColor: Color
    val nearColor: Color
    val tailColor: Color
    val glowColor: Color
    if (isDark) {
        headColor = Color(0xFFF2FFF2)
        nearColor = Color(0xFF66FF80)
        tailColor = Color(0xFF14B34A)
        glowColor = Color(0xFF33FF66)
    } else {
        headColor = Color(0xFF00330F)
        nearColor = Color(0xFF0B7A2E)
        tailColor = Color(0xFF1FA34A)
        glowColor = Color(0xFF0B7A2E)
    }

    val textSizePx = with(density) { 17.dp.toPx() }

    val paint = remember {
        Paint().apply {
            isAntiAlias = true
            typeface = Typeface.MONOSPACE
            textSize = textSizePx
        }
    }
    // Отдельный Paint с настоящим blur — рисуется под основным символом только у "головы"
    val glowPaint = remember(textSizePx) {
        Paint().apply {
            isAntiAlias = true
            typeface = Typeface.MONOSPACE
            textSize = textSizePx
            maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
        }
    }

    Canvas(modifier) {
        @Suppress("UNUSED_EXPRESSION") tick

        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            for (i in 0 until n) {
                val x = activeCols[i] * CELL
                val y = ys[i]
                val len = lens[i]
                val colChars = chars[i]

                // Glow-пятно за "головой" капли — один вызов на колонку, не на каждый символ
                if (y in -CELL..(size.height + CELL)) {
                    glowPaint.color = glowColor.toArgb()
                    glowPaint.alpha = 140
                    nc.drawText(colChars[0].toString(), x, y, glowPaint)
                }

                for (j in 0 until len) {
                    val cy = y - j * CELL
                    if (cy < -CELL || cy > size.height) continue

                    val idx = j % colChars.size
                    val ch = colChars[idx].toString()

                    val color = when {
                        j == 0 -> headColor
                        j <= 2 -> nearColor
                        else -> tailColor.copy(
                            alpha = maxOf(0.10f, (1f - j.toFloat() / len) * 0.9f)
                        )
                    }

                    paint.color = color.toArgb()
                    paint.alpha = (color.alpha * 255).toInt()
                    nc.drawText(ch, x, cy, paint)
                }
            }
        }
    }
}
