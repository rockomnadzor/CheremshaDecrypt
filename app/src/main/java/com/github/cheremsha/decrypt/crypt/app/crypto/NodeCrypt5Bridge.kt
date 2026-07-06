package com.github.cheremsha.decrypt.crypt.app.crypto

import android.content.Context
import io.nodejs.mobile.nodejsmobile.NodeJS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object NodeCrypt5Bridge {

    private const val PORT = 51720
    private var started = false

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun startOnce(context: Context) {
        if (started) return
        started = true
        NodeJS.startNodeWithArguments(arrayOf("node", "main.js"))
    }

    suspend fun decrypt(link: String): String = withContext(Dispatchers.IO) {
        val json = JSONObject().put("link", link).toString()
        val body = json.toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("http://127.0.0.1:$PORT/decrypt")
            .post(body)
            .build()

        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string() ?: error("Пустой ответ от Node")
            val obj = JSONObject(text)
            if (obj.getBoolean("ok")) {
                obj.getString("url")
            } else {
                error(obj.optString("error", "Неизвестная ошибка crypt5"))
            }
        }
    }
}
