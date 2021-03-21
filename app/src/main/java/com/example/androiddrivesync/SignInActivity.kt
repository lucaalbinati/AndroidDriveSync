package com.example.androiddrivesync

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.util.ScopeUtil
import com.google.android.gms.tasks.Task
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.lang.Exception
import java.util.*

class SignInActivity: AppCompatActivity() {
    companion object {
        const val SIGN_IN = 100

        fun getGoogleSignInOptions(context: Context, webClientId: String): GoogleSignInOptions {
            val scopes = ScopeUtil.fromScopeString(DriveScopes.DRIVE_APPDATA, Scopes.DRIVE_FULL)

            val gsoBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestServerAuthCode(webClientId)
                .requestIdToken(webClientId)

            for (scope in scopes) {
                gsoBuilder.requestScopes(scope)
            }

            return gsoBuilder.build()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_in)
    }

    fun signIn(v: View) {
        val gso = getGoogleSignInOptions(this, BootUpManager.getClientId(this))
        val mGoogleSignInClient = GoogleSignIn.getClient(this, gso)
        val signInIntent = mGoogleSignInClient.signInIntent
        startActivityForResult(signInIntent, SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            verifySignIn(task)
            finish()
        }
    }

    private fun verifySignIn(task: Task<GoogleSignInAccount>) {
        val account = task.result
        if (account != null) {
            // Verify that the ID Token is valid
            val isIdTokenValidResult = runBlocking {
                return@runBlocking validateIdToken(account.idToken!!)
            }

            if (isIdTokenValidResult.isFailure) {
                throw Exception("Invalid token")
            }
        } else {
            throw Exception("Account is null")
        }
    }

    private suspend fun validateIdToken(idToken: String): Result<Boolean> {
        val httpTransport = NetHttpTransport()
        val jsonFactory = JacksonFactory.getDefaultInstance()
        val clientId = BootUpManager.getClientId(this)
        val verifier = GoogleIdTokenVerifier.Builder(httpTransport, jsonFactory).setAudience(
            Collections.singletonList(clientId)).build()

        return withContext(Dispatchers.IO) {
            try {
                verifier.verify(verifier.verify(idToken))
            } catch (e: Exception) {
                return@withContext Result.failure<Boolean>(e)
            }
            Result.success(true)
        }
    }

}