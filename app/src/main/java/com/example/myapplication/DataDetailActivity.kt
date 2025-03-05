package com.example.myapplication

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class DataDetailActivity : AppCompatActivity() {

    private lateinit var chart: LineChart
    private lateinit var sensorDataList: List<SensorData>
    private val dateFormat = SimpleDateFormat("ss.SSS", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_detail)

        // Grafik ve RecyclerView tanımlamaları
        chart = findViewById(R.id.Graph)

        val fileName = intent.getStringExtra("FILE_NAME") ?: run {
            showError("Dosya bulunamadı!")
            finish()
            return
        }

        sensorDataList = readSensorDataFromFile(fileName)
        if (sensorDataList.isEmpty()) {
            showError("Geçersiz veri!")
            finish()
            return
        }

        setupChart()
    }

    private fun readSensorDataFromFile(fileName: String): List<SensorData> {
        return File(getExternalFilesDir(null), fileName).readLines().mapNotNull { line ->
            line.split(",").takeIf { it.size == 4 }?.let {
                try {
                    SensorData(
                        it[0].toLong(),
                        it[1].toFloat(),
                        it[2].toFloat(),
                        it[3].toFloat()
                    )
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }
    }

    private fun setupChart() {
        val entries = sensorDataList.map { data ->
            Entry(
                (data.timestamp - sensorDataList.first().timestamp).toFloat() / 1000f,
                data.x
            )
        }

        val dataSet = LineDataSet(entries, "İvme Değerleri").apply {
            color = Color.RED
            lineWidth = 2f
            setDrawCircles(true)
            circleRadius = 4f
        }

        with(chart) {
            this.data = LineData(dataSet)
            description.isEnabled = false
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float) = "${value.toInt()}s"
                }
            }
            animateX(1000)
            invalidate()
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Log.e("DataDetail", message)
    }
}