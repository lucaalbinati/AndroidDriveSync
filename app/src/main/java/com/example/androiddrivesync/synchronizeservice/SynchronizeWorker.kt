package com.example.androiddrivesync.synchronizeservice

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.example.androiddrivesync.drive.GoogleDriveClient
import com.example.androiddrivesync.utils.LocalFilesToSynchronizeHandler
import com.example.androiddrivesync.utils.Utility
import java.io.File
import java.util.*
import java.util.concurrent.ExecutionException

class SynchronizeWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    companion object {
        private const val TAG = "SynchronizeWorker"
        const val File = "FILE"
        const val SYNC_STATUS = "SYNC_STATUS"
        private const val UNIQUE_WORK_NAME = "synchronizeWork"

        fun setupPeriodicWorkRequest(context: Context): UUID? {
            Log.i(TAG, "enqueuing PeriodicWorkRequest")
            val periodicity = SynchronizeSettingsSharedPreferencesHelper.getPeriodicity(context)
            if (!periodicity.isRecurrent()) {
                Log.i(TAG, "current periodicity $periodicity is not recurrent, so we are not setting up a PeriodicWorkRequest")
                return null
            }
            val workRequest = PeriodicWorkRequestBuilder<SynchronizeWorker>(periodicity.convertToDuration())
                .setConstraints(getWorkRequestConstraints())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, workRequest)
            return workRequest.id
        }

        fun setupSingleWorkRequest(context: Context): UUID {
            Log.i(TAG, "enqueuing single WorkRequest")
            val workRequest = OneTimeWorkRequestBuilder<SynchronizeWorker>()
                .setConstraints(getWorkRequestConstraints())
                .build()
            WorkManager.getInstance(context).enqueue(workRequest)
            return workRequest.id
        }

        private fun getWorkRequestConstraints(): Constraints {
            return Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .build()
        }

        suspend fun ifNotCurrentlyRunning(context: Context, callback: () -> Unit) {
            val workInfos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(UNIQUE_WORK_NAME).await()
            if (workInfos.size == 0) {
                callback()
            }
        }

        suspend fun updatePeriodicity(context: Context) {
            // FIXME insert check to see if the last synchronization happened less than 'periodicity' ago
            val workInfos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(UNIQUE_WORK_NAME).await()
            when (workInfos.size) {
                0 -> Log.i(TAG, "no periodic work request were set up")
                1 -> {
                    Log.i(TAG, "found 1 periodic work request")
                    WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME).await()
                    Log.i(TAG, "cancelled periodic work request")
                    val workRequestId = setupPeriodicWorkRequest(context)
                    if (workRequestId != null) {
                        Log.i(TAG, "setup periodic work request (id: $workRequestId) with new periodicity")
                    }
                }
                else -> {
                    throw Exception("Did not expect more than 1 periodic work request with unique work name '$UNIQUE_WORK_NAME'. Instead found ${workInfos.size}")
                }
            }
        }
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "start doWork()")

        val builder = SynchronizeNotification.getInitialBuilder(applicationContext)
        val notificationId = builder.hashCode()
        val notificationManagerCompat = NotificationManagerCompat.from(applicationContext)

        // Issue initial notification
        Log.i(TAG, "sending initial notification")
        SynchronizeNotification.initialProgress(notificationManagerCompat, notificationId, builder)

        try {
            // Get the list of files to synchronize
            val filesToSynchronize: List<String> = LocalFilesToSynchronizeHandler.getLocalFilesToSynchronize(applicationContext)
            Log.i(TAG, "retrieved the files to synchronize: $filesToSynchronize")

            // Get a GoogleDriveClient instance
            val googleDriveClient = GoogleDriveClient.setupGoogleDriveClient(applicationContext)

            // Get the actions needed for each file, along with some upload size information
            val fileSyncActions = googleDriveClient.getFileSyncActions(filesToSynchronize)
            val totalSize = googleDriveClient.getFilesToUploadSize(fileSyncActions).toInt()
            val sizeUnit = Utility.getSizeUnit(totalSize)

            // Synchronize
            Log.i(TAG, "starting synchronization")
            doSynchronize(notificationManagerCompat, notificationId, builder, googleDriveClient, fileSyncActions, totalSize, sizeUnit)
            Log.i(TAG, "finished synchronization")

            // Update notification
            Log.i(TAG, "sending final notification")
            SynchronizeNotification.updateProgress(applicationContext, notificationManagerCompat, notificationId, builder, sizeUnit, totalSize, totalSize)
        } catch (e: ExecutionException) {
            Log.i(TAG, "caught exception ${e.message}")
        } finally {
            // Clear notification
            Log.i(TAG, "clearing notification")
            SynchronizeNotification.cancel(notificationManagerCompat, notificationId)
        }

        Log.i(TAG, "finished doWork()")
        return Result.success()
    }

    private suspend fun doSynchronize(
        notificationManagerCompat: NotificationManagerCompat,
        notificationId: Int,
        builder: NotificationCompat.Builder,
        googleDriveClient: GoogleDriveClient,
        fileSyncActions: List<Utility.FileSyncAction>,
        totalSize: Int,
        sizeUnit: String
    ) {
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