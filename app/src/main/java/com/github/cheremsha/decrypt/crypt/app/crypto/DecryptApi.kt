package com.github.cheremsha.decrypt.crypt.app.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

const val HAPPY_DECODER_DEMO_KEY = "hd_demo_a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6"
const val HAPPY_DECODER_SITE = "https://happy-decoder.cc/api"
const val RING_BOT_LINK = "https://t.me/Ring_encrypt_bot"

private const val HAPPY_DECODER_BASE = "https://happy-decoder.cc"
private const val KFWL_BASE = "https://api.ioo.ir"
private const val RING_BASE = "https://happ.ring-team.ru"

object DecryptApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Один HTTP-запрос на весь decrypt, маршрут зависит от выбранного провайдера. */
    suspend fun decrypt(provider: ApiProvider, key: String?, link: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val (url, headers, body) = buildRequest(provider, key, link)
                val reqBuilder = Request.Builder()
                    .url(url)
                    .header("Content-Type", "application/json")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                headers.forEach { (k, v) -> reqBuilder.header(k, v) }

                client.newCall(reqBuilder.build()).execute().use { resp ->
                    val text = resp.body?.string() ?: error("Пустой ответ от сервера")
                    parseResponse(text)
                }
            }
        }

    suspend fun validate(provider: ApiProvider, key: String?): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            when (provider) {
                ApiProvider.KFWL_LOL -> {
                    val req = Request.Builder().url("$KFWL_BASE/health").get().build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@runCatching false
                        val obj = JSONObject(resp.body?.string() ?: "{}")
                        obj.optBoolean("ok", false)
                    }
                }
                ApiProvider.HAPPY_DECODER -> {
                    val body = JSONObject().put("url", "happ://add/https://validate.local/ping")
                    val req = Request.Builder()
                        .url("$HAPPY_DECODER_BASE/api/v1/decrypt")
                        .header("Authorization", "Bearer ${key.orEmpty()}")
                        .header("Content-Type", "application/json")
                        .post(body.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    client.newCall(req).execute().use { it.code != 401 && it.code != 403 }
                }
                ApiProvider.RING_ENCRYPT -> {
                    val body = JSONObject().put("url", "https://validate.local/ping")
                    val req = Request.Builder()
                        .url("$RING_BASE/api/decrypt")
                        .header("X-API-Key", key.orEmpty())
                        .header("Content-Type", "application/json")
                        .post(body.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    client.newCall(req).execute().use { it.code != 401 && it.code != 403 }
                }
            }
        }.getOrDefault(false)
    }

    private fun buildRequest(provider: ApiProvider, key: String?, link: String): Triple<String, Map<String, String>, JSONObject> {
        return when (provider) {
            ApiProvider.HAPPY_DECODER -> Triple(
                "$HAPPY_DECODER_BASE/api/v1/decrypt",
                mapOf("Authorization" to "Bearer ${key.orEmpty()}"),
                JSONObject().put("url", link)
            )
            ApiProvider.KFWL_LOL -> {
                val endpoint = if (link.startsWith("incy://")) "/v1/incy/decrypt" else "/v1/happ/decrypt"
                Triple("$KFWL_BASE$endpoint", emptyMap(), JSONObject().put("link", link))
            }
            ApiProvider.RING_ENCRYPT -> Triple(
                "$RING_BASE/api/decrypt",
                mapOf("X-API-Key" to key.orEmpty()),
                JSONObject().put("url", link)
            )
        }
    }

    /** Гибкий парсер: пробуем несколько вероятных полей ответа, иначе — сырой текст если похож на URL. */
    private fun parseResponse(text: String): String {
        val obj = runCatching { JSONObject(text) }.getOrNull()
        if (obj != null) {
            if (obj.has("error")) error(obj.optString("error", "Ошибка API"))
            if (obj.has("ok") && !obj.optBoolean("ok", true)) {
                error(obj.optString("message", obj.optString("error", "Ошибка API")))
            }
            for (field in listOf("result", "decryptedUrl", "url", "data")) {
                if (obj.has(field)) {
                    val v = obj.opt(field)
                    if (v is String && v.isNotBlank()) return v
                }
            }
            error("Не удалось извлечь URL из ответа API")
        }
        val trimmed = text.trim()
        if (trimmed.startsWith("http")) return trimmed
        error("Некорректный ответ сервера")
    }
}
