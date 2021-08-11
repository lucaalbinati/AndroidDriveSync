package com.example.androiddrivesync.boot

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.androiddrivesync.boot.SignInActivity.Companion.FORCE_REFRESH_TOKEN_KEY
import com.example.androiddrivesync.main.MainActivity

class SplashActivity: AppCompatActivity() {
    companion object {
        private const val TAG = "SplashActivity"
    }

    private val signIn = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        Log.i(TAG, "finished and returned from SignInActivity")

        when (it.resultCode) {
            RESULT_OK -> startFilesPermissionActivityIfNeeded()
            else -> {
                Log.w(TAG, "received unexpected result code '${it.resultCode}'")
                finish()
            }
        }
    }

    private val filesPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        Log.i(TAG, "finished and returned from FilesPermissionActivity")

        when (it.resultCode) {
            RESULT_OK -> startMainActivity()
            else -> {
                Log.w(TAG, "received unexpected result code '${it.resultCode}'")
            }
        }

        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i(TAG, "trying to sign in silently")
        SignInActivity.trySilentSignIn(this,
            {
                Log.i(TAG, "silent sign in succeeded")
                startFilesPermissionActivityIfNeeded()
            }, { forceRefreshToken ->
                Log.i(TAG, "silent sign in failed")
                Log.i(TAG, "starting SignInActivity, with '$FORCE_REFRESH_TOKEN_KEY' set to '$forceRefreshToken'")
                startSignInActivity(forceRefreshToken)
            })
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
    }

    private fun startSignInActivity(forceRefreshToken: Boolean) {
        val intent = Intent(this, SignInActivity::class.java)
        intent.putExtra(FORCE_REFRESH_TOKEN_KEY, forceRefreshToken)
        signIn.launch(intent)
    }

    private fun startFilesPermissionActivityIfNeeded() {
        if (!FilesPermissionActivity.hasExternalFilesPermission()) {
            Log.i(TAG, "starting FilesPermissionActivity")
            filesPermission.launch(Intent(this, FilesPermissionActivity::class.java))
        } else {
            Log.i(TAG, "starting MainActivity (the files permission are already set)")
            startMainActivity()
            finish()
        }
    }
}