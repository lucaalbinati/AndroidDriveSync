package com.example.androiddrivesync

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.auth.api.signin.*
import kotlinx.coroutines.*
import java.util.*
import kotlin.collections.ArrayList


class MainActivity: AppCompatActivity() {
    companion object {
        private const val MANAGE_ALL_FILES_PERMISSION_REQUEST_CODE = 100
        private const val GOOGLE_SIGN_IN = 101
    }

    private val scope = CoroutineScope(Dispatchers.Main)

    private lateinit var googleDriveClient: GoogleDriveClient
    private lateinit var synchronizedFileHandler: SynchronizedFileHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Setup SharedPreferences file for Google credentials
        CredentialsSharedPreferences.setupCredentialsSharedPreferences(this@MainActivity)

        // Try to silently sign in to Google
        SignInActivity.trySilentSignIn(this, ::initializeGoogleDriveClientAndPopulate) {
            startActivityForResult(Intent(this, SignInActivity::class.java), GOOGLE_SIGN_IN)
        }
    }

    private fun initializeGoogleDriveClientAndPopulate() {
        // Setup Google Drive Client
        googleDriveClient = getGoogleDriveClient()

        // Setup RecyclerView
        synchronizedFileHandler = setupSynchronizedFileHandler()

        // Update files' status
        checkSynchronizedFilesStatus()
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
            synchronizedFiles.add(SynchronizedFile(localFile, SynchronizedFile.SyncStatus.UNKNOWN))
        }

        return SynchronizedFileHandler(this, recyclerView, synchronizedFiles)
    }

    fun syncDrive(v: View) {
        val hasExternalFilesPermission = checkExternalFilesPermission(MANAGE_ALL_FILES_PERMISSION_REQUEST_CODE)

        if (hasExternalFilesPermission) {
            doSyncDrive(v)
        }
    }

    private fun checkExternalFilesPermission(requestCode: Int): Boolean {
        return if (!Environment.isExternalStorageManager()) {
            startActivityForResult(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION), requestCode)
            false
        } else {
            true
        }
    }

    private fun doSyncDrive(v: View) {
        // Synchronise
        scope.launch {
            v.startAnimation(AnimationUtils.loadAnimation(this@MainActivity, R.anim.sync_fab_animation))
            val filesToSynchronize = synchronizedFileHandler.getAllElements().map { e -> e.name }
            googleDriveClient.synchronise(filesToSynchronize)
            Toast.makeText(this@MainActivity, "Synchronization successful!", Toast.LENGTH_SHORT).show()
            v.clearAnimation()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == MANAGE_ALL_FILES_PERMISSION_REQUEST_CODE) {
            if (!Environment.isExternalStorageManager()) {
                // The user did not enable the permission. We give him the possibility to try again
                showAlertDialogForRefusedPermission()
            } else {
                doSyncDrive(this.findViewById(R.id.sync_drive_fab))
            }
        } else if (requestCode == GOOGLE_SIGN_IN) {
            initializeGoogleDriveClientAndPopulate()
        }
    }

    private fun checkSynchronizedFilesStatus() {
        // TODO
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
                checkExternalFilesPermission(MANAGE_ALL_FILES_PERMISSION_REQUEST_CODE)
            }
            .setCancelable(false)
            .show()
    }

}