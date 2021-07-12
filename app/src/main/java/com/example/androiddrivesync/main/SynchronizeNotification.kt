package com.example.androiddrivesync.main

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.androiddrivesync.R
import com.example.androiddrivesync.utility.Utility

class SynchronizeNotification {
    companion object {
        private const val SYNCHRONIZING_CHANNEL_ID = "SYNCHRONIZING_CHANNEL_ID"

        fun createChannel(context: Context) {
            // Create the NotificationChannel
            val name = context.getString(R.string.synchronization_channel_name)
            val descriptionText = context.getString(R.string.synchronization_channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(SYNCHRONIZING_CHANNEL_ID, name, importance).apply { description = descriptionText }

            // Register the channel with the system
            val notificationManager: NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        fun getInitialBuilder(context: Context): NotificationCompat.Builder {
            return NotificationCompat.Builder(context, SYNCHRONIZING_CHANNEL_ID)
                .setSmallIcon(R.raw.synchronizing)
                .setContentTitle(context.resources.getString(R.string.synchronization_synchronizing_title))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setProgress(0, 0, true)
                .setColor(context.resources.getColor(R.color.ic_launcher_background, context.theme))
                .setOngoing(true)
        }

        fun initialProgress(notificationManagerCompat: NotificationManagerCompat, notificationId: Int, builder: NotificationCompat.Builder) {
            builder.setProgress(0, 0, true)
            notificationManagerCompat.notify(notificationId, builder.build())
        }

        fun updateProgress(context: Context, notificationManagerCompat: NotificationManagerCompat, notificationId: Int, builder: NotificationCompat.Builder, sizeUnit: String, totalBytes: Int, currentBytes: Int) {
            val totalBytesInSizeUnit = Utility.convertToSizeUnit(totalBytes, sizeUnit)
            val currentBytesInSizeUnit = Utility.convertToSizeUnit(currentBytes, sizeUnit)

            builder.setContentText(context.resources.getString(R.string.synchronization_synchronizing_uploading, currentBytesInSizeUnit, totalBytesInSizeUnit, sizeUnit))
            builder.setProgress(totalBytes,
                Integer.max(currentBytes, (0.05 * totalBytes).toInt()), false)
            notificationManagerCompat.notify(notificationId, builder.build())
        }

        fun cancel(notificationManagerCompat: NotificationManagerCompat, notificationId: Int) {
            notificationManagerCompat.cancel(notificationId)
        }
    }
}