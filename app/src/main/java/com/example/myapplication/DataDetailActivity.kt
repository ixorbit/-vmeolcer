package com.example.myapplication

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import java.io.File
import java.io.IOException

class DataDetailActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var lineChart: LineChart

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_detail)

        recyclerView = findViewById(R.id.recyclerView)
        lineChart = findViewById(R.id.lineChart)

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
            setupChart(sensorDataList)
        }
    }

    private fun setupChart(sensorDataList: List<SensorData>) {
        val entriesX = ArrayList<Entry>()
        val entriesY = ArrayList<Entry>()
        val entriesZ = ArrayList<Entry>()

        sensorDataList.forEach { sensorData ->
            entriesX.add(Entry(sensorData.timestamp.toFloat(), sensorData.x))
            entriesY.add(Entry(sensorData.timestamp.toFloat(), sensorData.y))
            entriesZ.add(Entry(sensorData.timestamp.toFloat(), sensorData.z))
        }

        val dataSetX = LineDataSet(entriesX, "X")
        dataSetX.color = Color.RED
        dataSetX.setDrawCircles(false)
        dataSetX.lineWidth = 2f

        val dataSetY = LineDataSet(entriesY, "Y")
        dataSetY.color = Color.BLUE
        dataSetY.setDrawCircles(false)
        dataSetY.lineWidth = 2f

        val dataSetZ = LineDataSet(entriesZ, "Z")
        dataSetZ.color = Color.YELLOW
        dataSetZ.setDrawCircles(false)
        dataSetZ.lineWidth = 2f

        val lineData = LineData(dataSetX, dataSetY, dataSetZ)
        lineChart.data = lineData

        // X eksenini yapılandır
        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.labelRotationAngle = 45f

        // Grafiğe CustomMarker ekle
        val marker = CustomMarker(this, sensorDataList)
        lineChart.marker = marker

        // Grafiği güncelle
        lineChart.invalidate()
    }

    private fun readSensorDataFromFile(fileName: String): List<SensorData> {
        val sensorDataList = mutableListOf<SensorData>()
        val file = File(getExternalFilesDir(null), fileName)

        try {
            file.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val parts = line.split(",")
                    if (parts.size == 4) {
                        try {
                            val timestamp = parts[0].toLong()
                            val x = parts[1].toFloat()
                            val y = parts[2].toFloat()
                            val z = parts[3].toFloat()
                            sensorDataList.add(SensorData(timestamp, x, y, z))
                        } catch (e: NumberFormatException) {
                            Log.e("DataDetailActivity", "Veri ayrıştırma hatası: $line", e)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            Log.e("DataDetailActivity", "Dosya okuma hatası: $fileName", e)
        }

        return sensorDataList
    }
}