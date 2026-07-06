package com.scribesync.scribesync.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * SummaryModelManager - one-time download and integrity verification of the on-device
 * summarization model.
 *
 * Privacy contract: the ONLY network traffic in the entire summary path is this download
 * of public model weights. No meeting content (audio, transcript, or summary) is ever
 * transmitted; inference runs fully on-device via LlamaEngine.
 *
 * The model is stored in app-internal storage (filesDir/models) so it persists across
 * restarts and is never re-downloaded once verified. Interrupted downloads resume from
 * the partial file via HTTP Range requests, and the SHA-256 of the completed file is
 * checked against a pinned digest before first use.
 */
class SummaryModelManager(private val context: Context) {

    sealed class DownloadState {
        object Idle : DownloadState()
        data class Downloading(val percent: Int) : DownloadState()
        object Verifying : DownloadState()
        object Ready : DownloadState()
        data class Failed(val reason: String) : DownloadState()
    }

    companion object {
        private const val TAG = "SummaryModelManager"

        // Qwen2.5-1.5B-Instruct, Q4_K_M quantization (~1.04 GiB): small enough for
        // mid-range phones, strong instruct model for summarization. Official GGUF
        // published by Qwen; size + SHA-256 pinned from the Hugging Face LFS metadata.
        const val MODEL_FILE_NAME = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
        private const val MODEL_URL =
            "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf"
        const val MODEL_SIZE_BYTES = 1_117_320_736L
        private const val MODEL_SHA256 =
            "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e"

        private const val MAX_REDIRECTS = 5
        private const val BUFFER_SIZE = 256 * 1024
    }

    private val modelDir: File get() = File(context.filesDir, "models")
    val modelFile: File get() = File(modelDir, MODEL_FILE_NAME)
    private val partFile: File get() = File(modelDir, "$MODEL_FILE_NAME.part")

    private val mutex = Mutex()

    private val _state = MutableStateFlow<DownloadState>(
        if (isModelReady()) DownloadState.Ready else DownloadState.Idle
    )
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    /** True when a verified model is present on disk; checked without any network use. */
    fun isModelReady(): Boolean =
        modelFile.exists() && modelFile.length() == MODEL_SIZE_BYTES

    /**
     * Ensures the model is present and verified, downloading (or resuming) if needed.
     * Safe to call concurrently; only one download runs at a time.
     */
    suspend fun ensureModel(): Result<File> = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (isModelReady()) {
                _state.value = DownloadState.Ready
                return@withContext Result.success(modelFile)
            }
            try {
                modelDir.mkdirs()
                download()
                verifyAndCommit()
                _state.value = DownloadState.Ready
                Log.i(TAG, "Summary model ready at ${modelFile.absolutePath}")
                Result.success(modelFile)
            } catch (e: Exception) {
                val reason = e.message ?: e.javaClass.simpleName
                Log.e(TAG, "Model download failed: $reason", e)
                _state.value = DownloadState.Failed(reason)
                Result.failure(e)
            }
        }
    }

    private fun download() {
        var alreadyHave = partFile.length()
        Log.i(TAG, "Starting model download (resuming from $alreadyHave of $MODEL_SIZE_BYTES bytes)")
        _state.value = DownloadState.Downloading((alreadyHave * 100 / MODEL_SIZE_BYTES).toInt())

        val connection = openWithRedirects(MODEL_URL, alreadyHave)
        try {
            val append = when (connection.responseCode) {
                HttpURLConnection.HTTP_PARTIAL -> true
                HttpURLConnection.HTTP_OK -> {
                    // Server ignored the Range request - start over.
                    if (alreadyHave > 0) Log.w(TAG, "Server ignored Range request; restarting download")
                    partFile.delete()
                    alreadyHave = 0
                    false
                }
                else -> throw IOException("Unexpected HTTP ${connection.responseCode} downloading model")
            }

            var downloaded = alreadyHave
            var lastPercent = -1
            connection.inputStream.use { input ->
                FileOutputStream(partFile, append).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        downloaded += n
                        val percent = (downloaded * 100 / MODEL_SIZE_BYTES).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            _state.value = DownloadState.Downloading(percent)
                        }
                    }
                }
            }
            if (downloaded != MODEL_SIZE_BYTES) {
                throw IOException("Incomplete download: $downloaded of $MODEL_SIZE_BYTES bytes (will resume on retry)")
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Follows redirects manually so the Range header reliably reaches the final host
     * (Hugging Face redirects to a CDN, and HttpURLConnection's automatic redirect
     * handling is not guaranteed to preserve request headers).
     */
    private fun openWithRedirects(startUrl: String, resumeFrom: Long): HttpURLConnection {
        var url = startUrl
        repeat(MAX_REDIRECTS) {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                if (resumeFrom > 0) setRequestProperty("Range", "bytes=$resumeFrom-")
            }
            when (connection.responseCode) {
                in 300..399 -> {
                    val location = connection.getHeaderField("Location")
                        ?: throw IOException("Redirect without Location header")
                    connection.disconnect()
                    url = URL(URL(url), location).toString()
                }
                else -> return connection
            }
        }
        throw IOException("Too many redirects downloading model")
    }

    private fun verifyAndCommit() {
        _state.value = DownloadState.Verifying
        val digest = MessageDigest.getInstance("SHA-256")
        partFile.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        if (sha != MODEL_SHA256) {
            partFile.delete()
            throw IOException("Model checksum mismatch (expected $MODEL_SHA256, got $sha); deleted corrupt download")
        }
        if (!partFile.renameTo(modelFile)) {
            throw IOException("Failed to move verified model into place")
        }
    }
}
