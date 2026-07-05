package su.happ.proxyutility.util.protection

import org.json.JSONObject

/**
 * Точный Kotlin-порт getHelp() из emu_core.js.
 * liberror-code.so вызывает этот статический метод через JNI
 * CallStaticObjectMethodV, чтобы получить base64 RSA-ключ по маркеру.
 */
object EncryptedSubUrlHelper {

    private var keyTable: JSONObject? = null

    // Диагностика без logcat/ADB — просто копим вызовы в памяти
    val callLog = mutableListOf<String>()

    fun init(json: String) {
        keyTable = JSONObject(json)
        callLog.clear()
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

        val key = keyTable?.optString(m0.toString(), "") ?: ""
        callLog.add("marker(len=$n)->M0='${m0}' keylen=${key.length}")
        return key
    }
}
