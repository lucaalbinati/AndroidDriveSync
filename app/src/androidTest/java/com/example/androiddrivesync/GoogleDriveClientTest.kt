package com.example.androiddrivesync

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.androiddrivesync.boot.SignInActivity
import com.example.androiddrivesync.drive.GoogleDriveClient
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.tasks.Tasks
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ExecutionException

@RunWith(AndroidJUnit4::class)
class GoogleDriveClientTest {

    lateinit var context: Context
    lateinit var serverAuthCode: String

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext

        try {
            val task = SignInActivity.getGoogleSignInClient(context).silentSignIn()
            Tasks.await(task)
        } catch (e: ExecutionException) {
            context.startActivity(SignInActivity.getGoogleSignInClient(context).signInIntent)
            SignInActivity.getGoogleSignInClient(context).silentSignIn()
        }

        val account = GoogleSignIn.getLastSignedInAccount(context)
        Assert.assertNotNull(account)
        Assert.assertNotNull(account?.serverAuthCode)
        serverAuthCode = account?.serverAuthCode!!
    }

    @Test
    fun googleDriveClient() {
        GoogleDriveClient(context, serverAuthCode)
    }

}