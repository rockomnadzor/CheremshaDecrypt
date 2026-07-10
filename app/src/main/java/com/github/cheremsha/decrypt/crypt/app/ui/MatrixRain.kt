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

private const val CHARS = "0123456789"
private const val CELL = 18f
private const val FRAME_MS = 50L

@Composable
fun MatrixRain(modifier: Modifier = Modifier, isDark: Boolean = true) {
    val config  = LocalConfiguration.current
    val density = LocalDensity.current
    val w = with(density) { config.screenWidthDp.dp.toPx() }
    val h = with(density) { config.screenHeightDp.dp.toPx() }

    val cols = remember(w) { (w / CELL).toInt().coerceAtLeast(1) }
    val rows = remember(h) { (h / CELL).toInt().coerceAtLeast(1) }

    // Защита от деления на ноль
    if (cols <= 0 || rows <= 0) return

    // Фоновая сетка
    val bgGrid = remember(cols, rows) {
        Array(cols) { Array(rows) { 
            Pair(CHARS.random(), Random.nextFloat()) 
        } }
    }
    val bgPhase = remember { mutableFloatStateOf(0f) }

    // Яркие streams
    val streams = remember(cols) { 
        List(cols) { StreamState(rows) } 
    }

    var tick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(cols, rows) {
        while (isActive) {
            delay(FRAME_MS)
            bgPhase.floatValue += 0.05f
            for (s in streams) {
                s.update(rows)
            }
            tick++
        }
    }

    // Цвета
    val headColor: Color
    val brightColor: Color
    val midColor: Color
    val tailColor: Color
    val bgColor: Color
    val bgSymbolColor: Color

    if (isDark) {
        headColor = Color(0xFFFFFFFF)
        brightColor = Color(0xFFCCFFCC)
        midColor = Color(0xFF66DD66)
        tailColor = Color(0xFF228822)
        bgColor = Color(0xFF000800)
        bgSymbolColor = Color(0xFF0A1A0A)
    } else {
        headColor = Color(0xFF001100)
        brightColor = Color(0xFF004400)
        midColor = Color(0xFF116611)
        tailColor = Color(0xFF88CC88)
        bgColor = Color(0xFFF0F5F0)
        bgSymbolColor = Color(0xFFE0E8E0)
    }

    val bgPaint = remember {
        Paint().apply {
            isAntiAlias = true
            typeface = Typeface.MONOSPACE
            textSize = with(density) { 14.dp.toPx() }
            textAlign = Paint.Align.CENTER
        }
    }

    val paint = remember {
        Paint().apply {
            isAntiAlias = true
            typeface = Typeface.MONOSPACE
            textSize = with(density) { 14.dp.toPx() }
            textAlign = Paint.Align.CENTER
        }
    }

    Canvas(modifier) {
        drawRect(color = bgColor, size = size)

        @Suppress("UNUSED_EXPRESSION") tick

        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas

            // Фоновая сетка
            for (cx in 0 until cols) {
                for (ry in 0 until rows) {
                    val (ch, phase) = bgGrid[cx][ry]
                    val flicker = 0.03f + 0.04f * kotlin.math.sin(bgPhase.floatValue + phase * 6.28f)
                    val alpha = (flicker * 255).toInt().coerceIn(5, 40)
                    
                    bgPaint.color = bgSymbolColor.copy(alpha = flicker).toArgb()
                    bgPaint.alpha = alpha
                    
                    val x = cx * CELL + CELL / 2
                    val y = ry * CELL + CELL * 0.75f
                    nc.drawText(ch.toString(), x, y, bgPaint)
                }
            }

            // Яркие streams
            for ((colIdx, stream) in streams.withIndex()) {
                if (!stream.active) continue
                
                val x = colIdx * CELL + CELL / 2
                
                for (i in 0 until stream.length) {
                    val rowIdx = ((stream.headRow - i) % rows + rows) % rows
                    val cy = rowIdx * CELL + CELL * 0.75f
                    val ch = stream.chars[i % stream.chars.size]
                    
                    val (color, alpha) = when {
                        i == 0 -> Pair(headColor, 1.0f)
                        i == 1 -> Pair(brightColor, 0.95f)
                        i <= 3 -> Pair(midColor, 0.85f - (i - 2) * 0.15f)
                        i <= 6 -> Pair(tailColor, 0.6f - (i - 4) * 0.08f)
                        else -> Pair(tailColor, maxOf(0.03f, 0.4f - i * 0.04f))
                    }

                    val finalAlpha = (alpha * 255).toInt().coerceIn(0, 255)

                    // Псевдо-свечение через мягкий подслой
                    if (i <= 2) {
                        paint.color = color.copy(alpha = alpha * 0.3f).toArgb()
                        paint.alpha = (alpha * 0.3f * 255).toInt()
                        paint.textSize = with(density) { 18.dp.toPx() }
                        nc.drawText(ch.toString(), x, cy, paint)
                        paint.textSize = with(density) { 14.dp.toPx() }
                    }

                    paint.color = color.toArgb()
                    paint.alpha = finalAlpha
                    nc.drawText(ch.toString(), x, cy, paint)
                }
            }
        }
    }
}

private class StreamState(private val rows: Int) {
    var active: Boolean = Random.nextFloat() < 0.6f
    var headRow: Float = -Random.nextInt(rows.coerceAtLeast(1)).toFloat()
    var length: Int = 8 + Random.nextInt(20)
    var speed: Float = 0.15f + Random.nextFloat() * 0.35f
    var chars: CharArray = CharArray(40) { CHARS.random() }
    private var changeTimer: Int = 0

    fun update(rows: Int) {
        val safeRows = rows.coerceAtLeast(1)
        
        if (!active) {
            if (Random.nextFloat() < 0.003f) {
                active = true
                headRow = -length.toFloat()
            }
            return
        }

        headRow += speed

        if (headRow > safeRows + length) {
            headRow = -length.toFloat()
            if (Random.nextFloat() < 0.25f) {
                active = false
                return
            }
            length = 8 + Random.nextInt(20)
            speed = 0.15f + Random.nextFloat() * 0.35f
        }

        changeTimer++
        if (changeTimer > 3) {
            changeTimer = 0
            if (Random.nextFloat() < 0.15f) {
                chars[Random.nextInt(chars.size)] = CHARS.random()
            }
        }
    }
}
