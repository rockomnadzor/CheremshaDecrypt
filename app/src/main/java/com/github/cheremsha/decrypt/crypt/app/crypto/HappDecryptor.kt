package com.github.cheremsha.decrypt.crypt.app.crypto

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher

object HappDecryptor {

    data class DecryptResult(val url: String, val keyIndex: Int, val keyBits: Int)

    fun decrypt(happUrl: String): Result<DecryptResult> = runCatching {
        require(happUrl.startsWith("happ://")) { "Не является happ:// ссылкой" }

        val path = happUrl.removePrefix("happ://")
        val slash = path.indexOf('/')
        require(slash >= 0) { "Неверный формат happ://" }

        val cryptType = path.substring(0, slash)
        val payload   = path.substring(slash + 1)

        val keyIdx = cryptType.last().digitToIntOrNull()?.minus(1)
            ?: error("Не удалось определить ключ из: $cryptType")
        require(keyIdx in Keys.RSA_KEYS.indices) { "Неверный индекс ключа: $keyIdx" }

        val pkcs1 = Base64.decode(Keys.RSA_KEYS[keyIdx], Base64.DEFAULT)
        val pkcs8 = pkcs1ToPkcs8(pkcs1)
        val privateKey = KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(pkcs8))
        val keySize = ((privateKey as RSAPrivateKey).modulus.bitLength() + 7) / 8

        // URL-safe base64 → standard
        val safe = payload.replace('-', '+').replace('_', '/')
        val padded = safe + "=".repeat((4 - safe.length % 4) % 4)
        val cipherBytes = Base64.decode(padded, Base64.DEFAULT)

        require(cipherBytes.isNotEmpty()) { "Payload пустой — URL обрезан!" }
        require(cipherBytes.size % keySize == 0) {
            "URL ОБРЕЗАН! Не хватает ~${keySize - cipherBytes.size % keySize} байт"
        }

        val out = ByteArrayOutputStream()
        for (offset in cipherBytes.indices step keySize) {
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
            cipher.init(Cipher.DECRYPT_MODE, privateKey)
            out.write(cipher.doFinal(cipherBytes, offset, keySize))
        }

        val plaintext = out.toByteArray()
            .dropWhile { it == 0.toByte() }
            .toByteArray()
            .toString(Charsets.UTF_8)
            .trim()

        require(plaintext.isNotEmpty()) { "Результат дешифровки пуст" }
        val url = if (plaintext.startsWith("http")) plaintext else "https://$plaintext"
        DecryptResult(url, keyIdx + 1, keySize * 8)
    }

    // Оборачиваем PKCS#1 RSA ключ в PKCS#8 структуру (без внешних библиотек)
    private fun pkcs1ToPkcs8(pkcs1: ByteArray): ByteArray {
        fun encLen(n: Int): ByteArray = when {
            n < 0x80 -> byteArrayOf(n.toByte())
            n < 0x100 -> byteArrayOf(0x81.toByte(), n.toByte())
            else -> byteArrayOf(0x82.toByte(), (n ushr 8 and 0xFF).toByte(), (n and 0xFF).toByte())
        }
        fun seq(v: ByteArray) = byteArrayOf(0x30) + encLen(v.size) + v
        fun oct(v: ByteArray) = byteArrayOf(0x04) + encLen(v.size) + v

        // AlgorithmIdentifier { OID rsaEncryption (1.2.840.113549.1.1.1), NULL }
        val algId = seq(byteArrayOf(
            0x06, 0x09,
            0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x01,
            0x05, 0x00
        ))

        return seq(byteArrayOf(0x02, 0x01, 0x00) + algId + oct(pkcs1))
    }
}
