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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import com.github.cheremsha.decrypt.crypt.app.crypto.ApiProvider
import com.github.cheremsha.decrypt.crypt.app.crypto.DecryptApi
import com.github.cheremsha.decrypt.crypt.app.crypto.HAPPY_DECODER_DEMO_KEY
import com.github.cheremsha.decrypt.crypt.app.crypto.HAPPY_DECODER_SITE
import com.github.cheremsha.decrypt.crypt.app.crypto.RING_BOT_LINK
import com.github.cheremsha.decrypt.crypt.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(isDark: Boolean, keyOnlyMode: Boolean = false, onFinished: () -> Unit, onCancel: () -> Unit = {}) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var page by remember { mutableIntStateOf(if (keyOnlyMode) 2 else 0) }
    var selectedProvider by remember { mutableStateOf<ApiProvider?>(ApiKeyManager.getProvider(context)) }
    var showInfoDialog by remember { mutableStateOf<ApiProvider?>(null) }

    var keyInput by remember { mutableStateOf("") }
    var useDemoKey by remember { mutableStateOf(false) }
    var validating by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(colors.bg)) {
        MatrixRain(
            modifier = Modifier.fillMaxSize().alpha(if (isDark) 0.5f else 0.5f),
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
                2 -> ProviderPage(
                    colors = colors,
                    selected = selectedProvider,
                    onSelect = { provider ->
                        selectedProvider = provider
                        keyInput = ""
                        useDemoKey = false
                        errorMsg = null
                        if (provider == ApiProvider.HAPPY_DECODER || provider == ApiProvider.RING_ENCRYPT) {
                            showInfoDialog = provider
                        }
                    },
                    onBack = { if (keyOnlyMode) onCancel() else page = 1 },
                    onNext = { if (selectedProvider != null) page = 3 }
                )
                else -> KeyPage(
                    colors = colors,
                    provider = selectedProvider,
                    keyInput = keyInput,
                    onKeyChange = { keyInput = it; errorMsg = null },
                    useDemoKey = useDemoKey,
                    onUseDemoKeyChange = { useDemoKey = it; errorMsg = null },
                    validating = validating,
                    errorMsg = errorMsg,
                    onCopyLink = { link ->
                        clipboard.setText(AnnotatedString(link))
                        Toast.makeText(context, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
                    },
                    onBack = { page = 2 },
                    onValidate = {
                        val provider = selectedProvider ?: return@KeyPage
                        val finalKey = when {
                            !provider.requiresKey -> null
                            provider == ApiProvider.HAPPY_DECODER && useDemoKey -> HAPPY_DECODER_DEMO_KEY
                            else -> keyInput.trim()
                        }
                        if (provider.requiresKey && finalKey.isNullOrBlank()) {
                            errorMsg = "Введите ключ"
                        } else {
                            validating = true
                            scope.launch {
                                val ok = DecryptApi.validate(provider, finalKey)
                                validating = false
                                if (ok) {
                                    ApiKeyManager.setProvider(context, provider)
                                    if (finalKey != null) ApiKeyManager.setKey(context, provider, finalKey)
                                    page = 4
                                } else {
                                    errorMsg = "Не удалось подтвердить (ключ или сервис недоступен)"
                                }
                            }
                        }
                    }
                )
            }

            if (p == 4) SuccessPage(colors)
        }

        showInfoDialog?.let { provider ->
            ProviderInfoDialog(
                provider = provider,
                onCopy = { link ->
                    clipboard.setText(AnnotatedString(link))
                    Toast.makeText(context, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showInfoDialog = null }
            )
        }
    }

    LaunchedEffect(page) {
        if (page == 4) {
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
private fun NavArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(if (enabled) CyanDim else Color(0xFF1A1A1A))
    ) {
        Icon(icon, null, tint = if (enabled) Cyan else Color(0xFF444444))
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
            Text("ДОБРО ПОЖАЛОВАТЬ В", color = colors.textSecondary, fontSize = 13.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
            Spacer(Modifier.height(6.dp))
            Text("CHEREMSHA DECRYPT", color = Cyan, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp, textAlign = TextAlign.Center)
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(24.dp)) { NavArrow(Icons.Default.ArrowForward, onClick = onNext) }
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
            Text("Расшифровывайте подписки", color = colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text("happ://crypt", color = Purple, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
        Box(Modifier.align(Alignment.BottomStart).padding(24.dp)) { NavArrow(Icons.Default.ArrowBack, onClick = onBack) }
        Box(Modifier.align(Alignment.BottomEnd).padding(24.dp)) { NavArrow(Icons.Default.ArrowForward, onClick = onNext) }
    }
}

@Composable
private fun ProviderPage(
    colors: AppColors,
    selected: ApiProvider?,
    onSelect: (ApiProvider) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Выберите оператора ключа", color = colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))

            ApiProvider.entries.forEach { provider ->
                val isSelected = provider == selected
                Row(
                    Modifier
                        .fillMaxWidth(0.85f)
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) Cyan.copy(alpha = 0.12f) else colors.cardBg)
                        .clickable { onSelect(provider) }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onSelect(provider) },
                        colors = RadioButtonDefaults.colors(selectedColor = Cyan, unselectedColor = colors.textDim)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(provider.label, color = if (isSelected) Cyan else colors.textPrimary, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
        Box(Modifier.align(Alignment.BottomStart).padding(24.dp)) { NavArrow(Icons.Default.ArrowBack, onClick = onBack) }
        Box(Modifier.align(Alignment.BottomEnd).padding(24.dp)) { NavArrow(Icons.Default.ArrowForward, enabled = selected != null, onClick = onNext) }
    }
}

@Composable
private fun ProviderInfoDialog(provider: ApiProvider, onCopy: (String) -> Unit, onDismiss: () -> Unit) {
    val (title, link) = when (provider) {
        ApiProvider.HAPPY_DECODER -> "Для работы с этим API перейдите на сайт и вставьте ссылку в указанное поле" to HAPPY_DECODER_SITE
        ApiProvider.RING_ENCRYPT -> "Для использования этого API сгенерируйте ключ в боте" to RING_BOT_LINK
        ApiProvider.KFWL_LOL -> return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF181818),
        title = { Text(title, color = Color.White, fontSize = 15.sp) },
        text = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0E0E0E))
                    .clickable { onCopy(link) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ContentCopy, null, tint = Cyan, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text(link, color = Cyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("ОК", color = Cyan) }
        }
    )
}

