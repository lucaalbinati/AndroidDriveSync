package com.example.androiddrivesync.main

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.example.androiddrivesync.drive.GoogleDriveClient
import com.example.androiddrivesync.utility.Utility
import com.google.android.gms.auth.api.signin.GoogleSignIn
import java.io.File
import java.util.*

class SynchronizeWorker(appContext: Context, workerParams: WorkerParameters): CoroutineWorker(appContext, workerParams) {
    companion object {
        const val FILES_TO_SYNCHRONIZE_KEY = "FILES_TO_SYNCHRONIZE_KEY"
        const val File = "FILE"
        const val SyncStatus = "SYNC_STATUS"

        fun enqueueWorkRequest(context: Context, filesToSynchronize: List<String>): UUID {
            val workRequest = OneTimeWorkRequestBuilder<SynchronizeWorker>()
                .setInputData(workDataOf(
                    FILES_TO_SYNCHRONIZE_KEY to filesToSynchronize.toTypedArray()
                ))
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
            return workRequest.id
        }

        fun observeWorkRequest(context: AppCompatActivity, workRequestId: UUID, onChange: (WorkInfo.State, Data) -> Unit) {
            WorkManager.getInstance(context)
                .getWorkInfoByIdLiveData(workRequestId)
                .observe(context, { workInfo: WorkInfo? ->
                    if (workInfo != null) {
                        onChange(workInfo.state, workInfo.progress)
                    }
                })
        }
    }

    override suspend fun doWork(): Result {
        if (!inputData.keyValueMap.containsKey(FILES_TO_SYNCHRONIZE_KEY)) {
            throw Exception("Key-value pair for '$FILES_TO_SYNCHRONIZE_KEY' not found")
        }

        val builder = SynchronizeNotification.getInitialBuilder(applicationContext)
        val notificationId = builder.hashCode()
        val notificationManagerCompat = NotificationManagerCompat.from(applicationContext)


        // Issue initial notification
        SynchronizeNotification.initialProgress(notificationManagerCompat, notificationId, builder)

        // Get the list of files to synchronize
        val filesToSynchronize: List<String> = inputData.getStringArray(FILES_TO_SYNCHRONIZE_KEY)!!.toList()

        // Get a GoogleDriveClient instance
        val googleDriveClient = getGoogleDriveClient()

        // Get the actions needed for each file, along with some upload size information
        val fileSyncActions = googleDriveClient.getFileSyncActions(filesToSynchronize)
        val totalSize = googleDriveClient.getFilesToUploadSize(fileSyncActions).toInt()
        val sizeUnit = Utility.getSizeUnit(totalSize)

        // Synchronize
        doSynchronize(notificationManagerCompat, notificationId, builder, googleDriveClient, fileSyncActions, totalSize, sizeUnit)

        // Update notification
        SynchronizeNotification.updateProgress(applicationContext, notificationManagerCompat, notificationId, builder, sizeUnit, totalSize, totalSize)

        // Clear notification
        SynchronizeNotification.cancel(notificationManagerCompat, notificationId)

        return Result.success()
    }

    private fun getGoogleDriveClient(): GoogleDriveClient {
        // Get Google account and server authentication code
        val account = GoogleSignIn.getLastSignedInAccount(applicationContext)
        val serverAuthCode = account!!.serverAuthCode!!

        // Create GoogleDriveClient
        return GoogleDriveClient(applicationContext, serverAuthCode)
    }

    private suspend fun doSynchronize(notificationManagerCompat: NotificationManagerCompat, notificationId: Int, builder: NotificationCompat.Builder, googleDriveClient: GoogleDriveClient, fileSyncActions: List<Utility.FileSyncAction>, totalSize: Int, sizeUnit: String) {
        SynchronizeNotification.updateProgress(applicationContext, notificationManagerCompat, notificationId, builder, sizeUnit, totalSize, 1)
        var currentSize = 0

        googleDriveClient.synchronise(fileSyncActions) { localRelativeFilepath, syncStatus ->
            if (localRelativeFilepath != null) {
                // Update the sync status on the RecyclerView element
                setProgressAsync(workDataOf(File to localRelativeFilepath, SyncStatus to syncStatus.name))

                // Update notification progress
                currentSize += File(GoogleDriveClient.BASE_STORAGE_DIR, localRelativeFilepath).length().toInt()
                SynchronizeNotification.updateProgress(applicationContext, notificationManagerCompat, notificationId, builder, sizeUnit, totalSize, currentSize)
            }
        }
    }

}