package su.happ.proxyutility.util

/**
 * Точное соответствие классу/методу из liberror-code.so.
 * JNI находит реализацию по символу:
 * Java_su_happ_proxyutility_util_ErrorCodeJNIWrapper_jniGetErrorMessageFromString2
 *
 * На реальном Android (ARM64) эта функция выполняется НАТИВНО,
 * без эмуляции — ОС сама резолвит JNI вызовы внутрь нашего
 * EncryptedSubUrlHelper.getHelp() через стандартный мост.
 */
class ErrorCodeJNIWrapper {
    external fun jniGetErrorMessageFromString2(input: ByteArray): ByteArray

    companion object {
        init {
            System.loadLibrary("error-code")
        }
    }
}
