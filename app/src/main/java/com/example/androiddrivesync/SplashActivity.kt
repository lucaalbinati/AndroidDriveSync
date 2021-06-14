package com.example.androiddrivesync

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class SplashActivity: AppCompatActivity() {

    private val signIn = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        startMainActivity()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SignInActivity.trySilentSignIn(this,
            {
                startMainActivity()
                finish()
            }, {
                signIn.launch(Intent(this, SignInActivity::class.java))
                finish()
            })
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
    }
}