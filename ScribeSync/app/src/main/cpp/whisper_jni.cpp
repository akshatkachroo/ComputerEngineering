#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "whisper.h"

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
        segmentInit = env->GetMethodID(segmentClass, "<init>", "(Ljava/lang/String;JJZ)V");
    } else {
        LOGE("Failed to find Segment class");
    }

    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_scribesync_scribesync_engine_WhisperEngine_initContext(JNIEnv *env, jobject thiz, jstring model_path) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Initializing Whisper with model: %s", path);

    struct whisper_context_params params = whisper_context_default_params();
    struct whisper_context * ctx = whisper_init_from_file_with_params(path, params);

    if (!ctx) {
        LOGE("Failed to initialize whisper_context from file: %s", path);
    } else {
        LOGI("Whisper context initialized successfully");
    }
    env->ReleaseStringUTFChars(model_path, path);
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_scribesync_scribesync_engine_WhisperEngine_transcribeSegments(JNIEnv *env, jobject thiz, jlong ctx_ptr, jfloatArray audio_data) {
    if (!arrayListClass || !segmentClass) {
        LOGE("JNI classes not cached correctly");
        return nullptr;
    }

    auto *ctx = reinterpret_cast<struct whisper_context *>(ctx_ptr);
    if (!ctx) {
        LOGE("Whisper context pointer is null in transcribeSegments");
        return nullptr;
    }

    jfloat *samples = env->GetFloatArrayElements(audio_data, nullptr);
    jsize len = env->GetArrayLength(audio_data);

    LOGI("Transcribing %d audio samples", len);

    // Using WHISPER_SAMPLING_BEAM_SEARCH for better speaker turn detection.
    // Beam size of 2 is a good balance between accuracy and mobile CPU performance.
    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_BEAM_SEARCH);
    params.print_realtime = false;
    params.print_progress = false;
    params.n_threads = 4;

    params.beam_search.beam_size = 2;

    // Suppress hallucinations and noise artifacts
    params.entropy_thold = 2.4f;
    params.no_speech_thold = 0.5f;
    params.suppress_blank = true;
    params.suppress_nst = true;

    // Each call is one silence-isolated phrase from the Kotlin-side VAD, so
    // there's no prior-segment context worth carrying into this call.
    params.no_context = true;

    // tinydiarize: when true, the model emits an internal turn token whenever
    // it detects a speaker change (whisper_full_get_segment_speaker_turn_next
    // surfaces that per-segment below). Enabled to support speaker splitting
    // using the tdrz-finetuned model asset.
    params.tdrz_enable = true;

    if (whisper_full(ctx, params, samples, len) != 0) {
        LOGE("Failed to run whisper_full");
        env->ReleaseFloatArrayElements(audio_data, samples, JNI_ABORT);
        return nullptr;
    }

    jobject listObj = env->NewObject(arrayListClass, arrayListInit);

    int n_segments = whisper_full_n_segments(ctx);
    LOGI("Transcription finished. Found %d segments", n_segments);

    for (int i = 0; i < n_segments; ++i) {
        const char * text_str = whisper_full_get_segment_text(ctx, i);
        jlong t0 = whisper_full_get_segment_t0(ctx, i);
        jlong t1 = whisper_full_get_segment_t1(ctx, i);

        // A turn flagged for THIS segment or the PREVIOUS one indicates a change.
        // tdrz models emit a special turn token (solm) which we check here.
        // Ignore turns on the very first segment of a chunk to avoid Speaker 2 starts.
        bool isNewSpeaker = (i > 0) && (whisper_full_get_segment_speaker_turn_next(ctx, i) ||
                                         whisper_full_get_segment_speaker_turn_next(ctx, i - 1));

        // Log turn detection confidence for debugging
        if (isNewSpeaker) {
            LOGI("Speaker change detected at segment %d", i);
        }

        jstring text = env->NewStringUTF(text_str);
        jobject segmentObj = env->NewObject(segmentClass, segmentInit, text, t0 * 10, t1 * 10, (jboolean)isNewSpeaker);
        env->CallBooleanMethod(listObj, arrayListAdd, segmentObj);
        env->DeleteLocalRef(text);
        env->DeleteLocalRef(segmentObj);
    }

    env->ReleaseFloatArrayElements(audio_data, samples, JNI_ABORT);
    return listObj;
}

extern "C" JNIEXPORT void JNICALL
Java_com_scribesync_scribesync_engine_WhisperEngine_freeContext(JNIEnv *env, jobject thiz, jlong ctx_ptr) {
    auto *ctx = reinterpret_cast<struct whisper_context *>(ctx_ptr);
    if (ctx) {
        whisper_free(ctx);
    }
}
