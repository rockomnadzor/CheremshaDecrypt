package com.github.cheremsha.decrypt.crypt.app.crypto

enum class ApiProvider(val id: String, val label: String, val requiresKey: Boolean) {
    HAPPY_DECODER("happy_decoder", "Happ Decoder API", true),
    KFWL_LOL("kfwl_lol", "KFWL lol API", false),
    RING_ENCRYPT("ring_encrypt", "Ring Encrypt API", true)
}
