package com.example.androiddrivesync.boot

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.androiddrivesync.R
import com.example.androiddrivesync.drive.GoogleDriveClient
import com.example.androiddrivesync.drive.GoogleDriveClient.Companion.REFRESH_TOKEN_KEY
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
        private const val TAG = "SignInActivity"
        const val FORCE_REFRESH_TOKEN_KEY = "forceRefreshToken"

        fun getGoogleSignInClient(context: Context, forceRefreshToken: Boolean): GoogleSignInClient {
            val googleSignInOptions = getGoogleSignInOptions(context, forceRefreshToken)
            return GoogleSignIn.getClient(context, googleSignInOptions)
        }

        private fun getGoogleSignInOptions(context: Context, forceRefreshToken: Boolean): GoogleSignInOptions {
            val webClientId = CredentialsSharedPreferences.getClientId(context)
            val scopes = ScopeUtil.fromScopeString(Scopes.DRIVE_FULL)

            val gsoBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestServerAuthCode(webClientId, forceRefreshToken)
                .requestIdToken(webClientId)

            for (scope in scopes) {
                gsoBuilder.requestScopes(scope)
            }

            return gsoBuilder.build()
        }

        fun trySilentSignIn(context: Context, onSuccessCallback: () -> Unit, onFailureCallback: (Boolean) -> Unit) {
            if (GoogleSignIn.getLastSignedInAccount(context) != null) {
                getGoogleSignInClient(context, false).silentSignIn().addOnCompleteListener {
                    if (it.isSuccessful) {
                        Log.i(TAG, "silent sign in task was successful")
                        if (isRefreshTokenMissing(context)) {
                            Log.i(TAG, "'$REFRESH_TOKEN_KEY' missing, proceeding with failure callback (with '$FORCE_REFRESH_TOKEN_KEY' set to 'true')")
                            onFailureCallback(true)
                        } else {
                            Log.i(TAG, "'$REFRESH_TOKEN_KEY' present, proceeding with success callback")
                            onSuccessCallback()
                        }
                    } else {
                        Log.i(TAG, "silent sign in task failed, proceeding with failure callback (with '$FORCE_REFRESH_TOKEN_KEY' set to 'false')")
                        onFailureCallback(false)
                    }
                }
            } else {
                Log.i(TAG, "there is not a last Google signed in account, proceeding with failure callback (with '$FORCE_REFRESH_TOKEN_KEY' set to 'true')")
                onFailureCallback(true)
            }
        }

        private fun isRefreshTokenMissing(context: Context): Boolean {
            val pref = context.getSharedPreferences(GoogleDriveClient.DRIVE_SHARED_PREFERENCES, MODE_PRIVATE)
            return pref.contains(REFRESH_TOKEN_KEY)
        }
    }

    private var forceRefreshToken: Boolean = false

    private val googleSignIn = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        Log.i(TAG, "finished and returned from GoogleSignInActivity")

        when (it.resultCode) {
            RESULT_OK -> {
                val task = GoogleSignIn.getSignedInAccountFromIntent(it.data)
                verifySignIn(task)
                setResult(RESULT_OK)
                finish()
            }
            else -> {
                Log.w(TAG, "received unexpected result code '${it.resultCode}'")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_in)

        if (intent.extras != null && !intent.extras?.isEmpty!!) {
            forceRefreshToken = intent.getBooleanExtra(FORCE_REFRESH_TOKEN_KEY, false)
            Log.i(TAG, "found '$FORCE_REFRESH_TOKEN_KEY' set to '$forceRefreshToken' in 'intent.extras'")
        } else {
            Log.i(TAG, "did not find '$FORCE_REFRESH_TOKEN_KEY' in 'intent.extras', so it's set to '$forceRefreshToken' by default")
        }
    }

    override fun onBackPressed() {
        // prevents from going back to the splash screen
        moveTaskToBack(true)
    }

    fun signIn(v: View) {
        Log.i(TAG, "'SignIn' button was clicked")
        startGoogleSignInActivity()
    }

    private fun startGoogleSignInActivity() {
        val googleSignInClient = getGoogleSignInClient(this, forceRefreshToken)
        Log.i(TAG, "got GoogleSignInClient: $googleSignInClient")
        val signInIntent = googleSignInClient.signInIntent

        Log.i(TAG, "launching GoogleSignIn activity, using signInIntent: $signInIntent")
        googleSignIn.launch(signInIntent)
    }

    private fun verifySignIn(task: Task<GoogleSignInAccount>) {
        Log.i(TAG, "verifying GoogleSignInAccount")

        val account = task.result
        if (account != null) {
            // Verify that the ID Token is valid
            val isIdTokenValidResult = runBlocking {
                return@runBlocking validateIdToken(account.idToken!!)
            }

            if (isIdTokenValidResult.isFailure) {
                throw Exception("Invalid token")
            } else {
                Log.i(TAG, "id token is valid")
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