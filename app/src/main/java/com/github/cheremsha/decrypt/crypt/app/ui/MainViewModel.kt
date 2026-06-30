package com.github.cheremsha.decrypt.crypt.app.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.cheremsha.decrypt.crypt.app.crypto.HappDecryptor
import com.github.cheremsha.decrypt.crypt.app.network.SubFetcher
import com.github.cheremsha.decrypt.crypt.app.parser.VpnConfig
import com.github.cheremsha.decrypt.crypt.app.parser.VpnConfigParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class ThemeMode { LIGHT, DARK, AUTO }

sealed class UiState {
    object Idle                                         : UiState()
    data class Working(val step: String)                : UiState()
    data class Success(val url: String, val count: Int) : UiState()
    data class Error(val msg: String)                   : UiState()
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        const val STATIC_HWID = "a67d61b1c88dc678"
    }

    private val prefs = app.getSharedPreferences("cheremsha_prefs", 0)

    // ── Тема ──────────────────────────────────────────────
    private val _themeMode = MutableStateFlow(
        ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.DARK.name) ?: ThemeMode.DARK.name)
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    // ── HWID ──────────────────────────────────────────────
    val deviceHwid: String = Settings.Secure.getString(
        app.contentResolver, Settings.Secure.ANDROID_ID
    ) ?: STATIC_HWID

    private val _useStaticHwid = MutableStateFlow(true)
    val useStaticHwid: StateFlow<Boolean> = _useStaticHwid.asStateFlow()

    private val _customHwid = MutableStateFlow(deviceHwid)
    val customHwid: StateFlow<String> = _customHwid.asStateFlow()

    val effectiveHwid: StateFlow<String> = combine(_useStaticHwid, _customHwid) { useStatic, custom ->
        if (useStatic) STATIC_HWID else custom.ifBlank { deviceHwid }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), deviceHwid)

    fun setUseStaticHwid(v: Boolean) { _useStaticHwid.value = v }
    fun setCustomHwid(v: String)     { _customHwid.value = v }

    // ── Основное ──────────────────────────────────────────
    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state = _state.asStateFlow()

    private val _input = MutableStateFlow(prefs.getString("last_input", "") ?: "")
    val input = _input.asStateFlow()

    private val _configs = MutableStateFlow<List<VpnConfig>>(emptyList())
    val configs = _configs.asStateFlow()

    private val _filter = MutableStateFlow("ВСЕ")
    val filter = _filter.asStateFlow()

    val filtered = combine(_configs, _filter) { list, f ->
        if (f == "ВСЕ") list else list.filter { it.protocol == f }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setInput(v: String) {
        _input.value = v
        prefs.edit().putString("last_input", v).apply()
    }
    fun setFilter(f: String) { _filter.value = f }

    fun process() = viewModelScope.launch {
        _state.value   = UiState.Working("Инициализация...")
        _configs.value = emptyList()
        val hwid = effectiveHwid.value
        runCatching {
            val raw = _input.value.trim()

            val url = if (raw.startsWith("happ://")) {
                _state.value = UiState.Working("Дешифровка RSA...")
                HappDecryptor.decrypt(raw).getOrThrow().url
            } else raw

            val content = if (url.startsWith("http")) {
                _state.value = UiState.Working("Загрузка подписки...")
                SubFetcher.fetch(url, hwid)
            } else url

            _state.value = UiState.Working("Парсинг конфигов...")
            val result = withContext(Dispatchers.Default) { VpnConfigParser.parse(content) }

            _configs.value = result
            _state.value   = UiState.Success(url, result.size)

        }.onFailure { _state.value = UiState.Error(it.message ?: "Неизвестная ошибка") }
    }

    fun copyAll() {
        val app = getApplication<Application>()
        val text = _configs.value.joinToString("\n") { it.rawLink }
        app.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("VPN Configs", text))
    }

    fun saveToFile(onDone: (String) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            val dir = File(
                android.os.Environment.getExternalStorageDirectory(), "Decrypts/TXT"
            ).also { it.mkdirs() }
            val domain = try {
                java.net.URI((_state.value as? UiState.Success)?.url ?: "unknown").host
                    ?.replace(":", "_") ?: "unknown"
            } catch (_: Exception) { "unknown" }
            val file = File(dir, "$domain.txt")
            file.writeText(_configs.value.joinToString("\n") { it.rawLink })
            withContext(Dispatchers.Main) { onDone(file.absolutePath) }
        }
    }

    val protocols: List<String> get() =
        listOf("ВСЕ") + _configs.value.map { it.protocol }.distinct().sorted()
}
