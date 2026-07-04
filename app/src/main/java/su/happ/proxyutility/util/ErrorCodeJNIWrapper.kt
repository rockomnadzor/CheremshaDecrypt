package su.happ.proxyutility.util

class ErrorCodeJNIWrapper {
    external fun jniGetErrorMessageFromString2(input: ByteArray): ByteArray

    companion object {
        private var loaded = false

        fun ensureLoaded(): Boolean {
            if (loaded) return true
            return try {
                System.loadLibrary("error-code")
                loaded = true
                true
            } catch (e: UnsatisfiedLinkError) {
                false
            }
        }
    }
}
