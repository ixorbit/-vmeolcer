package com.example.myapplication

import android.content.Intent
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var graph: GraphView
    private lateinit var seriesX: LineGraphSeries<DataPoint>
    private lateinit var seriesY: LineGraphSeries<DataPoint>
    private lateinit var seriesZ: LineGraphSeries<DataPoint>
    private var lastXValue = 0.0
    private var lastYValue = 0.0
    private var lastZValue = 0.0
    private var isRunning = false
    private lateinit var dbHelper: DatabaseHelper
    private var fileName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        graph = findViewById(R.id.graph)
        seriesX = LineGraphSeries()
        seriesY = LineGraphSeries()
        seriesZ = LineGraphSeries()
        graph.addSeries(seriesX)
        graph.addSeries(seriesY)
        graph.addSeries(seriesZ)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val screenWidth = resources.displayMetrics.widthPixels
        val graphWidth = screenWidth / 1
        val graphHeight = 2000

        val layoutParams = graph.layoutParams
        layoutParams.width = graphWidth
        layoutParams.height = graphHeight
        graph.layoutParams = layoutParams

        seriesX.color = Color.RED
        seriesY.color = Color.BLUE
        seriesZ.color = Color.YELLOW

        graph.viewport.isScalable = true
        graph.viewport.isScrollable = true

        val minX = 0.0
        val maxX = 100.0
        graph.viewport.setMinX(minX)
        graph.viewport.setMaxX(maxX)

        val startButton = findViewById<Button>(R.id.startButton)
        val stopButton = findViewById<Button>(R.id.stopButton)
        val showDataButton = findViewById<Button>(R.id.showDataButton)

        dbHelper = DatabaseHelper(this)

        startButton.setOnClickListener {
            if (!isRunning) {
                isRunning = true
                dbHelper.clearAllData()
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }

        stopButton.setOnClickListener {
            if (isRunning) {
                isRunning = false
                sensorManager.unregisterListener(this)
                val timestamp = System.currentTimeMillis()
                fileName = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date(timestamp)) + ".txt"
                val file = File(getExternalFilesDir(null), fileName)
                try {
                    if (!file.exists()) {
                        file.createNewFile() // Dosya yoksa oluştur
                    }
                } catch (e: IOException) {
                    Log.e("MainActivity", "Dosya oluşturma hatası: ${e.message}")
                }
                val sensorDataList = dbHelper.getAllSensorData()

                // Verileri doğrudan 'file' değişkeni tarafından temsil edilen dosyaya yaz
                file.bufferedWriter().use { out ->
                    sensorDataList.forEach { sensorData ->
                        out.write("${sensorData.timestamp},${sensorData.x},${sensorData.y},${sensorData.z}\n")
                    }
                }
            }
        }

        showDataButton.setOnClickListener {
            val intent = Intent(this, DataListActivity::class.java) // DataListActivity'yi aç
            intent.putExtra("FILE_NAME", fileName)
            startActivity(intent)
        }
    }

    private fun updateGraph(x: Float, y: Float, z: Float) {
        seriesX.appendData(DataPoint(lastXValue++, x.toDouble()), true, 100)
        seriesY.appendData(DataPoint(lastYValue++, y.toDouble()), true, 100)
        seriesZ.appendData(DataPoint(lastZValue++, z.toDouble()), true, 100)

        val viewportWidth = 20.0
        val minX = if (lastXValue > viewportWidth) lastXValue - viewportWidth else 0.0
        val maxX = if (lastXValue > viewportWidth) lastXValue else viewportWidth

        graph.viewport.isXAxisBoundsManual = true
        graph.viewport.setMinX(minX)
        graph.viewport.setMaxX(maxX)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Do nothing
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (isRunning && event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            Log.d("SENSOR_DATA", "X: $x, Y: $y, Z: $z")
            updateGraph(x, y, z)
            dbHelper.addSensorData(System.currentTimeMillis(), x, y, z)
        }
    }

    override fun onResume() {
        super.onResume()
        if(isRunning) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }
}