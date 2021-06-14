package com.example.androiddrivesync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.auth.api.signin.*
import kotlinx.coroutines.*
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

    private fun createSynchronizationNotificationBuilder(): NotificationCompat.Builder {
        return NotificationCompat.Builder(this, SYNCHRONIZING_CHANNEL_ID)
            .setSmallIcon(R.raw.synchronizing)
            .setContentTitle(getString(R.string.synchronization_synchronizing_title))
            .setContentText(getString(R.string.synchronization_synchronizing_progress))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
    }

    fun syncDrive(v: View) {
        val builder = createSynchronizationNotificationBuilder()

        scope.launch {
            val progressMultiplier = 20
            val progressMax = synchronizedFileHandler.getSize() * progressMultiplier
            val progressInitial = progressMax / progressMultiplier
            var progressCurrent = progressMultiplier
            val notificationId = builder.hashCode()

            NotificationManagerCompat.from(this@MainActivity).apply {

                fun updateNotificationProgress(notificationId: Int, builder: NotificationCompat.Builder, progressMax: Int, progressCurrent: Int, text: String? = null) {
                    if (text != null) {
                        builder.setContentText(text)
                    }
                    builder.setProgress(progressMax, progressCurrent, false)
                    notify(notificationId, builder.build())
                }

                // Issue initial notification
                updateNotificationProgress(notificationId, builder, progressMax, progressInitial)

                // Start animation
                v.startAnimation(
                    AnimationUtils.loadAnimation(
                        this@MainActivity,
                        R.anim.sync_fab_animation
                    )
                )

                // Start job
                val filesToSynchronize = synchronizedFileHandler.getAllNames()
                googleDriveClient.synchronise(filesToSynchronize) { filename, status ->
                    // Update sync status on RecyclerView element
                    updateSyncStatusUI(filename, status)

                    // Update notification progress
                    progressCurrent += progressMultiplier
                    val text = if (progressCurrent == progressMax) getString(R.string.synchronization_synchronizing_complete) else null
                    updateNotificationProgress(notificationId, builder, progressMax, progressCurrent, text)
                }

                // Show toast
                Toast.makeText(this@MainActivity, "Synchronization successful!", Toast.LENGTH_SHORT).show()

                // Stop animation
                v.clearAnimation()

                // Clear notification
                cancel(notificationId)
            }
        }
    }

    fun refreshStatus(v: View) {
        scope.launch {
            //this@MainActivity.findViewById<RecyclerView>(R.id.rvSynchronizedFiles).visibility = View.INVISIBLE
            this@MainActivity.findViewById<ProgressBar>(R.id.refresh_progressBar).visibility = View.VISIBLE

            googleDriveClient.checkDriveStatus(synchronizedFileHandler.getAllNames(), updateSyncStatusUI)

            this@MainActivity.findViewById<ProgressBar>(R.id.refresh_progressBar).visibility = View.INVISIBLE
            //this@MainActivity.findViewById<RecyclerView>(R.id.rvSynchronizedFiles).visibility = View.VISIBLE
        }
    }

    private val updateSyncStatusUI: (String, Utility.FileSyncStatus) -> Unit = { filename, status ->
        val synchronizedFile = synchronizedFileHandler.getElementByName(filename)
        if (status != synchronizedFile.syncStatus) {
            synchronizedFileHandler.modifyElement(synchronizedFile, synchronizedFile.withNewSyncStatus(status))
        }
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