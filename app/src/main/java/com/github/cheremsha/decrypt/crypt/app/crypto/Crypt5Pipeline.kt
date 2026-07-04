package com.github.cheremsha.decrypt.crypt.app.crypto

import android.content.Context
import android.util.Base64

object Crypt5Pipeline {

    private var soBytes: ByteArray? = null

    fun init(context: Context) {
        if (soBytes == null) {
            soBytes = context.assets.open("liberror-code.so").use { it.readBytes() }
        }
    }

    fun decrypt(payload: String): String {
        val so = soBytes ?: error("Crypt5Pipeline не инициализирован")

        val nativeIn = m4831f(payload).toByteArray(Charsets.UTF_8)
        val outputBytes = UnicornBridge.decryptCrypt5(so, nativeIn)
        require(outputBytes.isNotEmpty()) { "crypt5: пустой результат от эмулятора" }

        val obfuscated = String(outputBytes, Charsets.UTF_8)
        val swapped = swapPairs(obfuscated)
        val urlBytes = b64DecodeUrlSafe(swapped)
        return String(urlBytes, Charsets.UTF_8)
    }

    private fun swapPairs(s: String): String {
        val arr = s.toCharArray()
        var i = 0
        while (i + 1 < arr.size) {
            val tmp = arr[i]; arr[i] = arr[i + 1]; arr[i + 1] = tmp
            i += 2
        }
        return String(arr)
    }

    private fun m4831f(s: String): String {
        val full = s.length - (s.length % 6)
        val out = StringBuilder()
        var i = 0
        while (i < full) {
            val b = s.substring(i, i + 6)
            out.append(b[1]).append(b[3]).append(b[5]).append(b[0]).append(b[2]).append(b[4])
            i += 6
        }
        out.append(s.substring(full))
        return out.toString()
    }

    private fun b64DecodeUrlSafe(s: String): ByteArray {
        val safe = s.replace('-', '+').replace('_', '/')
        val padded = safe + "=".repeat((4 - safe.length % 4) % 4)
        return Base64.decode(padded, Base64.DEFAULT)
    }
}
