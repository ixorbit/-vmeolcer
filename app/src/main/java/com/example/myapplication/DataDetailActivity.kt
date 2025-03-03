package com.example.myapplication

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.LineData
import java.io.File

class DataDetailActivity : AppCompatActivity() {

    private lateinit var chart: LineChart

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_detail)

        chart = findViewById(R.id.graph)
        val fileName = intent.getStringExtra("FILE_NAME") ?: return
        val sensorDataList = readSensorDataFromFile(fileName)
        setupChart(sensorDataList)
    }

    private fun setupChart(data: List<SensorData>) {
        val entriesX = ArrayList<Entry>()
        val entriesY = ArrayList<Entry>()
        val entriesZ = ArrayList<Entry>()

        data.forEach { sensorData ->
            val time = (sensorData.timestamp - data.first().timestamp).toFloat() / 1000f
            entriesX.add(Entry(time, sensorData.x))
            entriesY.add(Entry(time, sensorData.y))
            entriesZ.add(Entry(time, sensorData.z))
        }

        val dataSetX = LineDataSet(entriesX, "X").apply { color = Color.RED }
        val dataSetY = LineDataSet(entriesY, "Y").apply { color = Color.BLUE }
        val dataSetZ = LineDataSet(entriesZ, "Z").apply { color = Color.GREEN }

        chart.apply {
            this.data = LineData(dataSetX, dataSetY, dataSetZ)
            description.isEnabled = false
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            setVisibleXRangeMaximum(30f)
            animateX(1000)
            invalidate()
        }
    }

    private fun readSensorDataFromFile(fileName: String): List<SensorData> {
        return File(getExternalFilesDir(null), fileName).let { file ->
            if (!file.exists()) emptyList() else file.readLines().mapNotNull { line ->
                line.split(",").takeIf { it.size == 4 }?.let {
                    try {
                        SensorData(it[0].toLong(), it[1].toFloat(), it[2].toFloat(), it[3].toFloat())
                    } catch (e: NumberFormatException) {
                        Log.e("DataDetail", "Geçersiz veri: $line")
                        null
                    }
                }
            }
        }
    }
}