package com.example.androiddrivesync

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class FilesPermissionActivity: AppCompatActivity() {
    companion object {
        fun hasExternalFilesPermission(): Boolean {
            return Environment.isExternalStorageManager()
        }
    }

    private val requestAllFilesPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (!hasExternalFilesPermission()) {
            // The user did not enable the permission. We give him the possibility to try again
            showAlertDialogForRefusedPermission()
        } else {
            setResult(RESULT_OK)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_files_permission)
    }

    override fun onBackPressed() {
        // prevents from going back to the splash screen
        moveTaskToBack(true)
    }

    fun requestExternalFilesPermission(v: View) {
        requestAllFilesPermission.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
    }

    private fun showAlertDialogForRefusedPermission() {
        AlertDialog.Builder(this)
            .setTitle(R.string.missing_permissions_dialog_alert_title)
            .setMessage(R.string.missing_permissions_dialog_alert_message)
            .setNegativeButton(R.string.missing_permissions_dialog_alert_negative) { _: DialogInterface, _: Int ->
            }
            .setCancelable(false)
            .show()
    }
}