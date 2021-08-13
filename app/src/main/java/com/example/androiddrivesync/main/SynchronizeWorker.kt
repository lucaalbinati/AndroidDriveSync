package com.example.androiddrivesync.main

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.example.androiddrivesync.drive.GoogleDriveClient
import com.example.androiddrivesync.utility.Utility
import java.io.File
import java.time.Duration
import java.util.*
import java.util.concurrent.ExecutionException

class SynchronizeWorker(appContext: Context, workerParams: WorkerParameters): CoroutineWorker(appContext, workerParams) {
    companion object {
        const val FILES_TO_SYNCHRONIZE_KEY = "FILES_TO_SYNCHRONIZE_KEY"
        const val File = "FILE"
        const val SYNC_STATUS = "SYNC_STATUS"
        const val UNIQUE_WORK_NAME = "synchronizeWork"

        fun setupPeriodicWorkRequest(context: Context, filesToSynchronize: List<String>): UUID {
            val workRequest = PeriodicWorkRequestBuilder<SynchronizeWorker>(Duration.ofMinutes(15))
                .setInputData(workDataOf(
                    FILES_TO_SYNCHRONIZE_KEY to filesToSynchronize.toTypedArray()
                ))
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, workRequest)
            return workRequest.id
        }
    }

    override suspend fun doWork(): Result {
        Log.i("synchronizeWorker", "start doWork()")

        if (!inputData.keyValueMap.containsKey(FILES_TO_SYNCHRONIZE_KEY)) {
            Log.i("synchronizeWorker", "missing '$FILES_TO_SYNCHRONIZE_KEY' in inputData")
            throw Exception("Key-value pair for '$FILES_TO_SYNCHRONIZE_KEY' not found")
        }

        val builder = SynchronizeNotification.getInitialBuilder(applicationContext)
        val notificationId = builder.hashCode()
        val notificationManagerCompat = NotificationManagerCompat.from(applicationContext)

        // Issue initial notification
        SynchronizeNotification.initialProgress(notificationManagerCompat, notificationId, builder)

        try {
            // Get the list of files to synchronize
            val filesToSynchronize: List<String> = inputData.getStringArray(FILES_TO_SYNCHRONIZE_KEY)!!.toList()

            // Get a GoogleDriveClient instance
            val googleDriveClient = GoogleDriveClient.setupGoogleDriveClient(applicationContext)

            // Get the actions needed for each file, along with some upload size information
            val fileSyncActions = googleDriveClient.getFileSyncActions(filesToSynchronize)
            val totalSize = googleDriveClient.getFilesToUploadSize(fileSyncActions).toInt()
            val sizeUnit = Utility.getSizeUnit(totalSize)

            // Synchronize
            doSynchronize(notificationManagerCompat, notificationId, builder, googleDriveClient, fileSyncActions, totalSize, sizeUnit)

            // Update notification
            SynchronizeNotification.updateProgress(applicationContext, notificationManagerCompat, notificationId, builder, sizeUnit, totalSize, totalSize)
        } catch (e: ExecutionException) {
            Log.i("synchronizeWorker", "caught exception ${e.message}")
        } finally {
            // Clear notification
            SynchronizeNotification.cancel(notificationManagerCompat, notificationId)
        }

        Log.i("synchronizeWorker", "finish doWork()")

        return Result.success()
    }

    private suspend fun doSynchronize(notificationManagerCompat: NotificationManagerCompat, notificationId: Int, builder: NotificationCompat.Builder, googleDriveClient: GoogleDriveClient, fileSyncActions: List<Utility.FileSyncAction>, totalSize: Int, sizeUnit: String) {
        SynchronizeNotification.updateProgress(applicationContext, notificationManagerCompat, notificationId, builder, sizeUnit, totalSize, 1)
        var currentSize = 0

        googleDriveClient.synchronise(fileSyncActions) { localRelativeFilepath, syncStatus ->
            if (localRelativeFilepath != null) {
                // Update the sync status on the RecyclerView element
                setProgressAsync(workDataOf(File to localRelativeFilepath, SYNC_STATUS to syncStatus.name))

                // Update notification progress
                currentSize += File(GoogleDriveClient.BASE_STORAGE_DIR, localRelativeFilepath).length().toInt()
                SynchronizeNotification.updateProgress(applicationContext, notificationManagerCompat, notificationId, builder, sizeUnit, totalSize, currentSize)
            }
        }
    }

}