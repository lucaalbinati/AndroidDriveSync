package com.example.androiddrivesync.main

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

        if (oldElement == newElement) {
            return
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

    fun containsFilename(filename: String): Boolean {
        return getAllNames().contains(filename)
    }

    fun getElementByName(filename: String): SynchronizedFile {
        if (!containsFilename(filename)) {
            throw NoSuchElementException()
        }

        val filteredFiles = synchronizedFiles.filter { sf -> sf.name == filename }

        if (filteredFiles.size >= 2) {
            throw Exception("Found ${filteredFiles.size} SynchronizedFiles with name '${filename}', instead of 1")
        }

        return filteredFiles[0]
    }

    fun getAllElements(): List<SynchronizedFile> {
        return synchronizedFiles
    }

    fun getAllNames(): List<String> {
        return synchronizedFiles.map { f -> f.name }
    }

    fun getSize(): Int {
        return synchronizedFiles.size
    }

}