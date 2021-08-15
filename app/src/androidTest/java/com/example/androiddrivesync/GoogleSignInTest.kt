package com.example.androiddrivesync

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.androiddrivesync.boot.SignInActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.tasks.Tasks
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoogleSignInTest {

    lateinit var context: Context
    lateinit var googleSignInClient: GoogleSignInClient

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        googleSignInClient = SignInActivity.getGoogleSignInClient(context, false)
    }

    @Test
    fun trySilentSignIn() {
        val task = googleSignInClient.silentSignIn()
        Tasks.await(task)

        val account = GoogleSignIn.getLastSignedInAccount(context)
        Assert.assertNotNull(account)
        Assert.assertNotNull(account?.serverAuthCode)
    }

}