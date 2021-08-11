package com.example.androiddrivesync.main

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.work.*
import com.example.androiddrivesync.*
import com.example.androiddrivesync.R
import com.example.androiddrivesync.drive.GoogleDriveClient
import com.example.androiddrivesync.synchronizeservice.SynchronizeService
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

        // Start the service, in case it wasn't running
        startService(Intent(this, SynchronizeService::class.java))
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
        val pref = this.getSharedPreferences(GoogleDriveClient.DRIVE_SHARED_PREFERENCES, MODE_PRIVATE)
        var serverAuthCode = pref.getString(GoogleDriveClient.SERVER_AUTHENTICATION_CODE_KEY_NAME, null)

        if (serverAuthCode == null) {
            // TODO move some of this in SignInActivity
            Log.i("MainActivity", "Did not find a ${GoogleDriveClient.SERVER_AUTHENTICATION_CODE_KEY_NAME} in the '${GoogleDriveClient.DRIVE_SHARED_PREFERENCES}' shared preferences file")
            val account = GoogleSignIn.getLastSignedInAccount(this)
            serverAuthCode = account!!.serverAuthCode!!
            Log.i("MainActivity", "Got new server authentication code $serverAuthCode")

            val editor = pref.edit()
            editor.putString(GoogleDriveClient.SERVER_AUTHENTICATION_CODE_KEY_NAME, serverAuthCode)
            editor.apply()
            Log.i("MainActivity", "Saved new server authentication code $serverAuthCode")
        } else {
            Log.i("MainActivity", "Found a ${GoogleDriveClient.SERVER_AUTHENTICATION_CODE_KEY_NAME} in the '${GoogleDriveClient.DRIVE_SHARED_PREFERENCES}' shared preferences file: $serverAuthCode")
        }

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

    fun syncDrive(v: View) {
        // FIXME: move somewhere else
        stopService(Intent(this, SynchronizeService::class.java))
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