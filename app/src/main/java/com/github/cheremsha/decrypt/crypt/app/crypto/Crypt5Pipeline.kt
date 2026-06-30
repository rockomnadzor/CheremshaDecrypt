package com.github.cheremsha.decrypt.crypt.app.crypto

import android.util.Base64
import su.happ.proxyutility.util.ErrorCodeJNIWrapper

/**
 * Полный пайплайн crypt5: shuffle -> native .so (через JNI) -> swapPairs -> base64
 * Точный порт decryptCrypt5() из DeCrypt.js, но без эмуляции —
 * .so выполняется нативно на ARM64 устройстве.
 */
object Crypt5Pipeline {

    fun decrypt(payload: String): String {
        val nativeIn = m4831f(payload)
        val inputBytes = nativeIn.toByteArray(Charsets.UTF_8)

        val outputBytes = ErrorCodeJNIWrapper().jniGetErrorMessageFromString2(inputBytes)
        require(outputBytes.isNotEmpty()) { "crypt5: пустой результат от native lib" }

        val obfuscated = String(outputBytes, Charsets.UTF_8)
        val swapped = swapPairs(obfuscated)
        val urlBytes = b64DecodeUrlSafe(swapped)
        return String(urlBytes, Charsets.UTF_8)
    }

    /** Перестановка пар символов: ABCD -> BADC */
    private fun swapPairs(s: String): String {
        val arr = s.toCharArray()
        var i = 0
        while (i + 1 < arr.size) {
            val tmp = arr[i]; arr[i] = arr[i + 1]; arr[i + 1] = tmp
            i += 2
        }
        return String(arr)
    }

    /** Перетасовка блоков по 6 символов: [1,3,5,0,2,4] */
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
