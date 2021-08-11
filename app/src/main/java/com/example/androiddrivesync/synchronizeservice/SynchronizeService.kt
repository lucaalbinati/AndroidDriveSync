package com.example.androiddrivesync.synchronizeservice

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import com.example.androiddrivesync.R
import com.example.androiddrivesync.main.SynchronizeWorker
import com.example.androiddrivesync.main.SynchronizeWorker.Companion.UNIQUE_WORK_NAME
import com.example.androiddrivesync.utility.LocalFilesToSynchronizeHandler
import java.util.*

class SynchronizeService: Service() {
    companion object {
        private const val TAG = "SynchronizeService"
    }

    override fun onBind(intent: Intent?): IBinder? {
        TODO("Not yet implemented")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand")

        val builder = NotificationCompat.Builder(this, "SYNCHRONIZING_CHANNEL_ID")
            .setSmallIcon(R.raw.synchronizing)
            .setContentTitle("Synchronize Service")
            .setContentText("Service started (${Calendar.getInstance().time.time})")
        val notificationManagerCompat = NotificationManagerCompat.from(applicationContext)
        notificationManagerCompat.notify(builder.hashCode(), builder.build())

        val filesToSynchronize = LocalFilesToSynchronizeHandler.getLocalFilesToSynchronize(this)
        SynchronizeWorker.setupPeriodicWorkRequest(this, filesToSynchronize)

        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy")
        WorkManager.getInstance(this).cancelUniqueWork(UNIQUE_WORK_NAME)
        Log.i(TAG, "cancelled unique work '$UNIQUE_WORK_NAME'")

        val builder = NotificationCompat.Builder(this, "SYNCHRONIZING_CHANNEL_ID")
            .setSmallIcon(R.raw.synchronizing)
            .setContentTitle("Synchronize Service")
            .setContentText("Service destroyed (${Calendar.getInstance().time.time})")
        val notificationManagerCompat = NotificationManagerCompat.from(applicationContext)
        notificationManagerCompat.notify(builder.hashCode(), builder.build())
    }

}