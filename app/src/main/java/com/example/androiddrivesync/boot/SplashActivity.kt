package com.example.androiddrivesync.boot

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.androiddrivesync.main.MainActivity

class SplashActivity: AppCompatActivity() {

    private val signIn = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        when (it.resultCode) {
            RESULT_OK -> startFilesPermissionActivityIfNeeded()
            else -> finish()
        }
    }

    private val filesPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        when (it.resultCode) {
            RESULT_OK -> startMainActivity()
            else -> {}
        }

        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SignInActivity.trySilentSignIn(this,
            {
                startFilesPermissionActivityIfNeeded()
            }, {
                startSignInActivity()
            })
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
    }

    private fun startSignInActivity() {
        signIn.launch(Intent(this, SignInActivity::class.java))
    }

    private fun startFilesPermissionActivityIfNeeded() {
        if (!FilesPermissionActivity.hasExternalFilesPermission()) {
            filesPermission.launch(Intent(this, FilesPermissionActivity::class.java))
        } else {
            startMainActivity()
            finish()
        }
    }
}