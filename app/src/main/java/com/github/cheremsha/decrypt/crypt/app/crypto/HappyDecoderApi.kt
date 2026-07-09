package com.github.cheremsha.decrypt.crypt.app.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object HappyDecoderApi {

    private const val BASE_URL = "https://happy-decoder.cc"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Один HTTP-запрос: happ://crypt* или happ://add/ -> расшифрованный URL. */
    suspend fun decrypt(link: String, apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val json = JSONObject().put("url", link).toString()
            val body = json.toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("$BASE_URL/api/v1/decrypt")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(body)
                .build()

            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string() ?: error("Пустой ответ от сервера")
                val obj = JSONObject(text)
                if (obj.has("error")) {
                    error(obj.getString("error"))
                }
                obj.getString("decryptedUrl")
            }
        }
    }

    /** Лёгкая проверка ключа через passthrough-формат (не тратит крипто-лимиты). */
    suspend fun validateKey(apiKey: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val json = JSONObject().put("url", "happ://add/https://validate.local/ping").toString()
            val body = json.toRequestBody("application/json".toMediaType())
            val req = Request.Builder()
                .url("$BASE_URL/api/v1/decrypt")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(body)
                .build()

            client.newCall(req).execute().use { resp -> resp.code != 401 && resp.code != 403 }
        }.getOrDefault(false)
    }
}
