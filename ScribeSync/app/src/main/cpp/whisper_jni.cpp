#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "whisper.cpp/whisper.h"

#define TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Global references for caching with null-safety
jclass arrayListClass = nullptr;
jmethodID arrayListInit = nullptr;
jmethodID arrayListAdd = nullptr;
jclass segmentClass = nullptr;
jmethodID segmentInit = nullptr;

extern "C" JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    // Cache ArrayList
    jclass localArrayListClass = env->FindClass("java/util/ArrayList");
    if (localArrayListClass) {
        arrayListClass = reinterpret_cast<jclass>(env->NewGlobalRef(localArrayListClass));
        arrayListInit = env->GetMethodID(arrayListClass, "<init>", "()V");
        arrayListAdd = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");
    } else {
        LOGE("Failed to find ArrayList class");
    }

    // Cache Segment
    jclass localSegmentClass = env->FindClass("com/scribesync/scribesync/engine/WhisperEngine$Segment");
    if (localSegmentClass) {
        segmentClass = reinterpret_cast<jclass>(env->NewGlobalRef(localSegmentClass));
        segmentInit = env->GetMethodID(segmentClass, "<init>", "(Ljava/lang/String;JJI)V");
    } else {
        LOGE("Failed to find Segment class");
    }

    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_scribesync_scribesync_engine_WhisperEngine_initContext(JNIEnv *env, jobject thiz, jstring model_path) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Initializing Whisper with model: %s", path);
    struct whisper_context * ctx = whisper_init_from_file(path);
    env->ReleaseStringUTFChars(model_path, path);
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_scribesync_scribesync_engine_WhisperEngine_transcribeSegments(JNIEnv *env, jobject thiz, jlong ctx_ptr, jfloatArray audio_data) {
    if (!arrayListClass || !segmentClass) {
        LOGE("JNI classes not cached correctly");
        return nullptr;
    }

    jfloat *samples = env->GetFloatArrayElements(audio_data, nullptr);
    jsize len = env->GetArrayLength(audio_data);

    jobject listObj = env->NewObject(arrayListClass, arrayListInit);

    // Mock segment for testing pipe integration
    jstring text = env->NewStringUTF("Mock transcribed segment");
    jobject segmentObj = env->NewObject(segmentClass, segmentInit, text, (jlong)0, (jlong)1000, (jint)1);
    env->CallBooleanMethod(listObj, arrayListAdd, segmentObj);

    env->ReleaseFloatArrayElements(audio_data, samples, JNI_ABORT);
    return listObj;
}

extern "C" JNIEXPORT void JNICALL
Java_com_scribesync_scribesync_engine_WhisperEngine_freeContext(JNIEnv *env, jobject thiz, jlong ctx_ptr) {
    auto *ctx = reinterpret_cast<struct whisper_context *>(ctx_ptr);
    whisper_free(ctx);
}
