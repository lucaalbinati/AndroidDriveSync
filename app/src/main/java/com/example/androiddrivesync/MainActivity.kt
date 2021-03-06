package com.example.androiddrivesync

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    fun initiateOnBoarding(v: View) {
        (findViewById<TextView> (R.id.initiate_onboarding_button)).setText(R.string.initiate_onboarding_button_after)
    }

}