package com.scribesync.scribesync.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptRepositoryTest {
    @Test
    fun `search pattern escapes SQLite wildcard characters`() {
        assertEquals("%budget\\_100\\%\\\\final%", "budget_100%\\final".toSqlLikePattern())
    }

    @Test
    fun `search pattern preserves ordinary text`() {
        assertEquals("%design review%", "design review".toSqlLikePattern())
    }
}
