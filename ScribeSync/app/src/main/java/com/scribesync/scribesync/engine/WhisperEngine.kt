package com.scribesync.scribesync.engine

class WhisperEngine {
    data class Segment(
        val text: String,
        val t0: Long,
        val t1: Long,
        val speakerId: Int = -1
    )

    companion object {
        init {
            System.loadLibrary("scribesync")
        }
    }

    /**
     * Initializes the Whisper context with the given model path.
     * @return A pointer to the native context (as a Long).
     */
    external fun initContext(modelPath: String): Long

    /**
     * Transcribes the given audio data and returns low-latency segment streams.
     * @param contextPtr The pointer to the native context.
     * @param audioData The raw PCM audio data (16kHz, FloatArray).
     * @param historyPrompt Previous transcription text to provide context (optional).
     * @return A list of transcribed segments.
     */
    external fun transcribeSegments(contextPtr: Long, audioData: FloatArray, historyPrompt: String?): List<Segment>

    /**
     * Frees the native context to prevent memory leaks.
     * @param contextPtr The pointer to the native context.
     */
    external fun freeContext(contextPtr: Long)
}
