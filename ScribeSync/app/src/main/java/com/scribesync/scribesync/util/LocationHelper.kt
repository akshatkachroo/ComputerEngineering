package com.scribesync.scribesync.util

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class LocationHelper(context: Context) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Pair<Double, Double>? {
        Log.d("LocationHelper", "getCurrentLocation requested")
        return try {
            val location = withTimeoutOrNull(5000) {
                Log.d("LocationHelper", "Attempting to get current location (High Accuracy)...")
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    null
                ).await()
            } ?: run {
                Log.d("LocationHelper", "Current location timeout, trying lastLocation...")
                fusedLocationClient.lastLocation.await()
            }
            
            if (location != null) {
                Log.d("LocationHelper", "Location found: ${location.latitude}, ${location.longitude}")
                Pair(location.latitude, location.longitude)
            } else {
                Log.e("LocationHelper", "Location is still null after all attempts")
                null
            }
        } catch (e: Exception) {
            Log.e("LocationHelper", "Error getting location", e)
            null
        }
    }
}
