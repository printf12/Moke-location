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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.*

/**
 * Foreground service that follows the REAL road route from start to end
 * (via the free OSRM routing server), pushing a fresh mock location every
 * second so other apps see the phone driving along the streets.
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
        // Get the real road path (list of lat/lng points). Falls back to a
        // straight line if the routing server can't be reached.
        val path = fetchRoute(sLat, sLng, eLat, eLng)
            ?: listOf(sLat to sLng, eLat to eLng)

        val speedMs = (speedKmh / 3.6).coerceAtLeast(0.1)

        do {
            // walk each road segment
            for (seg in 0 until path.size - 1) {
                if (!running) break
                val (aLat, aLng) = path[seg]
                val (bLat, bLng) = path[seg + 1]
                val segM = haversine(aLat, aLng, bLat, bLng)
                val bearing = bearingBetween(aLat, aLng, bLat, bLng)
                val steps = max(1, (segM / speedMs).roundToInt())

                for (n in 0..steps) {
                    if (!running) break
                    val f = n.toDouble() / steps
                    val lat = aLat + (bLat - aLat) * f
                    val lng = aLng + (bLng - aLng) * f
                    curLat = lat; curLng = lng
                    engine.push(lat, lng, speedMs.toFloat(), bearing)
                    delay(1000)
                }
            }
        } while (loop && running)

        stopSelf()
    }

    /** Ask the free OSRM server for the driving route. Returns road points or null. */
    private suspend fun fetchRoute(
        sLat: Double, sLng: Double, eLat: Double, eLng: Double
    ): List<Pair<Double, Double>>? = withContext(Dispatchers.IO) {
        try {
            val url = URL(
                "https://router.project-osrm.org/route/v1/driving/" +
                        "$sLng,$sLat;$eLng,$eLat?overview=full&geometries=geojson"
            )
            val con = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 15000
                requestMethod = "GET"
            }
            val text = con.inputStream.bufferedReader().use { it.readText() }
            con.disconnect()

            val routes = JSONObject(text).optJSONArray("routes") ?: return@withContext null
            if (routes.length() == 0) return@withContext null
            val coords = routes.getJSONObject(0)
                .getJSONObject("geometry")
                .getJSONArray("coordinates")

            val out = ArrayList<Pair<Double, Double>>(coords.length())
            for (k in 0 until coords.length()) {
                val c = coords.getJSONArray(k)   // [lon, lat]
                out.add(c.getDouble(1) to c.getDouble(0))
            }
            if (out.size >= 2) out else null
        } catch (e: Exception) {
            null   // fall back to straight line
        }
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
