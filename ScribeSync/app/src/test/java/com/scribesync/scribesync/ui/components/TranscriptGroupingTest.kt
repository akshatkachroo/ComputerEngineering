package com.scribesync.scribesync.ui.components

import com.scribesync.scribesync.data.TranscriptEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptGroupingTest {

    @Test
    fun testGroupingWithTimeGap() {
        val meetingId = "test-meeting"
        val entries = listOf(
            TranscriptEntry(meetingId = meetingId, speakerLabel = "Speaker 1", text = "Hello", timestampMs = 1000),
            TranscriptEntry(meetingId = meetingId, speakerLabel = "Speaker 1", text = "World", timestampMs = 2000),
            // Gap of 11 seconds
            TranscriptEntry(meetingId = meetingId, speakerLabel = "Speaker 1", text = "Long pause", timestampMs = 13000),
            TranscriptEntry(meetingId = meetingId, speakerLabel = "Speaker 2", text = "Hi", timestampMs = 14000)
        )

        val groups = groupConsecutiveBySpeaker(entries)

        assertEquals(3, groups.size)
        assertEquals(2, groups[0].size) // Hello, World
        assertEquals(1, groups[1].size) // Long pause (split due to time gap)
        assertEquals(1, groups[2].size) // Hi (split due to speaker change)
        
        assertEquals("Hello", groups[0][0].text)
        assertEquals("World", groups[0][1].text)
        assertEquals("Long pause", groups[1][0].text)
        assertEquals("Hi", groups[2][0].text)
    }

    @Test
    fun testGroupingSameSpeakerSmallGap() {
        val meetingId = "test-meeting"
        val entries = listOf(
            TranscriptEntry(meetingId = meetingId, speakerLabel = "Speaker 1", text = "One", timestampMs = 1000),
            TranscriptEntry(meetingId = meetingId, speakerLabel = "Speaker 1", text = "Two", timestampMs = 5000)
        )

        val groups = groupConsecutiveBySpeaker(entries)

        assertEquals(1, groups.size)
        assertEquals(2, groups[0].size)
    }
}
