package com.scribesync.scribesync

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scribesync.scribesync.engine.LlamaEngine
import com.scribesync.scribesync.util.SummaryModelManager
import com.scribesync.scribesync.util.SummaryService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end proof that summarization is fully on-device.
 *
 * Precondition: the GGUF model has been pushed into the app's internal storage
 * (files/models/) beforehand, so this test performs NO network I/O — run it with
 * airplane mode enabled to demonstrate that inference is local.
 */
@RunWith(AndroidJUnit4::class)
class OnDeviceSummaryTest {

    companion object {
        private const val TAG = "OnDeviceSummaryTest"

        private val SAMPLE_TRANSCRIPT = """
            Speaker 1: Alright, let's get started. Today we need to settle the plan for the beta release of the mobile app.
            Speaker 1: The two open questions are the crash on the settings screen and whether we ship the new calendar view.
            Speaker 1: Priya, where are we on the settings crash?
            Speaker 1: The crash turned out to be a null pointer when the user has no saved profile. I have a fix in review, it should land tomorrow.
            Speaker 1: Great. Then I propose we include the fix in the beta and delay the calendar view to the next sprint, it still has layout problems on small screens.
            Speaker 1: Agreed, the calendar view is not ready. Marcus, can you take updating the release notes and the store listing screenshots?
            Speaker 1: Sure, I'll have the release notes drafted by Thursday and the screenshots by Friday.
            Speaker 1: One more thing, QA found that battery drain is higher on Android 14. We think it's the location polling interval.
            Speaker 1: Let's reduce the polling from every thirty seconds to every five minutes in the beta build and measure again.
            Speaker 1: Okay. So decisions: settings fix goes in, calendar view is postponed, polling drops to five minutes.
            Speaker 1: Priya owns the crash fix, Marcus owns release notes and screenshots, and I will set up the beta rollout at ten percent on Monday.
        """.trimIndent()
    }

    @Test
    fun summarizesSampleTranscriptFullyOnDevice() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelManager = SummaryModelManager(context)
        assertTrue(
            "Model must be pre-pushed to ${modelManager.modelFile.absolutePath} before running",
            modelManager.isModelReady()
        )

        val service = SummaryService(modelManager, LlamaEngine())
        val result = service.generateSummary(SAMPLE_TRANSCRIPT)

        Log.i(TAG, "Result type: ${result.javaClass.simpleName}")
        val summary = (result as? SummaryService.SummaryResult.Success)?.text
            ?: throw AssertionError("Expected Success but got $result")

        Log.i(TAG, "SUMMARY_OUTPUT_BEGIN\n$summary\nSUMMARY_OUTPUT_END")

        // A genuine condensation: much shorter than the transcript and not a verbatim echo.
        assertTrue("Summary should be non-trivial", summary.length > 80)
        assertTrue("Summary should condense (was ${summary.length} chars)", summary.length < SAMPLE_TRANSCRIPT.length)
        assertFalse(
            "Summary must not reproduce transcript lines verbatim",
            summary.contains("Alright, let's get started. Today we need to settle the plan")
        )
    }

    @Test
    fun emptyTranscriptSkipsInference() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val service = SummaryService(SummaryModelManager(context), LlamaEngine())
        val result = service.generateSummary("uh")
        assertTrue("Expected EmptyTranscript but got $result", result is SummaryService.SummaryResult.EmptyTranscript)
    }
}
