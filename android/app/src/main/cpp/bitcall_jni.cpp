// JNI glue for app.bitcall.ai.LlamaBridge / WhisperBridge.
#include <jni.h>

#include "bitcall_ai.h"

namespace {
std::string str(JNIEnv * env, jstring s) {
    if (!s) return "";
    const char * c = env->GetStringUTFChars(s, nullptr);
    std::string out(c);
    env->ReleaseStringUTFChars(s, c);
    return out;
}

// Model output can end mid-way through a UTF-8 sequence; NewStringUTF would abort on that.
jstring utf8(JNIEnv * env, const std::string & s) {
    size_t end = s.size(), i = 0;
    while (i < s.size()) {
        const unsigned char c = s[i];
        const size_t len = c < 0x80 ? 1 : (c >> 5) == 0x6 ? 2 : (c >> 4) == 0xE ? 3 : (c >> 3) == 0x1E ? 4 : 0;
        if (len == 0 || i + len > s.size()) { end = i; break; }
        i += len;
    }
    return env->NewStringUTF(s.substr(0, end).c_str());
}

std::atomic<bool> g_cancel{false};
}

extern "C" {

JNIEXPORT void JNICALL
Java_app_bitcall_ai_LlamaBridge_nativeInit(JNIEnv * env, jobject, jstring nativeLibDir) {
    bit_backend_init(str(env, nativeLibDir).c_str());
}

JNIEXPORT jlong JNICALL
Java_app_bitcall_ai_LlamaBridge_nativeLoad(JNIEnv * env, jobject, jstring path, jint nCtx, jint threads) {
    return reinterpret_cast<jlong>(bit_llm_load(str(env, path).c_str(), nCtx, threads));
}

JNIEXPORT jstring JNICALL
Java_app_bitcall_ai_LlamaBridge_nativeGenerate(JNIEnv * env, jobject, jlong handle, jstring system, jstring user,
                                               jint maxTokens, jfloat temperature, jboolean thinking) {
    g_cancel = false;
    const auto out = bit_llm_generate(reinterpret_cast<BitLlm *>(handle), str(env, system), str(env, user),
                                      maxTokens, temperature, thinking, &g_cancel);
    return utf8(env, out);
}

JNIEXPORT void JNICALL
Java_app_bitcall_ai_LlamaBridge_nativeCancel(JNIEnv *, jobject) { g_cancel = true; }

JNIEXPORT void JNICALL
Java_app_bitcall_ai_LlamaBridge_nativeFree(JNIEnv *, jobject, jlong handle) {
    bit_llm_free(reinterpret_cast<BitLlm *>(handle));
}

JNIEXPORT jstring JNICALL
Java_app_bitcall_ai_WhisperBridge_nativeTranscribe(JNIEnv * env, jobject, jstring modelPath, jfloatArray pcm,
                                                   jstring language, jint threads) {
    const jsize n = env->GetArrayLength(pcm);
    std::vector<float> samples(n);
    env->GetFloatArrayRegion(pcm, 0, n, samples.data());
    const auto lang = str(env, language);
    return utf8(env, bit_whisper_transcribe(str(env, modelPath).c_str(), samples, lang.c_str(), threads));
}

}
