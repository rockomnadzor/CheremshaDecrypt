package com.github.cheremsha.decrypt.crypt.app.crypto

import android.content.Context
import android.util.Base64
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher

class HappDecryptor(private val context: Context) {

    companion object {
        fun decrypt(link: String): Result<String> = runCatching {
            HappDecryptor(android.app.Application()).decrypt(link)
        }
    }

    fun decrypt(link: String): String {
        val clean = if (link.startsWith("happ://")) link.removePrefix("happ://") else link

        return try {
            when {
                clean.startsWith("crypt/") -> rsaDecrypt(0, clean.removePrefix("crypt/"))
                clean.startsWith("crypt2/") -> rsaDecrypt(1, clean.removePrefix("crypt2/"))
                clean.startsWith("crypt3/") -> rsaDecrypt(2, clean.removePrefix("crypt3/"))
                clean.startsWith("crypt4/") -> rsaDecrypt(3, clean.removePrefix("crypt4/"))
                clean.startsWith("crypt5/") -> "crypt5 пока не поддерживается"
                else -> "Неизвестный формат"
            }
        } catch (e: Exception) {
            "Ошибка: ${e.message}"
        }
    }

    private fun rsaDecrypt(keyIndex: Int, payload: String): String {
        val keyB64 = Keys.PKCS1_KEYS.getOrNull(keyIndex) ?: return "Ключ не найден"
        val keyBytes = Base64.decode(keyB64, Base64.DEFAULT)

        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        val kf = KeyFactory.getInstance("RSA")
        val privateKey = kf.generatePrivate(keySpec)

        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey)

        val encrypted = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING)
        val decrypted = cipher.doFinal(encrypted)

        return String(decrypted, Charsets.UTF_8).trim()
    }
}
