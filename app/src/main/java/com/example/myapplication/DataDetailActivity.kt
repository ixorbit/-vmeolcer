package com.example.myapplication

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class DataDetailActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var lineChart: LineChart

    // Canlı renkler tanımla
    private val COLOR_X = Color.rgb(255, 89, 94)  // Parlak kırmızı
    private val COLOR_Y = Color.rgb(138, 255, 138)  // Parlak yeşil
    private val COLOR_Z = Color.rgb(119, 210, 255)  // Parlak mavi
    private val COLOR_BACKGROUND = Color.rgb(18, 18, 18)  // Koyu gri arka plan
    private val COLOR_GRID = Color.rgb(50, 50, 50)  // Izgara çizgileri
    private val COLOR_TEXT = Color.rgb(200, 200, 200)  // Metin rengi
    private val COLOR_AXIS_LINE = Color.rgb(100, 100, 100)  // Eksen çizgisi

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
        if (sensorDataList.size >= 2) {  // En az 2 veri noktası olmalı
            setupChart(sensorDataList)
        } else {
            // Yetersiz veri durumunda kullanıcıya bilgi ver
            lineChart.setNoDataText("Yetersiz veri (en az 2 veri noktası gerekiyor)")
            lineChart.setNoDataTextColor(COLOR_TEXT)
            Toast.makeText(this, "Grafik çizimi için yetersiz veri", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupChart(sensorDataList: List<SensorData>) {
        try {
            // Data validation
            if (sensorDataList.size < 2) {
                lineChart.setNoDataText("Yetersiz veri (en az 2 veri noktası gerekiyor)")
                lineChart.setNoDataTextColor(COLOR_TEXT)
                return
            }

            // Set chart background and general appearance
            lineChart.setBackgroundColor(COLOR_BACKGROUND)
            lineChart.description.isEnabled = false
            lineChart.setDrawGridBackground(false)
            lineChart.setDrawBorders(true)
            lineChart.setBorderColor(COLOR_AXIS_LINE)
            lineChart.setBorderWidth(2f)

            // Set margins
            lineChart.setExtraOffsets(16f, 16f, 16f, 16f)

            // Configure touch features and scaling
            lineChart.setTouchEnabled(true)
            lineChart.isDragEnabled = true
            lineChart.setScaleEnabled(true)
            lineChart.setPinchZoom(true)

            // Create Entry lists for X, Y, and Z axes
            val entriesX = ArrayList<Entry>()
            val entriesY = ArrayList<Entry>()
            val entriesZ = ArrayList<Entry>()

            // Use first timestamp as reference
            val firstTimestamp = sensorDataList.firstOrNull()?.timestamp ?: 0L

            // Safely add data points
            for (sensorData in sensorDataList) {
                try {
                    // Convert timestamps to seconds
                    val timeInSeconds = (sensorData.timestamp - firstTimestamp) / 1000f

                    // Only add valid values (filter NaN and Infinite values)
                    if (timeInSeconds.isFinite() &&
                        sensorData.x.isFinite() &&
                        sensorData.y.isFinite() &&
                        sensorData.z.isFinite()) {
                        entriesX.add(Entry(timeInSeconds, sensorData.x))
                        entriesY.add(Entry(timeInSeconds, sensorData.y))
                        entriesZ.add(Entry(timeInSeconds, sensorData.z))
                    }
                } catch (e: Exception) {
                    Log.e("DataDetailActivity", "Error adding data point", e)
                }
            }

            // Data validation - early exit for empty data sets
            if (entriesX.size < 2 || entriesY.size < 2 || entriesZ.size < 2) {
                lineChart.setNoDataText("Yetersiz geçerli veri noktası")
                lineChart.setNoDataTextColor(COLOR_TEXT)
                return
            }

            // Sort entries by X value to ensure proper line drawing
            entriesX.sortBy { it.x }
            entriesY.sortBy { it.x }
            entriesZ.sortBy { it.x }

            // Create data set for X axis and configure appearance
            val dataSetX = LineDataSet(entriesX, "X Ekseni").apply {
                color = COLOR_X
                lineWidth = 2.5f
                setDrawCircles(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER  // Smooth curve
                cubicIntensity = 0.2f
                setDrawFilled(true)
                fillAlpha = 40  // Semi-transparent fill
                fillColor = COLOR_X
                setDrawValues(false)
                highLightColor = Color.WHITE
                setDrawHorizontalHighlightIndicator(false)
            }

            // Create data set for Y axis and configure appearance
            val dataSetY = LineDataSet(entriesY, "Y Ekseni").apply {
                color = COLOR_Y
                lineWidth = 2.5f
                setDrawCircles(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
                cubicIntensity = 0.2f
                setDrawFilled(true)
                fillAlpha = 40
                fillColor = COLOR_Y
                setDrawValues(false)
                highLightColor = Color.WHITE
                setDrawHorizontalHighlightIndicator(false)
            }

            // Create data set for Z axis and configure appearance
            val dataSetZ = LineDataSet(entriesZ, "Z Ekseni").apply {
                color = COLOR_Z
                lineWidth = 2.5f
                setDrawCircles(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER
                cubicIntensity = 0.2f
                setDrawFilled(true)
                fillAlpha = 40
                fillColor = COLOR_Z
                setDrawValues(false)
                highLightColor = Color.WHITE
                setDrawHorizontalHighlightIndicator(false)
            }

            // Create LineData and add data sets
            val lineData = LineData(dataSetX, dataSetY, dataSetZ)
            lineChart.data = lineData

            // Formatter for time labels
            val timeFormatter = object : ValueFormatter() {
                private val dateFormat = SimpleDateFormat("mm:ss", Locale.getDefault())

                override fun getFormattedValue(value: Float): String {
                    try {
                        val timestamp = firstTimestamp + (value * 1000).toLong()
                        return dateFormat.format(Date(timestamp))
                    } catch (e: Exception) {
                        return value.toString()
                    }
                }
            }

            // Configure X axis
            lineChart.xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = COLOR_TEXT
                axisLineColor = COLOR_AXIS_LINE
                gridColor = COLOR_GRID
                setDrawGridLines(true)
                valueFormatter = timeFormatter
                labelRotationAngle = 0f
                isEnabled = true
                axisLineWidth = 2f
                gridLineWidth = 0.7f
            }

            // Configure left Y axis
            lineChart.axisLeft.apply {
                textColor = COLOR_TEXT
                axisLineColor = COLOR_AXIS_LINE
                gridColor = COLOR_GRID
                setDrawGridLines(true)
                isEnabled = true
                axisLineWidth = 2f
                gridLineWidth = 0.7f

                // Set Y axis limits with a buffer
                val buffer = 1.0f  // Add extra space
                val minValues = sensorDataList.mapNotNull {
                    minOf(it.x, it.y, it.z).takeIf { it.isFinite() }
                }
                val maxValues = sensorDataList.mapNotNull {
                    maxOf(it.x, it.y, it.z).takeIf { it.isFinite() }
                }

                // Only set axis limits if we have valid min/max values
                if (minValues.isNotEmpty() && maxValues.isNotEmpty()) {
                    val minY = minValues.minOrNull()!! - buffer
                    val maxY = maxValues.maxOrNull()!! + buffer
                    axisMinimum = minY
                    axisMaximum = maxY
                } else {
                    // Default limits if we can't calculate from data
                    axisMinimum = -12f
                    axisMaximum = 12f
                }
            }

            // Disable right Y axis
            lineChart.axisRight.isEnabled = false

            // Add custom marker to chart
            val marker = CustomMarker(this, sensorDataList)
            lineChart.marker = marker

            // Configure chart legend
            lineChart.legend.apply {
                isEnabled = true
                form = Legend.LegendForm.LINE
                textColor = COLOR_TEXT
                textSize = 12f
                xEntrySpace = 10f
                formSize = 15f
                formLineWidth = 2f
                verticalAlignment = Legend.LegendVerticalAlignment.TOP
                horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
                orientation = Legend.LegendOrientation.HORIZONTAL
                setDrawInside(true)
            }

            // Add animation
            lineChart.animateX(1500)

            // Refresh chart
            lineChart.invalidate()
        } catch (e: Exception) {
            // Safe exit in case of any error
            Log.e("DataDetailActivity", "Chart creation error", e)
            lineChart.setNoDataText("Grafik oluşturulurken hata oluştu: ${e.message}")
            lineChart.setNoDataTextColor(COLOR_TEXT)
        }
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