package com.mfaizanse.batteryinfoserver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.LOCKED_BOOT_COMPLETED"
        ) {
            return
        }

        Log.i(TAG, "Boot completed ($action), starting BatteryInfoService")

        val serviceIntent = Intent(context, BatteryInfoService::class.java).apply {
            this.action = BatteryInfoService.ACTION_START
        }
        // startForegroundService() is always correct here because minSdk = 26
        context.startForegroundService(serviceIntent)
    }
}
