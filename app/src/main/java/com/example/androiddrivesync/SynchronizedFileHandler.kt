package com.example.androiddrivesync

import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class SynchronizedFileHandler(context: Context, recyclerView: RecyclerView, private var synchronizedFiles: ArrayList<SynchronizedFile>) {

    private val adapter = SynchronizedFileAdapter(synchronizedFiles)

    init {
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(context)
    }

    fun addElement(synchronizedFile: SynchronizedFile) {
        synchronizedFiles.add(synchronizedFile)
        adapter.notifyItemInserted(synchronizedFiles.size - 1)
    }

    fun modifyElement(oldElement: SynchronizedFile, newElement: SynchronizedFile) {
        if (!synchronizedFiles.contains(oldElement)) {
            throw NoSuchElementException()
        }

        val idx = synchronizedFiles.indexOf(oldElement)
        synchronizedFiles[idx] = newElement

        adapter.notifyItemChanged(idx)
    }

    fun removeElement(index: Int) {
        synchronizedFiles.removeAt(index)
        adapter.notifyItemRemoved(index)
    }

    fun removeElement(element: SynchronizedFile) {
        if (!synchronizedFiles.contains(element)) {
            throw NoSuchElementException()
        }

        removeElement(synchronizedFiles.indexOf(element))
    }

    fun getAllElements(): ArrayList<SynchronizedFile> {
        return synchronizedFiles
    }

}