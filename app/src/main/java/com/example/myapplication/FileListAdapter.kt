package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileListAdapter(private val fileList: List<String>, private val context: Context) :
    RecyclerView.Adapter<FileListAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val fileNameTextView: TextView = itemView.findViewById(R.id.fileNameTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val fileName = fileList[position]
        holder.fileNameTextView.text = formatFileName(fileName)

        // Item click listener
        holder.itemView.setOnClickListener {
            val intent = Intent(context, DataDetailActivity::class.java)
            intent.putExtra("FILE_NAME", fileName)
            context.startActivity(intent)

            // Add animation
            if (context is DataListActivity) {
                context.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        }
    }

    override fun getItemCount(): Int = fileList.size

    // Helper method to format the file name with date if available
    private fun formatFileName(fileName: String): String {
        try {
            // Get the file and its last modified date
            val file = File(context.getExternalFilesDir(null), fileName)
            val lastModified = file.lastModified()
            val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            val formattedDate = dateFormat.format(Date(lastModified))

            // Return formatted string
            return "$fileName\n$formattedDate"
        } catch (e: Exception) {
            return fileName
        }
    }
}