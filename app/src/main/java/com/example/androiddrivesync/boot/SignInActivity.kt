package com.example.androiddrivesync.boot

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.androiddrivesync.R
import com.example.androiddrivesync.utility.CredentialsSharedPreferences
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

    private val googleSignIn = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { data ->
        when (data.resultCode) {
            RESULT_OK -> {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data.data)
                verifySignIn(task)
                setResult(RESULT_OK)
                finish()
            }
            else -> {}
        }
    }

    override fun onBackPressed() {
        // prevents from going back to the splash screen
        moveTaskToBack(true)
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

        googleSignIn.launch(signInIntent)
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