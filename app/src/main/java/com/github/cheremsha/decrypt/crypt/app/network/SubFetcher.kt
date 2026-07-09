package com.github.cheremsha.decrypt.crypt.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object SubFetcher {

    const val DEFAULT_USER_AGENT = "Happ/3.18.3/Android/17771400994551771562"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(
        url: String,
        hwid: String,
        userAgent: String = DEFAULT_USER_AGENT
    ): String = withContext(Dispatchers.IO) {
        val urlWithHwid = if ('?' in url) "$url&hwid=$hwid" else "$url?hwid=$hwid"

        for ((_, fetchUrl) in listOf("с HWID" to urlWithHwid, "без HWID" to url)) {
            runCatching {
                val req = Request.Builder()
                    .url(fetchUrl)
                    .header("User-Agent", userAgent)
                    .header("X-HWID", hwid)
                    .header("Accept", "*/*")
                    .build()
                val body = client.newCall(req).execute().use { it.body?.string()?.trim() ?: "" }
                if (body.isNotEmpty()) return@withContext body
            }
        }
        throw Exception("Не удалось получить данные по URL")
    }
}
