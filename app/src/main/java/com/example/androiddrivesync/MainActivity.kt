package com.example.androiddrivesync

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.*
import kotlinx.coroutines.*
import java.util.*

class MainActivity: AppCompatActivity() {
    companion object {
        private const val MANAGE_ALL_FILES_PERMISSION_REQUEST_CODE = 100
    }

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        BootUpManager.onBootUp(this)
    }

    private val gdc: GoogleDriveClient by lazy {
        // Get Google account and server authentication code
        val account = GoogleSignIn.getLastSignedInAccount(this)
        val serverAuthCode = account!!.serverAuthCode!!

        // Create GoogleDriveClient
        return@lazy GoogleDriveClient(this, serverAuthCode)
    }

    fun pingDrive(v: View) {
        // Request all files
        scope.launch {
            val result = gdc.listAllFiles()

            if (result.isSuccess) {
                val files = result.getOrNull()!!
                val message = resources.getQuantityString(R.plurals.files_received_toast_message, files.size, files.size)
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, getFailureExceptionErrorMessage(result), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun syncDrive(v: View) {
        val hasExternalFilesPermission = checkExternalFilesPermission(MANAGE_ALL_FILES_PERMISSION_REQUEST_CODE)

        if (hasExternalFilesPermission) {
            doSyncDrive()
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

    private fun doSyncDrive() {
        // Synchronise
        scope.launch {
            gdc.synchronise()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == MANAGE_ALL_FILES_PERMISSION_REQUEST_CODE) {
            if (!Environment.isExternalStorageManager()) {
                showAlertDialogForRefusedPermission()
            } else {
                doSyncDrive()
            }
        }
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

    private fun <T> getFailureExceptionErrorMessage(result: Result<T>): String {
        var errorMsg = ""
        result.exceptionOrNull()?.let {
            it.message?.let { msg ->
                errorMsg = msg
            } ?: run {
                errorMsg = "Exception '${it.javaClass.simpleName}'"
            }
        } ?: run {
            errorMsg = "Unknown exception"
        }
        return errorMsg
    }

}