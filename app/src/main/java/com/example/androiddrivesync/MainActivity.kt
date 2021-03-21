package com.example.androiddrivesync

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.*
import kotlinx.coroutines.*
import java.util.*
import kotlin.collections.ArrayList

class MainActivity: AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        BootUpManager.onBootUp(this)
    }

    fun pingDrive(v: View) {
        // Get Google account and server authentication code
        val account = GoogleSignIn.getLastSignedInAccount(this)
        val serverAuthCode = account!!.serverAuthCode!!

        // Create GoogleDriveClient
        val gdc = GoogleDriveClient(this, serverAuthCode)

        // Request all files
        scope.launch {
            val result = gdc.listAllFiles()

            if (result.isSuccess) {
                val fileList = result.getOrNull()!!["files"] as ArrayList<*>
                Toast.makeText(this@MainActivity, "Got ${fileList.size} files", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, getFailureExceptionErrorMessage(result), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun <T> getFailureExceptionErrorMessage(result: Result<T>): String {
        var errorMsg = ""
        result.exceptionOrNull()?.let {
            it.message?.let { msg ->
                errorMsg = msg
            } ?: run {
                errorMsg = "Exception '${it.javaClass.simpleName}'"
            }
        } ?: run {
            errorMsg = "Unknown exception"
        }
        return errorMsg
    }

}