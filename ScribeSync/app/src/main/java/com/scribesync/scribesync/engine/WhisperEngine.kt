package com.scribesync.scribesync.engine

class WhisperEngine {
    data class Segment(
        val text: String,
        val t0: Long,
        val t1: Long,
        // True when the tdrz model detected a speaker turn right before this
        // segment started - not persistent speaker identity, just "someone
        // new started talking here".
        val isNewSpeaker: Boolean = false
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
     * Each call is a self-contained phrase (already isolated by silence-based
     * VAD upstream), so no cross-call context/prompt is needed.
     * @param contextPtr The pointer to the native context.
     * @param audioData The raw PCM audio data (16kHz, FloatArray).
     * @return A list of transcribed segments.
     */
    external fun transcribeSegments(contextPtr: Long, audioData: FloatArray): List<Segment>

    /**
     * Frees the native context to prevent memory leaks.
     * @param contextPtr The pointer to the native context.
     */
    external fun freeContext(contextPtr: Long)
}