@Composable
private fun KeyPage(
    colors: AppColors,
    provider: ApiProvider?,
    keyInput: String,
    onKeyChange: (String) -> Unit,
    useDemoKey: Boolean,
    onUseDemoKeyChange: (Boolean) -> Unit,
    validating: Boolean,
    errorMsg: String?,
    onCopyLink: (String) -> Unit,
    onBack: () -> Unit,
    onValidate: () -> Unit
) {
    if (provider == null) return

    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (!provider.requiresKey) {
                Text("${provider.label} не требует ключа", color = colors.textPrimary, fontSize = 15.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text("Нажмите «Продолжить», чтобы проверить доступность сервиса", color = colors.textSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
            } else {
                Text("Вставьте ключ для", color = colors.textPrimary, fontSize = 15.sp, textAlign = TextAlign.Center)
                Text(provider.label, color = Cyan, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(16.dp))

                if (provider == ApiProvider.HAPPY_DECODER) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.cardBg)
                            .clickable { onCopyLink(HAPPY_DECODER_SITE) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ContentCopy, null, tint = Cyan, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("happy-decoder.cc/api", color = Cyan, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(0.9f)) {
                        Checkbox(
                            checked = useDemoKey,
                            onCheckedChange = onUseDemoKeyChange,
                            colors = CheckboxDefaults.colors(checkedColor = Orange, uncheckedColor = colors.textDim)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Использовать демо-ключ", color = if (useDemoKey) Orange else colors.textSecondary, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                } else if (provider == ApiProvider.RING_ENCRYPT) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.cardBg)
                            .clickable { onCopyLink(RING_BOT_LINK) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ContentCopy, null, tint = Cyan, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("t.me/Ring_encrypt_bot", color = Cyan, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(Modifier.height(14.dp))
                }

                OutlinedTextField(
                    value = if (provider == ApiProvider.HAPPY_DECODER && useDemoKey) HAPPY_DECODER_DEMO_KEY else keyInput,
                    onValueChange = onKeyChange,
                    enabled = !(provider == ApiProvider.HAPPY_DECODER && useDemoKey),
                    placeholder = { Text("hd_... / ring_enc...", color = colors.textDim, fontFamily = FontFamily.Monospace) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    modifier = Modifier.fillMaxWidth(0.9f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Cyan.copy(alpha = 0.6f),
                        unfocusedBorderColor = colors.border,
                        disabledBorderColor = colors.border.copy(alpha = 0.5f),
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textSecondary,
                        disabledTextColor = colors.textDim,
                        cursorColor = Cyan
                    )
                )
            }

            errorMsg?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = RedProto, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }
        Box(Modifier.align(Alignment.BottomStart).padding(24.dp)) { NavArrow(Icons.Default.ArrowBack, onClick = onBack) }
        Box(Modifier.align(Alignment.BottomEnd).padding(24.dp)) {
            if (validating) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(CyanDim), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Cyan, strokeWidth = 2.dp)
                }
            } else {
                NavArrow(Icons.Default.ArrowForward, onClick = onValidate)
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
                Text("Успешно!", color = Green, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
