package com.example.mockroute

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlin.math.*

/**
 * Foreground service that walks a straight-line path from start to end,
 * pushing a fresh mock location every tick so other apps see movement.
 */
class MockService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var engine: MockEngine

    companion object {
        const val EX_SLAT = "slat"; const val EX_SLNG = "slng"
        const val EX_ELAT = "elat"; const val EX_ELNG = "elng"
        const val EX_SPEED = "speed"          // km/h
        const val EX_LOOP = "loop"
        const val CH = "mockroute"
        @Volatile var running = false; private set
        // live position, read by the UI for status
        @Volatile var curLat = 0.0
        @Volatile var curLng = 0.0
    }

    override fun onCreate() {
        super.onCreate()
        engine = MockEngine(this)
        createChannel()
    }

    override fun onStartCommand(i: Intent?, flags: Int, id: Int): Int {
        startForeground(1, buildNotif("Mock route running"))
        val sLat = i?.getDoubleExtra(EX_SLAT, 0.0) ?: 0.0
        val sLng = i?.getDoubleExtra(EX_SLNG, 0.0) ?: 0.0
        val eLat = i?.getDoubleExtra(EX_ELAT, 0.0) ?: 0.0
        val eLng = i?.getDoubleExtra(EX_ELNG, 0.0) ?: 0.0
        val speed = i?.getDoubleExtra(EX_SPEED, 40.0) ?: 40.0
        val loop = i?.getBooleanExtra(EX_LOOP, false) ?: false

        engine.start()
        running = true
        scope.launch { drive(sLat, sLng, eLat, eLng, speed, loop) }
        return START_NOT_STICKY
    }

    private suspend fun drive(
        sLat: Double, sLng: Double, eLat: Double, eLng: Double,
        speedKmh: Double, loop: Boolean
    ) {
        val speedMs = (speedKmh / 3.6)
        val totalM = haversine(sLat, sLng, eLat, eLng)
        // one location update per second
        val steps = max(1, (totalM / max(speedMs, 0.1)).roundToInt())
        val bearing = bearingBetween(sLat, sLng, eLat, eLng)

        do {
            for (n in 0..steps) {
                if (!running) break
                val f = n.toDouble() / steps
                val lat = sLat + (eLat - sLat) * f
                val lng = sLng + (eLng - sLng) * f
                curLat = lat; curLng = lng
                engine.push(lat, lng, speedMs.toFloat(), bearing)
                delay(1000)
            }
        } while (loop && running)

        stopSelf()
    }

    private fun haversine(la1: Double, lo1: Double, la2: Double, lo2: Double): Double {
        val r = 6371000.0
        val dLa = Math.toRadians(la2 - la1)
        val dLo = Math.toRadians(lo2 - lo1)
        val a = sin(dLa / 2).pow(2) +
                cos(Math.toRadians(la1)) * cos(Math.toRadians(la2)) *
                sin(dLo / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun bearingBetween(la1: Double, lo1: Double, la2: Double, lo2: Double): Float {
        val y = sin(Math.toRadians(lo2 - lo1)) * cos(Math.toRadians(la2))
        val x = cos(Math.toRadians(la1)) * sin(Math.toRadians(la2)) -
                sin(Math.toRadians(la1)) * cos(Math.toRadians(la2)) *
                cos(Math.toRadians(lo2 - lo1))
        return ((Math.toDegrees(atan2(y, x)) + 360) % 360).toFloat()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CH, "Mock Route", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    private fun buildNotif(text: String): Notification =
        NotificationCompat.Builder(this, CH)
            .setContentTitle("MockRoute")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

    override fun onDestroy() {
        running = false
        engine.stop()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
