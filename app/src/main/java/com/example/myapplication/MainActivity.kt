package com.example.myapplication

import android.content.Intent
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

class MainActivity : AppCompatActivity(), SensorEventListener {

    // Grafik Konfigürasyonları
    private val Y_AXIS_MIN = -20f
    private val Y_AXIS_MAX = 20f
    private val VISIBLE_X_RANGE = 30f
    private val MAX_DATA_POINTS = 500
    private val UPDATE_INTERVAL_MS = 16L // 60 FPS

    // Sensör ve Grafik Bileşenleri
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var chart: LineChart
    private lateinit var dbHelper: DatabaseHelper

    // Veri Kümeleri
    private val entriesX = ArrayList<Entry>()
    private val entriesY = ArrayList<Entry>()
    private val entriesZ = ArrayList<Entry>()
    private lateinit var dataSetX: LineDataSet
    private lateinit var dataSetY: LineDataSet
    private lateinit var dataSetZ: LineDataSet

    // Zaman Yönetimi
    private var startTime = 0L
    private var isRunning = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeComponents()
        configureChart()
        setupButtons()
    }

    private fun initializeComponents() {
        chart = findViewById(R.id.chart)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        dbHelper = DatabaseHelper(this)
    }

    private fun configureChart() {
        // Veri Setleri Oluşturma
        dataSetX = createDataSet(entriesX, "X Ekseni", Color.RED)
        dataSetY = createDataSet(entriesY, "Y Ekseni", Color.BLUE)
        dataSetZ = createDataSet(entriesZ, "Z Ekseni", Color.GREEN)

        // Grafik Genel Ayarları
        with(chart) {
            data = LineData(dataSetX, dataSetY, dataSetZ)
            description.isEnabled = false
            setTouchEnabled(true)
            setPinchZoom(true)
            setDrawGridBackground(false)
            setViewPortOffsets(50f, 30f, 50f, 30f)

            // X Ekseni Ayarları
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                axisMinimum = 0f
                valueFormatter = TimeAxisFormatter()
                setDrawGridLines(false)
                textColor = Color.WHITE
            }

            // Y Ekseni Ayarları
            axisLeft.apply {
                axisMinimum = Y_AXIS_MIN
                axisMaximum = Y_AXIS_MAX
                granularity = 5f
                textColor = Color.WHITE
            }
            axisRight.isEnabled = false

            // Legend Ayarları
            legend.apply {
                verticalAlignment = Legend.LegendVerticalAlignment.TOP
                horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
                orientation = Legend.LegendOrientation.HORIZONTAL
                textColor = Color.WHITE
            }

            setVisibleXRangeMaximum(VISIBLE_X_RANGE)
            animateXY(1000, 1000)
        }
    }

    private fun createDataSet(entries: ArrayList<Entry>, label: String, color: Int): LineDataSet {
        return LineDataSet(entries, label).apply {
            this.color = color
            setDrawCircles(false)
            lineWidth = 1.5f
            setDrawValues(false)
            mode = LineDataSet.Mode.LINEAR
        }
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.startButton).setOnClickListener {
            if (!isRunning) startDataCollection()
        }

        findViewById<Button>(R.id.stopButton).setOnClickListener {
            if (isRunning) stopDataCollection()
        }

        findViewById<Button>(R.id.showDataButton).setOnClickListener {
            startActivity(Intent(this, DataListActivity::class.java))
        }
    }

    private fun startDataCollection() {
        isRunning = true
        startTime = System.currentTimeMillis()
        dbHelper.clearAllData()
        resetChart()
        handler.post(updateRunnable)
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
    }

    private fun stopDataCollection() {
        isRunning = false
        handler.removeCallbacks(updateRunnable)
        sensorManager.unregisterListener(this)
        saveDataToFile()
    }

    private fun resetChart() {
        entriesX.clear()
        entriesY.clear()
        entriesZ.clear()
        chart.clear()
        chart.invalidate()
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                updateChartView()
                handler.postDelayed(this, UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun updateChartView() {
        val lastX = entriesX.lastOrNull()?.x ?: 0f
        chart.moveViewToX(lastX)
        chart.notifyDataSetChanged()
        chart.invalidate()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!isRunning || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val timestamp = System.currentTimeMillis()
        val x = event.values[0].coerceIn(Y_AXIS_MIN, Y_AXIS_MAX)
        val y = event.values[1].coerceIn(Y_AXIS_MIN, Y_AXIS_MAX)
        val z = event.values[2].coerceIn(Y_AXIS_MIN, Y_AXIS_MAX)

        runOnUiThread {
            val timeSeconds = (timestamp - startTime).toFloat() / 1000f
            addDataPoint(timeSeconds, x, y, z)
            dbHelper.addSensorData(timestamp, x, y, z)
        }
    }

    private fun addDataPoint(time: Float, x: Float, y: Float, z: Float) {
        addEntry(entriesX, dataSetX, time, x)
        addEntry(entriesY, dataSetY, time, y)
        addEntry(entriesZ, dataSetZ, time, z)

        chart.data?.notifyDataChanged()
        chart.xAxis.axisMaximum = max(chart.xAxis.axisMaximum, time)
        chart.xAxis.axisMinimum = max(0f, time - VISIBLE_X_RANGE)
    }

    private fun addEntry(entries: MutableList<Entry>, dataSet: LineDataSet, x: Float, y: Float) {
        entries.add(Entry(x, y))
        if (entries.size > MAX_DATA_POINTS) {
            entries.removeAt(0)
            dataSet.notifyDataSetChanged()
        }
    }

    private fun saveDataToFile() {
        Thread {
            try {
                val fileName = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                    .format(Date()) + ".csv"

                File(getExternalFilesDir(null), fileName).bufferedWriter().use { writer ->
                    dbHelper.getAllSensorData().forEach {
                        writer.write("${it.timestamp},${it.x},${it.y},${it.z}\n")
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Veri kaydetme hatası: ${e.localizedMessage}")
            }
        }.start()
    }

    private inner class TimeAxisFormatter : ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            return "${abs(value.toInt())}s"
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onPause() {
        super.onPause()
        if (isRunning) stopDataCollection()
    }
}