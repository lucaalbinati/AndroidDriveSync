package com.example.androiddrivesync.main

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.androiddrivesync.*
import com.example.androiddrivesync.drive.GoogleDriveClient
import com.example.androiddrivesync.utility.CredentialsSharedPreferences
import com.example.androiddrivesync.utility.LocalFilesToSynchronizeHandler
import com.example.androiddrivesync.utility.Utility
import com.google.android.gms.auth.api.signin.*
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.*
import java.io.File
import java.lang.Integer.max
import java.util.*
import kotlin.collections.ArrayList


class MainActivity: AppCompatActivity() {
    companion object {
        private const val SYNCHRONIZING_CHANNEL_ID = "SYNCHRONIZING_CHANNEL_ID"
    }

    private val scope = CoroutineScope(Dispatchers.Main)

    private lateinit var googleDriveClient: GoogleDriveClient
    private lateinit var synchronizedFileHandler: SynchronizedFileHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Create notification channel
        createNotificationChannel()

        // Setup SharedPreferences file for Google credentials
        CredentialsSharedPreferences.setupCredentialsSharedPreferences(this@MainActivity)

        // Initialize and populate RecyclerView
        initializeGoogleDriveClientAndPopulate()
    }

    override fun onPause() {
        super.onPause()

        if (this::synchronizedFileHandler.isInitialized) {
            LocalFilesToSynchronizeHandler.save(this, synchronizedFileHandler.getAllNames())
        }
    }

    private fun initializeGoogleDriveClientAndPopulate() {
        // Setup Google Drive Client
        googleDriveClient = getGoogleDriveClient()

        // Setup RecyclerView
        synchronizedFileHandler = setupSynchronizedFileHandler()

        // Update files' status
        refreshStatus(this.findViewById(R.id.refresh_status_fab))
    }

    private fun getGoogleDriveClient(): GoogleDriveClient {
        // Get Google account and server authentication code
        val account = GoogleSignIn.getLastSignedInAccount(this)
        val serverAuthCode = account!!.serverAuthCode!!

        // Create GoogleDriveClient
        return GoogleDriveClient(this, serverAuthCode)
    }

    private fun setupSynchronizedFileHandler(): SynchronizedFileHandler {
        val recyclerView = findViewById<View>(R.id.rvSynchronizedFiles) as RecyclerView
        val localFilesToSynchronize = LocalFilesToSynchronizeHandler.getLocalFilesToSynchronize(this)
        val synchronizedFiles = ArrayList<SynchronizedFile>()
        for (localFile in localFilesToSynchronize) {
            synchronizedFiles.add(SynchronizedFile(localFile, Utility.FileSyncStatus.UNKNOWN))
        }

        return SynchronizedFileHandler(this, recyclerView, synchronizedFiles)
    }

    private fun createInitialSynchronizationNotificationBuilder(): NotificationCompat.Builder {
        return NotificationCompat.Builder(this, SYNCHRONIZING_CHANNEL_ID)
            .setSmallIcon(R.raw.synchronizing)
            .setContentTitle(getString(R.string.synchronization_synchronizing_title))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(0, 0, true)
            .setColor(getColor(R.color.ic_launcher_background))
    }

    fun syncDrive(v: View) {
        // Disable buttons
        disableButtons()

        val builder = createInitialSynchronizationNotificationBuilder()
        val notificationId = builder.hashCode()

        scope.launch {
            NotificationManagerCompat.from(this@MainActivity).apply {
                fun updateNotificationProgress(notificationId: Int, builder: NotificationCompat.Builder, sizeUnit: String, totalBytes: Int, currentBytes: Int) {
                    val totalBytesInSizeUnit = Utility.convertToSizeUnit(totalBytes, sizeUnit)
                    val currentBytesInSizeUnit = Utility.convertToSizeUnit(currentBytes, sizeUnit)

                    builder.setContentText(getString(R.string.synchronization_synchronizing_uploading, currentBytesInSizeUnit, totalBytesInSizeUnit, sizeUnit))
                    builder.setProgress(totalBytes, max(currentBytes, (0.05*totalBytes).toInt()), false)
                    notify(notificationId, builder.build())
                }

                // Issue initial notification
                builder.setProgress(0, 0, true)
                notify(notificationId, builder.build())

                // Start animation
                v.startAnimation(AnimationUtils.loadAnimation(this@MainActivity, R.anim.sync_fab_animation))

                // Start job
                val filesToSynchronize = synchronizedFileHandler.getAllNames()
                val fileSyncActions = googleDriveClient.getFileSyncActions(filesToSynchronize)
                val totalSize = googleDriveClient.getFilesToUploadSize(fileSyncActions).toInt()
                val sizeUnit = Utility.getSizeUnit(totalSize)

                var currentSize = 0
                updateNotificationProgress(notificationId, builder, sizeUnit, totalSize, currentSize)

                googleDriveClient.synchronise(fileSyncActions) { localRelativeFilepath, syncStatus ->
                    // Update the sync status on the RecyclerView element
                    updateSyncStatusUI(localRelativeFilepath, syncStatus)

                    // Update notification progress
                    if (localRelativeFilepath != null) {
                        currentSize += File(GoogleDriveClient.BASE_STORAGE_DIR, localRelativeFilepath).length().toInt()
                        updateNotificationProgress(notificationId, builder, sizeUnit, totalSize, currentSize)
                    }
                }

                // Update notification
                updateNotificationProgress(notificationId, builder, sizeUnit, totalSize, totalSize)

                // Stop animation
                v.clearAnimation()

                // Enable buttons
                enableButtons()

                // Clear notification
                cancel(notificationId)
            }
        }
    }

    fun refreshStatus(v: View) {
        scope.launch {
            // Disable buttons
            disableButtons()

            // Show progress bar and hide RecyclerView
            this@MainActivity.findViewById<RecyclerView>(R.id.rvSynchronizedFiles).visibility = View.INVISIBLE
            this@MainActivity.findViewById<ProgressBar>(R.id.refresh_progressBar).visibility = View.VISIBLE

            // Check status
            googleDriveClient.checkDriveStatus(synchronizedFileHandler.getAllNames(), updateSyncStatusUI)

            // Hide progress bar and show RecyclerView
            this@MainActivity.findViewById<ProgressBar>(R.id.refresh_progressBar).visibility = View.INVISIBLE
            this@MainActivity.findViewById<RecyclerView>(R.id.rvSynchronizedFiles).visibility = View.VISIBLE

            // Enable buttons
            enableButtons()
        }
    }

    private val updateSyncStatusUI: (String?, Utility.FileSyncStatus) -> Unit = { localRelativeFilepath, syncStatus ->
        if (localRelativeFilepath != null) {
            val synchronizedFile = getSynchronizedFileByFilepath(localRelativeFilepath)
            if (syncStatus != synchronizedFile.syncStatus) {
                synchronizedFileHandler.modifyElement(synchronizedFile, synchronizedFile.withNewSyncStatus(syncStatus))
            }
        }
    }

    private fun getSynchronizedFileByFilepath(localRelativeFilepath: String): SynchronizedFile {
        return try {
            synchronizedFileHandler.getElementByName(localRelativeFilepath)
        } catch (e: NoSuchElementException) {
            val parentFile = File(localRelativeFilepath).parentFile
            if (parentFile != null) {
                getSynchronizedFileByFilepath(parentFile.path)
            } else {
                throw e
            }
        }
    }

    private fun enableButtons() {
        findViewById<FloatingActionButton>(R.id.sync_drive_fab).setOnClickListener(::syncDrive)
        findViewById<FloatingActionButton>(R.id.refresh_status_fab).setOnClickListener(::refreshStatus)
    }

    private fun disableButtons() {
        findViewById<FloatingActionButton>(R.id.sync_drive_fab).setOnClickListener(null)
        findViewById<FloatingActionButton>(R.id.refresh_status_fab).setOnClickListener(null)
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel
        val name = getString(R.string.synchronization_channel_name)
        val descriptionText = getString(R.string.synchronization_channel_description)
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(SYNCHRONIZING_CHANNEL_ID, name, importance).apply { description = descriptionText }

        // Register the channel with the system
        val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

}