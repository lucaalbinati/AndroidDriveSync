package com.example.androiddrivesync

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class SplashActivity: AppCompatActivity() {

    private val signIn = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
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
                startMainActivity()
                finish()
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
}