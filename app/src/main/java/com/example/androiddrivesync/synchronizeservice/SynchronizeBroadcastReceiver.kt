package com.example.androiddrivesync.synchronizeservice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class SynchronizeBroadcastReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "SynchronizeBroadcastReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null) {
            Log.i(TAG, "received null intent")
            return
        }

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.i(TAG, "received ${intent.action} broadcast: $intent")
                SynchronizeWorker.setupPeriodicWorkRequest(context!!)
            }
            else -> Log.i(TAG, "received ${intent.action} broadcast:")
        }
    }

}