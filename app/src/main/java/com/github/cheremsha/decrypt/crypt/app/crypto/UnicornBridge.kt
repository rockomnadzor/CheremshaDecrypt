package com.github.cheremsha.decrypt.crypt.app.crypto

object UnicornBridge {
    init {
        System.loadLibrary("cheremshabridge")
    }

    external fun checkUnicornVersion(): String
}
