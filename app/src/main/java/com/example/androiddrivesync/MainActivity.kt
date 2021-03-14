package com.example.androiddrivesync

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.util.ScopeUtil
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.FileList
import kotlinx.coroutines.*
import java.lang.Exception
import java.util.*


class MainActivity: AppCompatActivity() {

    private val SIGN_IN_CODE = 1010

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    fun initiateOnBoarding(v: View) {
//        val account = GoogleSignIn.getLastSignedInAccount(this)
//        if (account != null) {
//            GoogleSignIn.getClient(this, GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
//        }

        //val scopes = ScopeUtil.fromScopeString(Scopes.DRIVE_FULL).elementAt(0)
        val scopes = ScopeUtil.fromScopeString(DriveScopes.DRIVE_READONLY).elementAt(0)

        val webClientId = getString(R.string.default_web_client_id)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestServerAuthCode(webClientId).requestIdToken(webClientId).requestEmail().requestScopes(scopes).build()
        val mGoogleSignInClient = GoogleSignIn.getClient(this, gso)

        val signInIntent = mGoogleSignInClient.signInIntent
        startActivityForResult(signInIntent, SIGN_IN_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SIGN_IN_CODE) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account: GoogleSignInAccount? = task.result
            if (account != null) {
                findViewById<TextView> (R.id.initiate_onboarding_button).setText(R.string.initiate_onboarding_button_after)
                findViewById<Button>(R.id.initiate_onboarding_button).setOnClickListener(null)

                // Verify that the ID Token is valid
                val isIdTokenValidResult = runBlocking {
                    return@runBlocking validateIdToken(account.idToken!!)
                }

                if (isIdTokenValidResult.isFailure) {
                    var errorMsg = ""
                    isIdTokenValidResult.exceptionOrNull()?.let {
                        it.message?.let { msg ->
                            errorMsg = msg
                        } ?: run {
                            errorMsg = "Exception '${it.javaClass.simpleName}'"
                        }
                    } ?: run {
                        errorMsg = "Unknown exception"
                    }
                    displayError(errorMsg)
                    return
                }

                // Create GoogleDriveClient and request to see all files
                val gdc = GoogleDriveClient(getString(R.string.app_name), account.serverAuthCode!!)
                val fileListResult = runBlocking {
                    return@runBlocking gdc.listAllFiles()
                }

                if (fileListResult.isFailure) {
                    displayError("another error")
                    return
                }

                val fileList = fileListResult.getOrNull()!!
                displayError("Got ${fileList.size} files")
            }
        }
    }

    private suspend fun validateIdToken(idToken: String): Result<Boolean> {
        val httpTransport = NetHttpTransport()
        val jsonFactory = JacksonFactory.getDefaultInstance()
        val verifier = GoogleIdTokenVerifier.Builder(httpTransport, jsonFactory).setAudience(
            Collections.singletonList(getString(R.string.default_web_client_id))).build()

        return withContext(Dispatchers.IO) {
            try {
                verifier.verify( verifier.verify(idToken))
            } catch (e: Exception) {
                return@withContext Result.failure<Boolean>(e)
            }
            Result.success(true)
        }
    }

    private fun displayError(errorMessage: String): Unit {
        val errorTextView = findViewById<TextView>(R.id.display_error_text)
        errorTextView.text = errorMessage
        errorTextView.visibility = View.VISIBLE
    }

}