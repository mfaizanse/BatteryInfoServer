package com.mfaizanse.batteryinfoserver

import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mfaizanse.batteryinfoserver.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isServerRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Restore across configuration changes, or detect if auto-started at boot
        isServerRunning = savedInstanceState?.getBoolean(KEY_SERVER_RUNNING) ?: isServiceRunning()
        updateUi()

        binding.btnToggleServer.setOnClickListener {
            if (isServerRunning) stopServer() else startServer()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_SERVER_RUNNING, isServerRunning)
    }

    private fun startServer() {
        val intent = Intent(this, BatteryInfoService::class.java).apply {
            action = BatteryInfoService.ACTION_START
        }
        startForegroundService(intent)
        isServerRunning = true
        updateUi()
    }

    private fun stopServer() {
        val intent = Intent(this, BatteryInfoService::class.java).apply {
            action = BatteryInfoService.ACTION_STOP
        }
        startService(intent)
        isServerRunning = false
        updateUi()
    }

    private fun updateUi() {
        if (isServerRunning) {
            binding.btnToggleServer.text = getString(R.string.stop_server)
            binding.tvStatus.text = getString(R.string.status_running)
        } else {
            binding.btnToggleServer.text = getString(R.string.start_server)
            binding.tvStatus.text = getString(R.string.status_stopped)
        }
    }

    /**
     * Checks whether BatteryInfoService is currently running.
     * Used on cold start to sync the button state when the service was auto-started at boot.
     */
    @Suppress("DEPRECATION")
    private fun isServiceRunning(): Boolean {
        val am = getSystemService(ActivityManager::class.java)
        return am.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == BatteryInfoService::class.java.name
        }
    }

    companion object {
        private const val KEY_SERVER_RUNNING = "key_server_running"
    }
}
