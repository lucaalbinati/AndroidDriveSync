package com.example.androiddrivesync

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.util.ScopeUtil
import com.google.android.gms.tasks.Task
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import kotlinx.coroutines.*
import java.util.*

class SignInActivity: AppCompatActivity() {
    companion object {
        private const val SIGN_IN = 100

        fun getGoogleSignInClient(context: Context): GoogleSignInClient {
            val googleSignInOptions = getGoogleSignInOptions(context)
            return GoogleSignIn.getClient(context, googleSignInOptions)
        }

        private fun getGoogleSignInOptions(context: Context): GoogleSignInOptions {
            val webClientId = CredentialsSharedPreferences.getClientId(context)
            val scopes = ScopeUtil.fromScopeString(Scopes.DRIVE_FULL)

            val gsoBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestProfile()
                .requestServerAuthCode(webClientId)
                .requestIdToken(webClientId)

            for (scope in scopes) {
                gsoBuilder.requestScopes(scope)
            }

            return gsoBuilder.build()
        }

        fun trySilentSignIn(context: Context, onSuccessCallback: () -> Unit, onFailureCallback: () -> Unit) {
            if (GoogleSignIn.getLastSignedInAccount(context) != null) {
                getGoogleSignInClient(context).silentSignIn().addOnCompleteListener {
                    if (it.isSuccessful) {
                        onSuccessCallback()
                    } else {
                        onFailureCallback()
                    }
                }
            } else {
                onFailureCallback()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_in)
    }

    fun signIn(v: View) {
        startGoogleSignInActivity()
    }

    private fun startGoogleSignInActivity() {
        val googleSignInClient = getGoogleSignInClient(this)
        val signInIntent = googleSignInClient.signInIntent
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
        val clientId = CredentialsSharedPreferences.getClientId(this)
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