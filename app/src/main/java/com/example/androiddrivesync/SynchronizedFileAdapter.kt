package com.example.androiddrivesync

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * See https://guides.codepath.com/android/using-the-recyclerview#binding-the-adapter-to-the-recyclerview for RecyclerView tutorial
 */
class SynchronizedFileAdapter(private val mSynchronizedFiles: List<SynchronizedFile>): RecyclerView.Adapter<SynchronizedFileAdapter.ViewHolder>() {

    inner class ViewHolder(listItemView: View): RecyclerView.ViewHolder(listItemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.synchronized_file_name)
        val syncStatusTextView: TextView = itemView.findViewById(R.id.synchronized_file_sync_status)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val inflater = LayoutInflater.from(context)
        val synchronizedFilesView = inflater.inflate(R.layout.item_synchronized_file, parent, false)
        return ViewHolder(synchronizedFilesView)
    }

    override fun onBindViewHolder(holder: SynchronizedFileAdapter.ViewHolder, position: Int) {
        val synchronizedFile: SynchronizedFile = mSynchronizedFiles[position]
        val nameTextView = holder.nameTextView
        nameTextView.text = synchronizedFile.name
        val syncStatusTextView = holder.syncStatusTextView
        syncStatusTextView.text = synchronizedFile.syncStatus.toString()
    }

    override fun getItemCount(): Int {
        return mSynchronizedFiles.size
    }

}