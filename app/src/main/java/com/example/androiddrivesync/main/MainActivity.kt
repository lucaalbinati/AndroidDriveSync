package com.example.androiddrivesync.main

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.work.*
import com.example.androiddrivesync.*
import com.example.androiddrivesync.R
import com.example.androiddrivesync.drive.GoogleDriveClient
import com.example.androiddrivesync.synchronizeservice.SynchronizeNotification
import com.example.androiddrivesync.synchronizeservice.SynchronizeWorker
import com.example.androiddrivesync.utils.CredentialsSharedPreferences
import com.example.androiddrivesync.utils.LocalFilesToSynchronizeHandler
import com.example.androiddrivesync.utils.Utility
import com.google.android.gms.auth.api.signin.*
import kotlinx.coroutines.*
import java.io.File
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private val scope = CoroutineScope(Dispatchers.Main)

    private lateinit var googleDriveClient: GoogleDriveClient
    private lateinit var synchronizedFileHandler: SynchronizedFileHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Create notification channel
        SynchronizeNotification.createChannel(this)

        // Setup SharedPreferences file for Google credentials
        CredentialsSharedPreferences.setupCredentialsSharedPreferences(this@MainActivity)

        // Initialize and populate RecyclerView
        initializeGoogleDriveClientAndPopulate()

        // Start periodic work request, if not already
        SynchronizeWorker.setupPeriodicWorkRequest(this)
    }

    override fun onPause() {
        super.onPause()

        if (this::synchronizedFileHandler.isInitialized) {
            LocalFilesToSynchronizeHandler.save(this, synchronizedFileHandler.getAllNames())
        }
    }

    private fun initializeGoogleDriveClientAndPopulate() {
        // Setup Google Drive Client
        googleDriveClient = GoogleDriveClient.setupGoogleDriveClient(this)

        // Setup RecyclerView
        synchronizedFileHandler = setupSynchronizedFileHandler()

        // Update files' status
        refreshStatus(this.findViewById(R.id.refresh_status_fab))
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

    fun syncDrive(v: View) {
        // TODO
    }

    fun refreshStatus(v: View) {
        scope.launch {
            // Disable button
            v.setOnClickListener(null)

            // Show progress bar and hide RecyclerView
            this@MainActivity.findViewById<RecyclerView>(R.id.rvSynchronizedFiles).visibility = View.INVISIBLE
            this@MainActivity.findViewById<ProgressBar>(R.id.refresh_progressBar).visibility = View.VISIBLE

            // Check status
            googleDriveClient.checkDriveStatus(synchronizedFileHandler.getAllNames(), updateSyncStatusUI)

            // Hide progress bar and show RecyclerView
            this@MainActivity.findViewById<ProgressBar>(R.id.refresh_progressBar).visibility = View.INVISIBLE
            this@MainActivity.findViewById<RecyclerView>(R.id.rvSynchronizedFiles).visibility = View.VISIBLE

            // Enable button
            v.setOnClickListener(::refreshStatus)
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
                throw Exception("Local relative filepath '$localRelativeFilepath' does not share a root path with any of the filepaths to be synchronized", e)
            }
        }
    }

}