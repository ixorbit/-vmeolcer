package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FileListAdapter(private val fileList: List<String>, private val context: Context) :
    RecyclerView.Adapter<FileListAdapter.ViewHolder>() {
        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val fileNameTextView: TextView = itemView.findViewById(R.id.fileNameTextView) // Dosya adını gösteren TextView
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false) // item_file.xml layout dosyasını kullan
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.fileNameTextView.text = fileList[position]

        // Tıklama dinleyicisi ekle
        holder.itemView.setOnClickListener {
            val intent = Intent(context, DataDetailActivity::class.java)
            intent.putExtra("FILE_NAME", fileList[position])
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = fileList.size
}