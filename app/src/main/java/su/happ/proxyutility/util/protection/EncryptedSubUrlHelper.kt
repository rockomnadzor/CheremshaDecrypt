package su.happ.proxyutility.util.protection

import org.json.JSONObject

/**
 * Точный Kotlin-порт getHelp() из emu_core.js — статический метод,
 * который liberror-code.so вызывает через JNI CallStaticObjectMethodV
 * для получения base64 RSA-ключа по маркеру.
 */
object EncryptedSubUrlHelper {

    private var keyTable: JSONObject? = null

    fun init(json: String) {
        keyTable = JSONObject(json)
    }

    @JvmStatic
    fun getHelp(markerIn: String): String {
        val n = markerIn.length
        val marker = StringBuilder()
        for (i in 0 until n) marker.append(markerIn[n - 1 - i])

        val m0 = StringBuilder()
        var i = 0
        while (i + 1 < n) {
            m0.append(marker[i + 1])
            i += 2
        }
        if (n % 2 == 1) m0.append(marker[n - 1])

        return keyTable?.optString(m0.toString(), "") ?: ""
    }
}
