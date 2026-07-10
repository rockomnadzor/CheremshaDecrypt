package com.github.cheremsha.decrypt.crypt.app.ui

import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.view.View
import android.view.View.LAYER_TYPE_HARDWARE
import android.view.View.LAYER_TYPE_SOFTWARE
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.random.Random

// В основном цифры — как на классическом "цифровом дожде"
private const val CHARS = "0123456789013456789234567890"
private const val CELL = 22f
private const val FRAME_MS = 40L  // Было 80 — ускорили для плавности

@Composable
fun MatrixRain(modifier: Modifier = Modifier, isDark: Boolean = true) {
    val config  = LocalConfiguration.current
    val density = LocalDensity.current
    val view = LocalView.current
    val w = with(density) { config.screenWidthDp.dp.toPx() }
    val h = with(density) { config.screenHeightDp.dp.toPx() }

    // Включаем software layer для размытия (glow эффект)
    DisposableEffect(Unit) {
        val originalLayerType = view.layerType
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            view.setLayerType(LAYER_TYPE_SOFTWARE, null)
        }
        onDispose {
            view.setLayerType(originalLayerType, null)
        }
    }

    val cols = remember(w) { (w / CELL).toInt().coerceAtLeast(1) }
    
    // Фоновые колонки (тусклые, статичные или медленные)
    val bgCols = remember(cols) { (cols * 1.5f).toInt() }
    val bgYs = remember(bgCols) { FloatArray(bgCols) { Random.nextFloat() * h } }
    val bgChars = remember(bgCols) { Array(bgCols) { CharArray(64) { CHARS.random() } } }
    val bgActive = remember(bgCols) { BooleanArray(bgCols) { Random.nextFloat() < 0.8f } }
    
    // Основные яркие колонки
    val ys     = remember(cols) { FloatArray(cols) { -Random.nextInt(2000).toFloat() } }
    val speeds = remember(cols) { FloatArray(cols) { 0.4f + Random.nextFloat() * 1.2f } }
    val lens   = remember(cols) { IntArray(cols) { 12 + Random.nextInt(20) } }  // Было 6-20, стало 12-32 — длиннее хвост
    val chars  = remember(cols) { Array(cols) { CharArray(64) { CHARS.random() } } }
    val active = remember(cols) { BooleanArray(cols) { Random.nextFloat() < 0.65f } }

    var tick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(cols, h) {
        while (isActive) {
            delay(FRAME_MS)
            
            // Обновляем фоновые колонки (очень медленно)
            for (c in 0 until bgCols) {
                if (!bgActive[c]) continue
                bgYs[c] += 0.1f
                if (bgYs[c] > h + CELL * 10) bgYs[c] = -CELL * 10
                if (Random.nextFloat() < 0.02f) {
                    bgChars[c][Random.nextInt(64)] = CHARS.random()
                }
            }
            
            // Обновляем основные колонки
            for (c in 0 until cols) {
                if (!active[c]) {
                    if (Random.nextFloat() < 0.002f) active[c] = true
                    continue
                }
                ys[c] += speeds[c] * CELL * 0.18f  // Было 0.30 — замедлили для плавности
                if (ys[c] > h + lens[c] * CELL) {
                    ys[c] = -Random.nextInt(300).toFloat()
                    if (Random.nextFloat() < 0.3f) active[c] = false
                }
                if (Random.nextFloat() < 0.08f) {  // Было 0.10 — реже меняем символы
                    chars[c][Random.nextInt(64)] = CHARS.random()
                }
            }
            tick++
        }
    }

    // === Цвета для тёмной темы (классический Matrix) ===
    val headColor: Color
    val nearColor: Color
    val midColor: Color
    val tailColor: Color
    val bgColor: Color
    val bgSymbolColor: Color

    if (isDark) {
        headColor = Color(0xFFFFFFFF)       // Белая голова
        nearColor = Color(0xFFAAFFAA)       // Ярко-зелёный
        midColor  = Color(0xFF44DD44)       // Средний зелёный
        tailColor = Color(0xFF008800)       // Тёмно-зелёный хвост
        bgColor   = Color(0xFF000500)       // Очень тёмный зелёно-чёрный фон
        bgSymbolColor = Color(0xFF002200)   // Почти невидимые фоновые символы
    } else {
        // === Цвета для светлой темы ===
        headColor = Color(0xFF002200)
        nearColor = Color(0xFF116611)
        midColor  = Color(0xFF339933)
        tailColor = Color(0xFF88CC88)
        bgColor   = Color(0xFFF5FAF5)
        bgSymbolColor = Color(0xFFDDEEDD)
    }

    val paint = remember {
        Paint().apply {
            isAntiAlias = true
            typeface = Typeface.MONOSPACE
            textSize = with(density) { 16.dp.toPx() }
        }
    }
    
    // Paint для свечения (blur)
    val glowPaint = remember {
        Paint().apply {
            isAntiAlias = true
            typeface = Typeface.MONOSPACE
            textSize = with(density) { 16.dp.toPx() }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                // Размытие для glow-эффекта
            }
        }
    }
    
    // Paint для фоновых символов
    val bgPaint = remember {
        Paint().apply {
            isAntiAlias = true
            typeface = Typeface.MONOSPACE
            textSize = with(density) { 14.dp.toPx() }
        }
    }

    Canvas(modifier) {
        // Заливаем фон
        drawRect(color = bgColor, size = size)

        @Suppress("UNUSED_EXPRESSION") tick

        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            
            // === Рисуем фоновые тусклые символы (глубина) ===
            for (col in 0 until bgCols) {
                if (!bgActive[col]) continue
                val x = (col * CELL * 0.7f) % size.width
                val baseY = bgYs[col]
                
                for (i in 0..40) {
                    val cy = baseY - i * CELL
                    if (cy < -CELL || cy > size.height) continue
                    
                    val idx = (i + col) % bgChars[col].size
                    val ch = bgChars[col][idx].toString()
                    
                    // Фоновые символы очень тусклые
                    val alpha = 0.03f + Random.nextFloat() * 0.04f
                    bgPaint.color = bgSymbolColor.copy(alpha = alpha).toArgb()
                    nc.drawText(ch, x, cy, bgPaint)
                }
            }
            
            // === Рисуем основные яркие колонки ===
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

                    // Плавный градиент с бОльшим количеством ступеней
                    val color = when {
                        i == 0 -> headColor
                        i == 1 -> nearColor
                        i <= 4 -> midColor.copy(alpha = 0.9f - (i - 2) * 0.15f)
                        else -> tailColor.copy(
                            alpha = maxOf(0.02f, (1f - i.toFloat() / len) * 0.7f)
                        )
                    }

                    // === Многослойное свечение (glow) ===
                    if (i <= 3) {
                        // Внешнее размытое свечение
                        val glowAlpha = when(i) {
                            0 -> 0.25f
                            1 -> 0.15f
                            else -> 0.08f
                        }
                        glowPaint.color = color.copy(alpha = glowAlpha).toArgb()
                        glowPaint.textSize = paint.textSize * 1.3f
                        nc.drawText(ch, x, cy, glowPaint)
                        glowPaint.textSize = paint.textSize
                        
                        // Среднее свечение
                        glowPaint.color = color.copy(alpha = glowAlpha * 0.6f).toArgb()
                        nc.drawText(ch, x - 2f, cy, glowPaint)
                        nc.drawText(ch, x + 2f, cy, glowPaint)
                    }

                    // Основной символ
                    paint.color = color.toArgb()
                    paint.alpha = (color.alpha * 255).toInt().coerceIn(0, 255)
                    nc.drawText(ch, x, cy, paint)
                }
            }
        }
    }
}
