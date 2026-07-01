package com.github.cheremsha.decrypt.crypt.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.cheremsha.decrypt.crypt.app.ui.theme.Green
import com.github.cheremsha.decrypt.crypt.app.ui.theme.LocalAppColors
import com.github.cheremsha.decrypt.crypt.app.ui.theme.RedProto
import com.github.cheremsha.decrypt.crypt.app.util.AppLogger
import com.github.cheremsha.decrypt.crypt.app.util.LogLevel

@Composable
fun LogsScreen(onBack: () -> Unit) {
    val colors = LocalAppColors.current
    val logs by AppLogger.logs.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.cardBg)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null, tint = colors.textPrimary)
            }
            Text(
                "ЛОГИ",
                color = colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${logs.size}",
                color = colors.textDim,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = { AppLogger.clear() }) {
                Icon(Icons.Default.DeleteOutline, "Очистить", tint = colors.textSecondary)
            }
        }

        HorizontalDivider(color = colors.border)

        if (logs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Логов пока нет", color = colors.textDim, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                items(logs, key = { "${it.time}_${it.tag}_${it.message}" }) { entry ->
                    val msgColor = when (entry.level) {
                        LogLevel.ERROR   -> RedProto
                        LogLevel.SUCCESS -> Green
                        LogLevel.INFO    -> Color(0xFF888888)
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp)
                    ) {
                        Text(
                            entry.time,
                            color = Color(0xFF444444),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(52.dp)
                        )
                        Text(
                            entry.tag.take(10).padEnd(10),
                            color = Color(0xFF555555),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(70.dp)
                        )
                        Text(
                            entry.message,
                            color = msgColor,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}
