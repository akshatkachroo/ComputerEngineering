package com.scribesync.scribesync.engine

/**
 * LlamaEngine - JNI bridge into the native `summarizer` library (llama.cpp), mirroring
 * the WhisperEngine pattern. All inference is 100% on-device; this class performs no I/O.
 *
 * Callers own the lifecycle discipline: initContext/generate/freeContext must not race,
 * and generate() blocks the calling thread for the whole inference (run on a background
 * dispatcher).
 */
class LlamaEngine {

    companion object {
        init {
            System.loadLibrary("summarizer")
        }
    }

    /**
     * Loads a GGUF model and creates an inference context.
     * @return native context pointer, or 0 on failure.
     */
    external fun initContext(modelPath: String): Long

    /**
     * Runs chat-templated generation for the given system + user prompt.
     * @return the generated text, or null on failure.
     */
    fun generate(contextPtr: Long, systemPrompt: String, userPrompt: String, maxTokens: Int): String? =
        generateNative(contextPtr, systemPrompt, userPrompt, maxTokens)?.toString(Charsets.UTF_8)

    // Returns raw UTF-8 bytes: JNI's NewStringUTF expects modified UTF-8 and can abort on
    // 4-byte sequences (emoji), so the string conversion happens on the Kotlin side.
    private external fun generateNative(contextPtr: Long, systemPrompt: String, userPrompt: String, maxTokens: Int): ByteArray?

    /** Frees the native model + context. */
    external fun freeContext(contextPtr: Long)
}
