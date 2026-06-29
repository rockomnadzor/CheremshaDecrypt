package com.github.cheremsha.decrypt.crypt.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

private val CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789@#%&<>{}アイウエオカキクケコ".toList()
private const val CELL = 16f
private const val TICK = 110L

private data class DropSnap(
    val x: Float,
    val y: Float,
    val chars: List<Char>,
    val len: Int
)

@Composable
fun MatrixRain(modifier: Modifier = Modifier) {
    val config  = LocalConfiguration.current
    val density = LocalDensity.current
    val W = with(density) { config.screenWidthDp.dp.toPx() }
    val H = with(density) { config.screenHeightDp.dp.toPx() }

    class Drop(
        val x: Float,
        var y: Float,
        val speed: Float,
        val len: Int,
        val chars: MutableList<Char>
    )

    val drops = remember(W, H) {
        val cols = (W / CELL).toInt().coerceAtLeast(1)
        List(cols) { i ->
            Drop(
                x     = i * CELL,
                y     = -Random.nextInt(H.toInt().coerceAtLeast(1)).toFloat(),
                speed = 0.8f + Random.nextFloat() * 2f,
                len   = 8 + Random.nextInt(18),
                chars = MutableList(32) { CHARS.random() }
            )
        }
    }

    var snapshot by remember { mutableStateOf<List<DropSnap>>(emptyList()) }

    LaunchedEffect(drops) {
        while (true) {
            delay(TICK)
            drops.forEach { drop ->
                drop.y += drop.speed * CELL * 0.22f
                if (drop.y > H + drop.len * CELL) {
                    drop.y = -Random.nextInt(H.toInt().coerceAtLeast(200)).toFloat()
                }
                if (Random.nextFloat() < 0.07f) {
                    drop.chars[Random.nextInt(drop.chars.size)] = CHARS.random()
                }
            }
            snapshot = drops.map { DropSnap(it.x, it.y, it.chars.toList(), it.len) }
        }
    }

    val measurer = rememberTextMeasurer()
    val data     = snapshot   // state read — triggers recomposition

    Canvas(modifier) {
        data.forEach { drop ->
            for (i in 0 until drop.len) {
                val cy = drop.y - i * CELL
                if (cy < -CELL || cy > size.height) continue

                val idx  = ((drop.y / CELL).toInt().coerceAtLeast(0) + i) % drop.chars.size
                val char = drop.chars[idx].toString()

                val (color, fontSize) = when {
                    i == 0 -> Color.White                                       to 13.sp
                    i <= 2 -> Color(0xFF00E5FF).copy(alpha = 0.9f)              to 13.sp
                    else   -> Color(0xFF00C853).copy(
                        alpha = maxOf(0.04f, (1f - i.toFloat() / drop.len) * 0.7f)
                    ) to 12.sp
                }

                drawText(
                    textMeasurer = measurer,
                    text         = char,
                    topLeft      = Offset(drop.x, cy),
                    style        = TextStyle(
                        color      = color,
                        fontSize   = fontSize,
                        fontFamily = FontFamily.Monospace
                    )
                )
            }
        }
    }
}
