package com.example.mockroute

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.mockroute.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        requestPerms()

        b.btnStart.setOnClickListener { startRoute() }
        b.btnStop.setOnClickListener {
            stopService(Intent(this, MockService::class.java))
            b.txtStatus.text = getString(R.string.stopped)
        }
    }

    private fun requestPerms() {
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty())
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1)
    }

    private fun startRoute() {
        val startAddr = b.edStart.text.toString().trim()
        val endAddr = b.edEnd.text.toString().trim()
        val speed = b.edSpeed.text.toString().toDoubleOrNull() ?: 40.0
        val loop = b.chkLoop.isChecked

        if (startAddr.isEmpty() || endAddr.isEmpty()) {
            toast("Enter both addresses"); return
        }

        b.txtStatus.text = getString(R.string.geocoding)

        lifecycleScope.launch {
            val s = geocode(startAddr)
            val e = geocode(endAddr)
            if (s == null || e == null) {
                b.txtStatus.text = getString(R.string.geo_fail); return@launch
            }
            val i = Intent(this@MainActivity, MockService::class.java).apply {
                putExtra(MockService.EX_SLAT, s.first)
                putExtra(MockService.EX_SLNG, s.second)
                putExtra(MockService.EX_ELAT, e.first)
                putExtra(MockService.EX_ELNG, e.second)
                putExtra(MockService.EX_SPEED, speed)
                putExtra(MockService.EX_LOOP, loop)
            }
            ContextCompat.startForegroundService(this@MainActivity, i)
            b.txtStatus.text = getString(
                R.string.running_fmt,
                s.first, s.second, e.first, e.second
            )
        }
    }

    private suspend fun geocode(addr: String): Pair<Double, Double>? =
        withContext(Dispatchers.IO) {
            runCatching {
                val g = Geocoder(this@MainActivity, Locale.getDefault())
                @Suppress("DEPRECATION")
                val res = g.getFromLocationName(addr, 1)
                res?.firstOrNull()?.let { it.latitude to it.longitude }
            }.getOrNull()
        }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
