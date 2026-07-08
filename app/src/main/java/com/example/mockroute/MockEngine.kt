package com.example.mockroute

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock

/**
 * Wraps Android's test/mock location provider.
 * Requires the app to be selected under:
 * Developer Options -> Select mock location app.
 */
class MockEngine(context: Context) {

    private val lm =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val providers = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER
    )

    fun start() {
        providers.forEach { p ->
            runCatching {
                lm.addTestProvider(
                    p,
                    false, false, false, false,
                    true, true, true,
                    android.location.Criteria.POWER_LOW,
                    android.location.Criteria.ACCURACY_FINE
                )
                lm.setTestProviderEnabled(p, true)
            }
        }
    }

    fun push(lat: Double, lng: Double, speedMs: Float, bearing: Float) {
        providers.forEach { p ->
            val loc = Location(p).apply {
                latitude = lat
                longitude = lng
                accuracy = 1f
                speed = speedMs
                this.bearing = bearing
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    speedAccuracyMetersPerSecond = 0.1f
                    bearingAccuracyDegrees = 0.1f
                    verticalAccuracyMeters = 0.1f
                }
            }
            runCatching { lm.setTestProviderLocation(p, loc) }
        }
    }

    fun stop() {
        providers.forEach { p ->
            runCatching {
                lm.setTestProviderEnabled(p, false)
                lm.removeTestProvider(p)
            }
        }
    }
}
