package com.github.cheremsha.decrypt.crypt.app.ui

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.cheremsha.decrypt.crypt.app.parser.VpnConfig
import com.github.cheremsha.decrypt.crypt.app.ui.theme.*

@Composable
fun HomeScreen(vm: MainViewModel, isDark: Boolean, onSettings: () -> Unit) {
    val context        = LocalContext.current
    val clipboard       = LocalClipboardManager.current
    val colors          = LocalAppColors.current
    val state           by vm.state.collectAsState()
    val input           by vm.input.collectAsState()
    val configs         by vm.configs.collectAsState()
    val filtered        by vm.filtered.collectAsState()
    val filter          by vm.filter.collectAsState()
    val useStaticHwid    by vm.useStaticHwid.collectAsState()
    val customHwid      by vm.customHwid.collectAsState()
    val effectiveHwid   by vm.effectiveHwid.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(colors.bg)) {

        MatrixRain(
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (isDark) 0.42f else 0.50f),
            isDark = isDark
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            // ── Header ───────────────────────────────────────────
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "CHEREMSHA", color = Cyan, fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace,
                            letterSpacing = 3.sp
                        )
                        Text(
                            "DECRYPT", color = Purple, fontSize = 13.sp,
                            fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                            letterSpacing = 6.sp
                        )
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.cardBg)
                            .border(1.dp, colors.border, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text(
                            effectiveHwid.take(10) + "…",
                            color = if (useStaticHwid) Orange else Cyan,
                            fontSize = 10.sp, fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onSettings, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Settings, "Настройки", tint = colors.textSecondary)
                    }
                }
            }

            // ── HWID Card ─────────────────────────────────────────
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                    shape  = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = useStaticHwid,
                                onCheckedChange = vm::setUseStaticHwid,
                                colors = CheckboxDefaults.colors(
                                    checkedColor   = Orange,
                                    uncheckedColor = colors.textDim
                                ),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Статичный HWID",
                                color = if (useStaticHwid) Orange else colors.textSecondary,
                                fontSize = 13.sp, fontFamily = FontFamily.Monospace
                            )
                            if (useStaticHwid) {
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Orange.copy(alpha = 0.1f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        MainViewModel.STATIC_HWID,
                                        color = Orange, fontSize = 10.sp, fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        if (!useStaticHwid) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = customHwid,
                                onValueChange = vm::setCustomHwid,
                                label = { Text("HWID", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                textStyle = LocalTextStyle.current.copy(
                                    fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor   = Cyan.copy(alpha = 0.5f),
                                    unfocusedBorderColor = colors.border,
                                    focusedTextColor     = Cyan,
                                    unfocusedTextColor   = colors.textSecondary,
                                    focusedLabelColor    = Cyan,
                                    unfocusedLabelColor  = colors.textDim,
                                    cursorColor          = Cyan,
                                )
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Устройство: ${vm.deviceHwid}",
                                color = colors.textDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            // ── Input card ───────────────────────────────────────
            item {
                val isLoading = state is UiState.Working
                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.cardBg),
                    shape  = RoundedCornerShape(14.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "Подписка / happ:// / URL",
                            color = colors.textDim, fontSize = 11.sp,
                            fontWeight = FontWeight.Medium, letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = input,
                            onValueChange = vm::setInput,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp, max = 180.dp),
                            placeholder = {
                                Text(
                                    "happ://crypt1/... или https://...",
                                    color = colors.textDim, fontSize = 12.sp, fontFamily = FontFamily.Monospace
                                )
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor   = Cyan.copy(alpha = 0.6f),
                                unfocusedBorderColor = colors.border,
                                focusedTextColor     = colors.textPrimary,
                                unfocusedTextColor   = colors.textSecondary,
                                cursorColor          = Cyan,
                            ),
                            textStyle = LocalTextStyle.current.copy(
                                fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        )
                        Spacer(Modifier.height(8.dp))

                        val localClip = LocalClipboardManager.current
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val t = localClip.getText()?.text ?: ""
                                    if (t.isNotBlank()) vm.setInput(t)
                                },
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, colors.border),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.ContentPaste, null, Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Вставить", fontSize = 13.sp)
                            }
                            if (input.isNotBlank()) {
                                IconButton(
                                    onClick = { vm.setInput("") },
                                    modifier = Modifier.size(40.dp),
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = Color(0xFF150505),
                                        contentColor   = Color(0xFF5A1A1A)
                                    )
                                ) { Icon(Icons.Default.Clear, null, Modifier.size(16.dp)) }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        val canDecrypt = input.isNotBlank() && !isLoading
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (canDecrypt)
                                        Brush.horizontalGradient(listOf(Color(0xFF003A40), Color(0xFF1A0030)))
                                    else
                                        Brush.horizontalGradient(listOf(Color(0xFF111111), Color(0xFF111111)))
                                )
                                .clickable(enabled = canDecrypt) { vm.process() }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp), color = Cyan, strokeWidth = 2.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        (state as UiState.Working).step,
                                        color = Cyan, fontSize = 13.sp, fontFamily = FontFamily.Monospace
                                    )
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.LockOpen, null,
                                        tint = if (canDecrypt) Cyan else Color(0xFF2A2A2A),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "РАСШИФРОВАТЬ",
                                        color = if (canDecrypt) Cyan else Color(0xFF2A2A2A),
                                        fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace, letterSpacing = 2.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Status ───────────────────────────────────────────
            when (val s = state) {
                is UiState.Error -> item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xCC150505)),
                        shape  = RoundedCornerShape(10.dp)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ErrorOutline, null, tint = RedProto, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(s.msg, color = RedProto, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        }
                    }
                }
                is UiState.Success -> item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xCC001A05)),
                        shape  = RoundedCornerShape(10.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, tint = Green, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Найдено ${s.count} конфигов",
                                    color = Green, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                s.url.take(60) + if (s.url.length > 60) "…" else "",
                                color = Color(0xFF2A6A2A), fontSize = 10.sp, fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
                else -> Unit
            }

            // ── Results ──────────────────────────────────────────
            if (configs.isNotEmpty()) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(vm.protocols) { proto ->
                            FilterChip(
                                selected = filter == proto,
                                onClick  = { vm.setFilter(proto) },
                                label    = { Text(proto, fontSize = 12.sp, fontFamily = FontFamily.Monospace) },
                                colors   = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = protoColor(proto).copy(alpha = 0.15f),
                                    selectedLabelColor     = protoColor(proto),
                                    containerColor         = colors.cardBg,
                                    labelColor             = colors.textDim
                                ),
                                border   = FilterChipDefaults.filterChipBorder(
                                    enabled             = true,
                                    selected            = filter == proto,
                                    selectedBorderColor = protoColor(proto).copy(alpha = 0.4f),
                                    borderColor         = colors.border
                                )
                            )
                        }
                    }
                }

                items(filtered, key = { it.rawLink }) { config ->
                    ConfigCard(config, colors) {
                        clipboard.setText(AnnotatedString(config.rawLink))
                        Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                vm.copyAll()
                                Toast.makeText(context, "Все ${configs.size} конфигов скопированы", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors   = ButtonDefaults.buttonColors(containerColor = CyanDim, contentColor = Cyan),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.CopyAll, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Скопировать все", fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                vm.saveToFile { path ->
                                    Toast.makeText(context, "Сохранено:\n$path", Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            border   = BorderStroke(1.dp, colors.border),
                            colors   = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.SaveAlt, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Сохранить .txt", fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun ConfigCard(config: VpnConfig, colors: AppColors, onCopy: () -> Unit) {
    val color = protoColor(config.protocol)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = colors.cardBg),
        shape    = RoundedCornerShape(10.dp)
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(color.copy(alpha = 0.12f))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(config.protocol, color = color, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(config.remarks, color = colors.textPrimary, fontSize = 13.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(config.endpoint, color = colors.textDim, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(
                onClick = onCopy, modifier = Modifier.size(34.dp),
                colors  = IconButtonDefaults.iconButtonColors(contentColor = colors.textDim)
            ) { Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)) }
        }
    }
}
