package com.example.androiddrivesync

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.androiddrivesync.boot.SignInActivity
import com.example.androiddrivesync.main.SynchronizeWorker
import com.google.android.gms.tasks.Tasks
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*

@RunWith(AndroidJUnit4::class)
class SynchronizeWorkerTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        val googleSignInClient = SignInActivity.getGoogleSignInClient(context)
        val task = googleSignInClient.silentSignIn()
        Tasks.await(task)
    }

    @Test
    fun test() {
        val filesToSynchronize = Collections.emptyList<String>()
        val workManager = WorkManager.getInstance(context)
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val testDriver = WorkManagerTestInitHelper.getTestDriver(context)

        val requestId = SynchronizeWorker.setupPeriodicWorkRequest(context, filesToSynchronize)
        testDriver?.setPeriodDelayMet(requestId)

        val workInfoFuture = workManager.getWorkInfoById(requestId)
        while (!(workInfoFuture.isDone || workInfoFuture.isCancelled)) {}

        val workInfo = workInfoFuture.get()
        assert(workInfo.state == WorkInfo.State.SUCCEEDED)
    }

}