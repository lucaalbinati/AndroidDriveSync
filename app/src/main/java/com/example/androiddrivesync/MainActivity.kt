package com.example.androiddrivesync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
        private const val GOOGLE_SIGN_IN = 100
        private const val REQUEST_ALL_FILES_PERMISSION = 101

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

        // Try to silently sign in to Google
        SignInActivity.trySilentSignIn(this, ::initializeGoogleDriveClientAndPopulate) {
            startActivityForResult(Intent(this, SignInActivity::class.java), GOOGLE_SIGN_IN)
        }
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

    private fun hasExternalFilesPermission(): Boolean {
        return Environment.isExternalStorageManager()
    }

    private fun requestExternalFilesPermission() {
        if (hasExternalFilesPermission()) {
            return
        }
        startActivityForResult(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION), REQUEST_ALL_FILES_PERMISSION)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == GOOGLE_SIGN_IN) {
            if (hasExternalFilesPermission()) {
                initializeGoogleDriveClientAndPopulate()
            } else {
                requestExternalFilesPermission()
            }
        } else if (requestCode == REQUEST_ALL_FILES_PERMISSION) {
            if (hasExternalFilesPermission()) {
                initializeGoogleDriveClientAndPopulate()
            } else {
                // The user did not enable the permission. We give him the possibility to try again
                showAlertDialogForRefusedPermission()
            }
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

    private fun showAlertDialogForRefusedPermission() {
        AlertDialog.Builder(this)
            .setTitle(R.string.missing_permissions_dialog_alert_title)
            .setMessage(R.string.missing_permissions_dialog_alert_message)
            .setPositiveButton(R.string.missing_permissions_dialog_alert_positive) { _: DialogInterface, _: Int ->
                Toast.makeText(
                    this,
                    R.string.missing_permissions_dialog_alert_positive_toast_message,
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(R.string.missing_permissions_dialog_alert_negative) { _: DialogInterface, _: Int ->
                requestExternalFilesPermission()
            }
            .setCancelable(false)
            .show()
    }

}