#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include "llama.h"

// JNI bridge for on-device summarization (llama.cpp), mirroring whisper_jni.cpp.
// Built into its own shared library (libsummarizer.so) whose llama/ggml symbols are
// hidden (-Wl,--exclude-libs,ALL) so they cannot clash with the separate copy of ggml
// compiled into libscribesync.so for Whisper.
//
// Privacy: this library performs inference only. It contains no networking code
// (llama.cpp is built without common/curl/httplib).

#define TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

struct SummarizerContext {
    llama_model   * model = nullptr;
    llama_context * ctx   = nullptr;
};

constexpr int N_CTX = 4096;

void forward_llama_log(ggml_log_level level, const char * text, void * /*user*/) {
    // Keep logcat readable: only warnings and errors from the native library.
    if (level == GGML_LOG_LEVEL_WARN) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "%s", text);
    } else if (level == GGML_LOG_LEVEL_ERROR) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "%s", text);
    }
}

std::string jstring_to_utf8(JNIEnv * env, jstring value) {
    if (value == nullptr) return {};
    const char * chars = env->GetStringUTFChars(value, nullptr);
    std::string result = chars ? chars : "";
    if (chars) env->ReleaseStringUTFChars(value, chars);
    return result;
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_scribesync_scribesync_engine_LlamaEngine_initContext(JNIEnv * env, jobject /*thiz*/, jstring model_path) {
    static bool backend_initialized = false;
    if (!backend_initialized) {
        llama_log_set(forward_llama_log, nullptr);
        llama_backend_init();
        backend_initialized = true;
    }

    const std::string path = jstring_to_utf8(env, model_path);
    LOGI("Loading summary model: %s", path.c_str());

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0; // CPU-only

    llama_model * model = llama_model_load_from_file(path.c_str(), model_params);
    if (!model) {
        LOGE("Failed to load model from %s", path.c_str());
        return 0;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx     = N_CTX;
    ctx_params.n_batch   = N_CTX; // allow the whole prompt in one llama_decode call
    ctx_params.n_threads = 4;

    llama_context * ctx = llama_init_from_model(model, ctx_params);
    if (!ctx) {
        LOGE("Failed to create llama context");
        llama_model_free(model);
        return 0;
    }

    auto * holder = new SummarizerContext{model, ctx};
    LOGI("Summary model loaded (n_ctx=%d)", N_CTX);
    return reinterpret_cast<jlong>(holder);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_scribesync_scribesync_engine_LlamaEngine_generateNative(
        JNIEnv * env, jobject /*thiz*/, jlong ctx_ptr,
        jstring system_prompt, jstring user_prompt, jint max_tokens) {
    auto * holder = reinterpret_cast<SummarizerContext *>(ctx_ptr);
    if (!holder || !holder->model || !holder->ctx) {
        LOGE("generate called with invalid context pointer");
        return nullptr;
    }
    llama_model   * model = holder->model;
    llama_context * ctx   = holder->ctx;
    const llama_vocab * vocab = llama_model_get_vocab(model);

    const std::string sys  = jstring_to_utf8(env, system_prompt);
    const std::string user = jstring_to_utf8(env, user_prompt);

    // Render the model's own chat template (Qwen ships ChatML in the GGUF).
    llama_chat_message messages[] = {
        {"system", sys.c_str()},
        {"user",   user.c_str()},
    };
    const char * tmpl = llama_model_chat_template(model, nullptr);
    if (!tmpl) tmpl = "chatml";

    std::vector<char> prompt_buf(sys.size() + user.size() + 2048);
    int prompt_len = llama_chat_apply_template(tmpl, messages, 2, true, prompt_buf.data(), (int32_t) prompt_buf.size());
    if (prompt_len > (int) prompt_buf.size()) {
        prompt_buf.resize(prompt_len);
        prompt_len = llama_chat_apply_template(tmpl, messages, 2, true, prompt_buf.data(), (int32_t) prompt_buf.size());
    }
    if (prompt_len < 0) {
        LOGE("Failed to apply chat template");
        return nullptr;
    }
    const std::string prompt(prompt_buf.data(), (size_t) prompt_len);

    // Tokenize (negative return = required token count).
    int n_prompt = -llama_tokenize(vocab, prompt.c_str(), (int32_t) prompt.size(), nullptr, 0, true, true);
    std::vector<llama_token> tokens((size_t) n_prompt);
    if (llama_tokenize(vocab, prompt.c_str(), (int32_t) prompt.size(), tokens.data(), n_prompt, true, true) < 0) {
        LOGE("Tokenization failed");
        return nullptr;
    }

    // Safety net if the Kotlin-side character clipping underestimated token counts:
    // drop tokens from the middle (transcript body) and keep both template ends intact.
    const int n_available = N_CTX - max_tokens - 8;
    if ((int) tokens.size() > n_available) {
        LOGI("Prompt too long (%zu tokens); truncating middle to %d", tokens.size(), n_available);
        const int head = n_available / 2;
        const int tail = n_available - head;
        std::vector<llama_token> truncated(tokens.begin(), tokens.begin() + head);
        truncated.insert(truncated.end(), tokens.end() - tail, tokens.end());
        tokens.swap(truncated);
    }

    LOGI("Generating summary: %zu prompt tokens, max %d output tokens", tokens.size(), (int) max_tokens);

    // Fresh generation on a reused context: clear any previous KV state.
    llama_memory_clear(llama_get_memory(ctx), true);

    llama_batch batch = llama_batch_get_one(tokens.data(), (int32_t) tokens.size());
    if (llama_decode(ctx, batch) != 0) {
        LOGE("llama_decode failed on prompt");
        return nullptr;
    }

    llama_sampler * sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    std::string output;
    char piece[512];
    for (int i = 0; i < max_tokens; ++i) {
        llama_token token = llama_sampler_sample(sampler, ctx, -1);
        if (llama_vocab_is_eog(vocab, token)) {
            break;
        }
        const int n = llama_token_to_piece(vocab, token, piece, (int32_t) sizeof(piece), 0, false);
        if (n > 0) {
            output.append(piece, (size_t) n);
        }
        batch = llama_batch_get_one(&token, 1);
        if (llama_decode(ctx, batch) != 0) {
            LOGE("llama_decode failed during generation at token %d", i);
            break;
        }
    }
    llama_sampler_free(sampler);

    LOGI("Generation finished (%zu bytes)", output.size());

    jbyteArray result = env->NewByteArray((jsize) output.size());
    if (!result) return nullptr;
    env->SetByteArrayRegion(result, 0, (jsize) output.size(), reinterpret_cast<const jbyte *>(output.data()));
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_scribesync_scribesync_engine_LlamaEngine_freeContext(JNIEnv * /*env*/, jobject /*thiz*/, jlong ctx_ptr) {
    auto * holder = reinterpret_cast<SummarizerContext *>(ctx_ptr);
    if (!holder) return;
    if (holder->ctx)   llama_free(holder->ctx);
    if (holder->model) llama_model_free(holder->model);
    delete holder;
    LOGI("Summary model freed");
}
