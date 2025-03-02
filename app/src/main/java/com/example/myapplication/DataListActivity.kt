package com.example.myapplication

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.LineChart

class DataListActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_list)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        val filesDir = getExternalFilesDir(null)

        // .txt uzantılı dosyaları filtrele
        val fileList = filesDir?.listFiles()?.filter {
            it.isFile && it.name.endsWith(".txt")
        }?.map { it.name } ?: emptyList()

        recyclerView.adapter = FileListAdapter(fileList, this)
        recyclerView.layoutManager = LinearLayoutManager(this)
    }
}