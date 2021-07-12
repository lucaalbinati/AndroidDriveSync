package com.example.androiddrivesync.main

import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.work.*
import com.example.androiddrivesync.*
import com.example.androiddrivesync.R
import com.example.androiddrivesync.drive.GoogleDriveClient
import com.example.androiddrivesync.utility.CredentialsSharedPreferences
import com.example.androiddrivesync.utility.LocalFilesToSynchronizeHandler
import com.example.androiddrivesync.utility.Utility
import com.google.android.gms.auth.api.signin.*
import kotlinx.coroutines.*
import java.io.File
import java.util.*
import kotlin.collections.ArrayList


class MainActivity: AppCompatActivity() {
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
    }

    override fun onPause() {
        super.onPause()

        if (this::synchronizedFileHandler.isInitialized) {
            LocalFilesToSynchronizeHandler.save(this, synchronizedFileHandler.getAllNames())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // TODO
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

    private var workRequestId: UUID? = null

    fun syncDrive(v: View) {
        // Check if a synchronization is already going on
        if (workRequestId != null && !WorkManager.getInstance(this).getWorkInfoById(workRequestId!!).isDone) {
            return
        }

        // Create and enqueue work request
        workRequestId = SynchronizeWorker.enqueueWorkRequest(this, synchronizedFileHandler.getAllNames())

        // Observe work request
        SynchronizeWorker.observeWorkRequest(this, workRequestId!!) { workState, workProgress ->
            when (workState) {
                WorkInfo.State.ENQUEUED -> {
                    // Start animation
                    v.startAnimation(AnimationUtils.loadAnimation(this@MainActivity, R.anim.sync_fab_animation))
                }
                WorkInfo.State.RUNNING -> {
                    val file = workProgress.getString(SynchronizeWorker.File)
                    val syncStatusString = workProgress.getString(SynchronizeWorker.SyncStatus)
                    if (file != null && syncStatusString != null) {
                        val syncStatus = Utility.FileSyncStatus.valueOf(syncStatusString)
                        updateSyncStatusUI(file, syncStatus)
                    }
                }
                WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                    // Stop animation
                    v.clearAnimation()
                }
                WorkInfo.State.BLOCKED -> {}
            }
        }
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