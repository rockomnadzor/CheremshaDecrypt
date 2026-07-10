package com.github.cheremsha.decrypt.crypt.app.ui

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
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

    // Фоновая сетка
    val bgGrid = remember(cols, rows) {
        Array(cols) { Array(rows) { 
            Pair(CHARS.random(), Random.nextFloat()) 
        } }
    }
    val bgPhase = remember { mutableFloatStateOf(0f) }

    // Яркие streams
    val streams = remember(cols) { 
        List(cols) { StreamState(h, rows) } 
    }

    var tick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(cols, h, rows) {
        while (isActive) {
            delay(FRAME_MS)
            bgPhase.floatValue += 0.05f
            for (s in streams) {
                s.update(h, rows)
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
    val glowColor: Color

    if (isDark) {
        headColor = Color(0xFFFFFFFF)
        brightColor = Color(0xFFCCFFCC)
        midColor = Color(0xFF66DD66)
        tailColor = Color(0xFF228822)
        bgColor = Color(0xFF000800)
        bgSymbolColor = Color(0xFF0A1A0A)
        glowColor = Color(0xFF44FF44)
    } else {
        headColor = Color(0xFF001100)
        brightColor = Color(0xFF004400)
        midColor = Color(0xFF116611)
        tailColor = Color(0xFF88CC88)
        bgColor = Color(0xFFF0F5F0)
        bgSymbolColor = Color(0xFFE0E8E0)
        glowColor = Color(0xFF228822)
    }

    val bgPaint = remember {
        Paint().apply {
            isAntiAlias = true
            typeface = Typeface.MONOSPACE
            textSize = with(density) { 14.dp.toPx() }
            textAlign = Paint.Align.CENTER
        }
    }

    val glowPaint = remember {
        Paint().apply {
            isAntiAlias = true
            typeface = Typeface.MONOSPACE
            textSize = with(density) { 14.dp.toPx() }
            textAlign = Paint.Align.CENTER
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
            }
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
        val width = size.width
        val height = size.height

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
                    
                    val (color, alpha, useGlow) = when {
                        i == 0 -> Triple(headColor, 1.0f, true)
                        i == 1 -> Triple(brightColor, 0.95f, true)
                        i <= 3 -> Triple(midColor, 0.85f - (i - 2) * 0.15f, true)
                        i <= 6 -> Triple(tailColor, 0.6f - (i - 4) * 0.08f, false)
                        else -> Triple(tailColor, maxOf(0.03f, 0.4f - i * 0.04f), false)
                    }

                    val finalAlpha = (alpha * 255).toInt().coerceIn(0, 255)

                    if (useGlow && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        glowPaint.color = glowColor.copy(alpha = alpha * 0.3f).toArgb()
                        glowPaint.alpha = (alpha * 0.3f * 255).toInt()
                        nc.drawText(ch.toString(), x, cy, glowPaint)
                    }

                    paint.color = color.toArgb()
                    paint.alpha = finalAlpha
                    nc.drawText(ch.toString(), x, cy, paint)
                }
            }
        }
    }
}

private class StreamState(private val screenH: Float, private val rows: Int) {
    var active: Boolean = Random.nextFloat() < 0.6f
    var headRow: Float = -Random.nextInt(rows).toFloat()
    var length: Int = 8 + Random.nextInt(20)
    var speed: Float = 0.15f + Random.nextFloat() * 0.35f
    var chars: CharArray = CharArray(40) { CHARS.random() }
    private var changeTimer: Int = 0

    fun update(screenH: Float, rows: Int) {
        if (!active) {
            if (Random.nextFloat() < 0.003f) {
                active = true
                headRow = -length.toFloat()
            }
            return
        }

        headRow += speed

        if (headRow > rows + length) {
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
