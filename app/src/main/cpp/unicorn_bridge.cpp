#include <jni.h>
#include <unicorn/unicorn.h>
#include <android/log.h>

#define LOG_TAG "UnicornBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_com_github_cheremsha_decrypt_crypt_app_crypto_UnicornBridge_checkUnicornVersion(
        JNIEnv *env, jobject /* this */) {
    unsigned int major, minor;
    uc_version(&major, &minor);
    LOGI("Unicorn version: %u.%u", major, minor);

    char buf[32];
    snprintf(buf, sizeof(buf), "%u.%u", major, minor);
    return env->NewStringUTF(buf);
}
