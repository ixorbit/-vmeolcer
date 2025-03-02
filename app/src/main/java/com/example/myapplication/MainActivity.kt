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
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var chart: LineChart
    private lateinit var dataSetX: LineDataSet
    private lateinit var dataSetY: LineDataSet
    private lateinit var dataSetZ: LineDataSet
    private val entriesX = ArrayList<Entry>()
    private val entriesY = ArrayList<Entry>()
    private val entriesZ = ArrayList<Entry>()
    private var startTime = 0L
    private var isRunning = false
    private lateinit var dbHelper: DatabaseHelper

    private val handler = Handler(Looper.getMainLooper())
    private val bufferSize = 500
    private val updateInterval = 16L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeComponents()
        setupChart()
        setupButtons()
    }

    private fun initializeComponents() {
        chart = findViewById(R.id.chart)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        dbHelper = DatabaseHelper(this)
    }

    private fun setupChart() {
        configureChart()
        chart.setNoDataText("Veri bekleniyor...")
        chart.setNoDataTextColor(Color.GRAY)
    }

    private fun configureChart() {
        dataSetX = LineDataSet(entriesX, "X Ekseni").apply {
            color = Color.RED
            setDrawCircles(false)
            lineWidth = 1.5f
            setDrawValues(false)
        }

        dataSetY = LineDataSet(entriesY, "Y Ekseni").apply {
            color = Color.BLUE
            setDrawCircles(false)
            lineWidth = 1.5f
            setDrawValues(false)
        }

        dataSetZ = LineDataSet(entriesZ, "Z Ekseni").apply {
            color = Color.GREEN
            setDrawCircles(false)
            lineWidth = 1.5f
            setDrawValues(false)
        }

        chart.apply {
            data = LineData(dataSetX, dataSetY, dataSetZ)
            description.isEnabled = false
            setTouchEnabled(true)
            setPinchZoom(true)

            axisLeft.apply {
                setAxisMinimum(-20f)
                setAxisMaximum(20f)
                granularity = 5f
            }

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return "${value.toInt()}s"
                    }
                }
            }

            axisRight.isEnabled = false
            legend.isWordWrapEnabled = true
        }
    }

    private fun setupButtons() {
        val startButton = findViewById<Button>(R.id.startButton)
        val stopButton = findViewById<Button>(R.id.stopButton)
        val showDataButton = findViewById<Button>(R.id.showDataButton)

        startButton.setOnClickListener {
            if (!isRunning) {
                startDataCollection()
            }
        }

        stopButton.setOnClickListener {
            if (isRunning) {
                stopDataCollection()
            }
        }

        showDataButton.setOnClickListener {
            startActivity(Intent(this, DataListActivity::class.java))
        }
    }

    private fun startDataCollection() {
        isRunning = true
        startTime = System.currentTimeMillis()
        dbHelper.clearAllData()
        resetChartData()
        handler.post(updateRunnable)

        // Başlangıç veri noktası ekle
        runOnUiThread {
            entriesX.add(Entry(0f, 0f))
            entriesY.add(Entry(0f, 0f))
            entriesZ.add(Entry(0f, 0f))
        }
    }

    private fun stopDataCollection() {
        isRunning = false
        handler.removeCallbacks(updateRunnable)
        saveDataToFile()
    }

    private fun resetChartData() {
        entriesX.clear()
        entriesY.clear()
        entriesZ.clear()
        chart.data?.clearValues()
        chart.invalidate()
    }

    private fun updateChart(x: Float, y: Float, z: Float) {
        val currentTime = (System.currentTimeMillis() - startTime).toFloat() / 1000f

        runOnUiThread {
            entriesX.add(Entry(currentTime, x))
            entriesY.add(Entry(currentTime, y))
            entriesZ.add(Entry(currentTime, z))

            // Veri setlerini manuel güncelle
            dataSetX.notifyDataSetChanged()
            dataSetY.notifyDataSetChanged()
            dataSetZ.notifyDataSetChanged()

            // Grafik sınırlarını otomatik ayarla
            chart.xAxis.axisMaximum = entriesX.last().x + 5f
            chart.data?.notifyDataChanged()
            chart.invalidate()
        }
    }

    private fun saveDataToFile() {
        Thread {
            try {
                val timestamp = System.currentTimeMillis()
                val fileName = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                    .format(Date(timestamp)) + ".txt"
                val file = File(getExternalFilesDir(null), fileName).apply {
                    if (!exists()) createNewFile()
                }

                dbHelper.getAllSensorData().let { data ->
                    file.bufferedWriter().use { writer ->
                        data.forEach {
                            writer.write("${it.timestamp},${it.x},${it.y},${it.z}\n")
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e("MainActivity", "Dosya hatası: ${e.message}")
            }
        }.start()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.takeIf { isRunning && it.sensor.type == Sensor.TYPE_ACCELEROMETER }?.let {
            val x = it.values[0]
            val y = it.values[1]
            val z = it.values[2]

            dbHelper.addSensorData(System.currentTimeMillis(), x, y, z)
            updateChart(x, y, z)
            Log.d("SENSOR_DATA", "X: $x, Y: $y, Z: $z")
        }
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        handler.removeCallbacks(updateRunnable)
    }

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (entriesX.size > 1) {
                chart.data?.let {
                    it.notifyDataChanged()
                    chart.notifyDataSetChanged()
                    chart.moveViewToX(it.xMax)
                    chart.invalidate()
                }
            }
            handler.postDelayed(this, updateInterval)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}