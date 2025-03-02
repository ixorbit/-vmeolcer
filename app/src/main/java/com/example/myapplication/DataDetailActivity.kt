package com.example.myapplication

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import java.io.File

class DataDetailActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var graph: GraphView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_detail)

        recyclerView= findViewById(R.id.recyclerView)
        graph = findViewById(R.id.graph)

        // Dosya adını Intent'ten al
        val fileName = intent.getStringExtra("FILE_NAME") ?: ""

        // Dosyadan verileri oku
        val sensorDataList = readSensorDataFromFile(fileName)

        // Listeyi güncelle
        val adapter = SensorDataAdapter(sensorDataList)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Grafiği güncelle
        if (sensorDataList.isNotEmpty()) {
            val seriesX = LineGraphSeries<DataPoint>()
            val seriesY = LineGraphSeries<DataPoint>()
            val seriesZ = LineGraphSeries<DataPoint>()

            sensorDataList.forEachIndexed { index, sensorData ->
                val timestamp = sensorData.timestamp.toDouble()
                val x = DataPoint(timestamp, sensorData.x.toDouble())
                val y = DataPoint(timestamp, sensorData.y.toDouble())
                val z = DataPoint(timestamp, sensorData.z.toDouble())

                seriesX.appendData(x, true, sensorDataList.size)
                seriesY.appendData(y, true, sensorDataList.size)
                seriesZ.appendData(z, true, sensorDataList.size)
            }

            seriesX.color = Color.RED
            seriesY.color = Color.BLUE
            seriesZ.color = Color.YELLOW

            graph.addSeries(seriesX)
            graph.addSeries(seriesY)
            graph.addSeries(seriesZ)
        }
    }

    // Dosyadan SensorData listesini okuyan yardımcı fonksiyon
    private fun readSensorDataFromFile(fileName: String): List<SensorData> {
        val sensorDataList = mutableListOf<SensorData>()
        val file = File(getExternalFilesDir(null), fileName)
        if (file.exists()) {
            file.forEachLine { line ->
                val parts = line.split(",")
                if (parts.size == 4) {
                    try {
                        val timestamp = parts[0].toLong()
                        val x = parts[1].toFloat()
                        val y = parts[2].toFloat()
                        val z = parts[3].toFloat()
                        sensorDataList.add(SensorData(timestamp, x, y, z))
                    } catch (e: NumberFormatException) {
                        // Sayı dönüştürme hatası, uygun bir işlem yapın
                        Log.e("DataDetailActivity", "Sayı dönüştürme hatası: ${e.message}")
                    }
                }
            }
        }
        return sensorDataList
    }
}