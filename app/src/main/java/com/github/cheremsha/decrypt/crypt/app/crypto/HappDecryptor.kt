package com.github.cheremsha.decrypt.crypt.app.crypto

import android.util.Base64
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher

object HappDecryptor {

    fun decrypt(link: String): Result<String> = runCatching {
        val clean = if (link.startsWith("happ://")) link.removePrefix("happ://") else link

        when {
            clean.startsWith("crypt/")  -> rsaDecrypt(0, clean.removePrefix("crypt/"))
            clean.startsWith("crypt2/") -> rsaDecrypt(1, clean.removePrefix("crypt2/"))
            clean.startsWith("crypt3/") -> rsaDecrypt(2, clean.removePrefix("crypt3/"))
            clean.startsWith("crypt4/") -> rsaDecrypt(3, clean.removePrefix("crypt4/"))
            clean.startsWith("crypt5/") -> Crypt5Pipeline.decrypt(clean.removePrefix("crypt5/"))
            else -> error("Неизвестный формат")
        }
    }

    private fun rsaDecrypt(keyIndex: Int, payload: String): String {
        var data = payload.trim().replace('-', '+').replace('_', '/')
        while (data.length % 4 != 0) data += "="

        val keyB64 = Keys.PKCS1_KEYS.getOrNull(keyIndex) ?: error("Ключ не найден")
        val keyBytes = Base64.decode(keyB64, Base64.DEFAULT)

        val privateKey = KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(keyBytes))

        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey)

        val encrypted = Base64.decode(data, Base64.DEFAULT)
        val decrypted = cipher.doFinal(encrypted)

        return String(decrypted, Charsets.UTF_8).trim()
    }
}
