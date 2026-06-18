package com.scribesync.scribesync.data

import java.util.Date

/**
 * Structurally binds system clock timestamps alongside geospatial data
 * into the active transcript's metadata payload.
 */
data class TranscriptMetadata(
    val startTime: Date,
    val latitude: Double?,
    val longitude: Double?,
    val deviceModel: String = android.os.Build.MODEL
)
