package com.github.cheremsha.decrypt.crypt.app.crypto

object UnicornBridge {
    init {
        System.loadLibrary("cheremshabridge")
    }

    external fun checkUnicornVersion(): String
    external fun decryptCrypt5(soBytes: ByteArray, inputBytes: ByteArray): ByteArray
}
