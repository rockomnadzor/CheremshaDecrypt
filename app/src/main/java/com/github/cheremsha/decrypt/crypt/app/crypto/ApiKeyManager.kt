package com.github.cheremsha.decrypt.crypt.app.crypto

import android.content.Context

object ApiKeyManager {
    private const val PREFS = "cheremsha_prefs"
    private const val KEY = "happy_decoder_api_key"

    fun getKey(context: Context): String? =
        context.getSharedPreferences(PREFS, 0).getString(KEY, null)

    fun setKey(context: Context, key: String) {
        context.getSharedPreferences(PREFS, 0).edit().putString(KEY, key).apply()
    }

    fun clearKey(context: Context) {
        context.getSharedPreferences(PREFS, 0).edit().remove(KEY).apply()
    }

    fun hasKey(context: Context): Boolean = !getKey(context).isNullOrBlank()
}
