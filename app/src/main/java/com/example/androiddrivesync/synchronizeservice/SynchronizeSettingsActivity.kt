package com.example.androiddrivesync.synchronizeservice

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.androiddrivesync.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SynchronizeSettingsActivity : AppCompatActivity(), View.OnClickListener {
    companion object {
        private const val TAG = "SynchronizeSettingsActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_synchronize_settings)

        findViewById<View>(R.id.periodicity_layout).setOnClickListener(this)
        updatePeriodicityContent()
    }

    override fun onClick(v: View?) {
        val items: Array<String> = SynchronizePeriodicity.values().map { it.format() }.toTypedArray()
        val currentItem = SynchronizeSettingsSharedPreferencesHelper.getPeriodicity(this).ordinal

        AlertDialog.Builder(this)
            .setTitle(R.string.synchronization_periodicity_picker_title)
            .setSingleChoiceItems(items, currentItem) { dialog, selectedItemIdx ->
                updatePeriodicitySetting(selectedItemIdx)
                updatePeriodicityContent()
                updatePeriodicityForWorker(dialog)
            }
            .setNegativeButton(R.string.synchronization_periodicity_picker_cancel) { dialog, _ ->
                dialog.cancel()
            }
            .setCancelable(true)
            .show()
    }

    private fun updatePeriodicitySetting(newItemIdx: Int) {
        val selectedPeriodicity = SynchronizePeriodicity.values()[newItemIdx]
        SynchronizeSettingsSharedPreferencesHelper.setPeriodicity(this, selectedPeriodicity)
    }

    private fun updatePeriodicityContent() {
        findViewById<TextView>(R.id.periodicity_text).text = SynchronizeSettingsSharedPreferencesHelper.getPeriodicity(this).format()
    }

    private fun updatePeriodicityForWorker(dialog: DialogInterface) {
        CoroutineScope(Dispatchers.IO).launch {
            SynchronizeWorker.updatePeriodicity(this@SynchronizeSettingsActivity)
            dialog.cancel()
        }
    }

}