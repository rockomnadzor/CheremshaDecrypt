package com.github.cheremsha.decrypt.crypt.app.crypto

import android.content.Context

object ApiKeyManager {
    private const val PREFS = "cheremsha_prefs"
    private const val PROVIDER_KEY = "api_provider"

    private fun keyPrefKey(p: ApiProvider) = "api_key_${p.id}"

    fun getProvider(context: Context): ApiProvider? {
        val id = context.getSharedPreferences(PREFS, 0).getString(PROVIDER_KEY, null) ?: return null
        return ApiProvider.entries.find { it.id == id }
    }

    fun setProvider(context: Context, provider: ApiProvider) {
        context.getSharedPreferences(PREFS, 0).edit().putString(PROVIDER_KEY, provider.id).apply()
    }

    fun getKey(context: Context, provider: ApiProvider): String? =
        context.getSharedPreferences(PREFS, 0).getString(keyPrefKey(provider), null)

    fun setKey(context: Context, provider: ApiProvider, key: String) {
        context.getSharedPreferences(PREFS, 0).edit().putString(keyPrefKey(provider), key).apply()
    }

    /** Готово ли приложение к работе: провайдер выбран, и если ему нужен ключ — ключ сохранён. */
    fun hasValidSetup(context: Context): Boolean {
        val p = getProvider(context) ?: return false
        if (!p.requiresKey) return true
        return !getKey(context, p).isNullOrBlank()
    }

    fun clearProvider(context: Context) {
        context.getSharedPreferences(PREFS, 0).edit().remove(PROVIDER_KEY).apply()
    }
}
