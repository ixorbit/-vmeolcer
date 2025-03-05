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
        val entriesZ = ArrayList<Entry>()

        sensorDataList.forEach { sensorData ->
            entriesZ.add(Entry(sensorData.timestamp.toFloat(), sensorData.z))
        }
        Log.d("DataDetailActivity", "entriesZ: $entriesZ")

        val dataSetZ = LineDataSet(entriesZ, "Z")
        dataSetZ.color = Color.YELLOW
        dataSetZ.setDrawCircles(false)
        dataSetZ.lineWidth = 2f

        val lineData = LineData()
        lineData.addDataSet(dataSetZ)

        lineChart.data = lineData

        // X eksenini yapılandır
        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.labelRotationAngle = 45f
        xAxis.isEnabled = true // X eksenini görünür yap
        xAxis.axisMinimum = 0f // X ekseninin 0 dan başlamasını sağla
        if (sensorDataList.isNotEmpty()) {
            xAxis.axisMinimum = sensorDataList.first().timestamp.toFloat()
        }

        // Y eksenini yapılandır
        val yAxisLeft = lineChart.axisLeft
        yAxisLeft.isEnabled = true // Sol Y eksenini görünür yap

        val yAxisRight = lineChart.axisRight
        yAxisRight.isEnabled = false // Sağ Y eksenini görünmez yap

        // Grafiğe CustomMarker ekle
        val marker = CustomMarker(this, sensorDataList)
        lineChart.marker = marker

        // Grafik ayarları
        lineChart.setAutoScaleMinMaxEnabled(true)
        lineChart.setDragEnabled(true)
        lineChart.setScaleEnabled(true)
        lineChart.setTouchEnabled(true)

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
        if (sensorDataList.isNotEmpty()) {
            Log.d("DataDetailActivity", "Min Timestamp: ${sensorDataList.minOf { it.timestamp }}")
            Log.d("DataDetailActivity", "Max Timestamp: ${sensorDataList.maxOf { it.timestamp }}")
            Log.d("DataDetailActivity", "Min X: ${sensorDataList.minOf { it.x }}")
            Log.d("DataDetailActivity", "Max X: ${sensorDataList.maxOf { it.x }}")
            Log.d("DataDetailActivity", "Min Y: ${sensorDataList.minOf { it.y }}")
            Log.d("DataDetailActivity", "Max Y: ${sensorDataList.maxOf { it.y }}")
            Log.d("DataDetailActivity", "Min Z: ${sensorDataList.minOf { it.z }}")
            Log.d("DataDetailActivity", "Max Z: ${sensorDataList.maxOf { it.z }}")
        }

        return sensorDataList
    }
}