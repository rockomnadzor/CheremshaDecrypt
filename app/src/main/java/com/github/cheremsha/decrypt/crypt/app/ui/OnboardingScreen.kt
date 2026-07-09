package com.github.cheremsha.decrypt.crypt.app.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.cheremsha.decrypt.crypt.app.R
import com.github.cheremsha.decrypt.crypt.app.crypto.ApiKeyManager
import com.github.cheremsha.decrypt.crypt.app.crypto.HappyDecoderApi
import com.github.cheremsha.decrypt.crypt.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val KEY_GEN_URL = "https://happy-decoder.cc/api"

@Composable
fun OnboardingScreen(isDark: Boolean, onFinished: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var page by remember { mutableIntStateOf(0) }
    var keyInput by remember { mutableStateOf("") }
    var validating by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(colors.bg)) {
        MatrixRain(
            modifier = Modifier.fillMaxSize().alpha(if (isDark) 0.35f else 0.45f),
            isDark = isDark
        )

        Crossfade(
            targetState = page,
            animationSpec = tween(durationMillis = 320),
            modifier = Modifier.fillMaxSize()
        ) { p ->
            when (p) {
                0 -> WelcomePage(colors, onNext = { page = 1 })
                1 -> FeaturePage(colors, onBack = { page = 0 }, onNext = { page = 2 })
                2 -> KeyPage(
                    colors = colors,
                    keyInput = keyInput,
                    onKeyChange = { keyInput = it; errorMsg = null },
                    validating = validating,
                    errorMsg = errorMsg,
                    onCopyLink = {
                        clipboard.setText(AnnotatedString(KEY_GEN_URL))
                        Toast.makeText(context, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
                    },
                    onBack = { page = 1 },
                    onValidate = {
                        val trimmed = keyInput.trim()
                        if (trimmed.isBlank()) {
                            errorMsg = "Введите ключ"
                        } else {
                            validating = true
                            scope.launch {
                                val ok = HappyDecoderApi.validateKey(trimmed)
                                validating = false
                                if (ok) {
                                    ApiKeyManager.setKey(context, trimmed)
                                    page = 3
                                } else {
                                    errorMsg = "Неверный или неактивный ключ"
                                }
                            }
                        }
                    }
                )
                else -> SuccessPage(colors)
            }
        }
    }

    LaunchedEffect(page) {
        if (page == 3) {
            delay(1600)
            onFinished()
        }
    }
}

@Composable
private fun AppIcon(size: androidx.compose.ui.unit.Dp = 96.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .border(1.dp, Cyan.copy(alpha = 0.4f), CircleShape)
            .padding(4.dp)
            .clip(CircleShape)
    ) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun NavArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(CyanDim)
    ) {
        Icon(icon, null, tint = Cyan)
    }
}

@Composable
private fun WelcomePage(colors: AppColors, onNext: () -> Unit) {
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AppIcon(120.dp)
            Spacer(Modifier.height(28.dp))
            Text(
                "ДОБРО ПОЖАЛОВАТЬ В",
                color = colors.textSecondary, fontSize = 13.sp,
                fontFamily = FontFamily.Monospace, letterSpacing = 2.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "CHEREMSHA DECRYPT",
                color = Cyan, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace, letterSpacing = 2.sp, textAlign = TextAlign.Center
            )
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(24.dp)) {
            NavArrow(Icons.Default.ArrowForward, onNext)
        }
    }
}

@Composable
private fun FeaturePage(colors: AppColors, onBack: () -> Unit, onNext: () -> Unit) {
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AppIcon(120.dp)
            Spacer(Modifier.height(28.dp))
            Text(
                "Расшифровывайте подписки",
                color = colors.textPrimary, fontSize = 17.sp,
                fontWeight = FontWeight.Medium, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "happ://crypt",
                color = Purple, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Box(Modifier.align(Alignment.BottomStart).padding(24.dp)) {
            NavArrow(Icons.Default.ArrowBack, onBack)
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(24.dp)) {
            NavArrow(Icons.Default.ArrowForward, onNext)
        }
    }
}

@Composable
private fun KeyPage(
    colors: AppColors,
    keyInput: String,
    onKeyChange: (String) -> Unit,
    validating: Boolean,
    errorMsg: String?,
    onCopyLink: () -> Unit,
    onBack: () -> Unit,
    onValidate: () -> Unit
) {
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Для начала работы",
                color = colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "сгенерируйте свой ключ на сайте",
                color = colors.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.cardBg)
                    .clickable(onClick = onCopyLink)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ContentCopy, null, tint = Cyan, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "happy-decoder.cc/api",
                    color = Cyan, fontSize = 13.sp, fontFamily = FontFamily.Monospace
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "затем вставьте полученный ключ сюда",
                color = colors.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = keyInput,
                onValueChange = onKeyChange,
                placeholder = { Text("hd_...", color = colors.textDim, fontFamily = FontFamily.Monospace) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                modifier = Modifier.fillMaxWidth(0.9f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Cyan.copy(alpha = 0.6f),
                    unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textSecondary,
                    cursorColor = Cyan
                )
            )
            errorMsg?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = RedProto, fontSize = 12.sp)
            }
        }
        Box(Modifier.align(Alignment.BottomStart).padding(24.dp)) {
            NavArrow(Icons.Default.ArrowBack, onBack)
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(24.dp)) {
            if (validating) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(CyanDim),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Cyan, strokeWidth = 2.dp)
                }
            } else {
                NavArrow(Icons.Default.ArrowForward, onValidate)
            }
        }
    }
}

@Composable
private fun SuccessPage(colors: AppColors) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(visible = visible, enter = fadeIn(tween(400))) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CheckCircle, null, tint = Green, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(16.dp))
                Text(
                    "Успешно!",
                    color = Green, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
