#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "whisper.cpp/whisper.h"

#define TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

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
    auto *ctx = reinterpret_cast<struct whisper_context *>(ctx_ptr);
    jfloat *samples = env->GetFloatArrayElements(audio_data, nullptr);
    jsize len = env->GetArrayLength(audio_data);

    LOGI("Transcribing %d samples into segments", len);

    // Create an ArrayList to return
    jclass listClass = env->FindClass("java/util/ArrayList");
    jmethodID listInit = env->GetMethodID(listClass, "<init>", "()V");
    jobject listObj = env->NewObject(listClass, listInit);
    jmethodID listAdd = env->GetMethodID(listClass, "add", "(Ljava/lang/Object;)Z");

    // Create a Segment object (mock)
    jclass segmentClass = env->FindClass("com/scribesync/scribesync/engine/WhisperEngine$Segment");
    jmethodID segmentInit = env->GetMethodID(segmentClass, "<init>", "(Ljava/lang/String;JJI)V");

    jstring text = env->NewStringUTF("Mock transcribed segment");
    jobject segmentObj = env->NewObject(segmentClass, segmentInit, text, (jlong)0, (jlong)1000, (jint)1);

    env->CallBooleanMethod(listObj, listAdd, segmentObj);

    env->ReleaseFloatArrayElements(audio_data, samples, JNI_ABORT);
    return listObj;
}

extern "C" JNIEXPORT void JNICALL
Java_com_scribesync_scribesync_engine_WhisperEngine_freeContext(JNIEnv *env, jobject thiz, jlong ctx_ptr) {
    LOGI("Freeing Whisper context");
    auto *ctx = reinterpret_cast<struct whisper_context *>(ctx_ptr);
    whisper_free(ctx);
}
