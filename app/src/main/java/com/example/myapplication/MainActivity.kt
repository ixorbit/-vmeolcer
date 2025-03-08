package com.example.myapplication

import android.content.Intent
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.GridLabelRenderer
import com.jjoe64.graphview.LegendRenderer
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

    // UI elements
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var showDataButton: Button
    private lateinit var statusText: TextView
    private lateinit var xValueText: TextView
    private lateinit var yValueText: TextView
    private lateinit var zValueText: TextView
    private lateinit var infoCard: CardView

    // Modern color scheme
    private val COLOR_X = Color.rgb(255, 89, 94)  // Vibrant red
    private val COLOR_Y = Color.rgb(138, 255, 138)  // Vibrant green
    private val COLOR_Z = Color.rgb(119, 210, 255)  // Vibrant blue
    private val COLOR_GRID = Color.rgb(200, 200, 200)  // Light gray for grid
    private val COLOR_BACKGROUND = Color.BLACK  // Siyah arka plan
    private val COLOR_TEXT = Color.rgb(80, 80, 100)  // Dark blue-gray

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI elements
        graph = findViewById(R.id.graph)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        showDataButton = findViewById(R.id.showDataButton)
        statusText = findViewById(R.id.statusText)
        xValueText = findViewById(R.id.xValueText)
        yValueText = findViewById(R.id.yValueText)
        zValueText = findViewById(R.id.zValueText)
        infoCard = findViewById(R.id.infoCard)

        // Initialize series with styling
        initializeGraphSeries()

        // Set up sensor manager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Initialize database helper
        dbHelper = DatabaseHelper(this)

        // Set up button listeners with animations
        setupButtonListeners()

        // Initial UI state
        updateUIState(false)
    }

    private fun initializeGraphSeries() {
        // Initialize graph series with styling
        seriesX = LineGraphSeries<DataPoint>().apply {
            color = COLOR_X
            thickness = 4
            isDrawDataPoints = true
            dataPointsRadius = 6f
            setAnimated(true)
            title = "X-Axis"
        }

        seriesY = LineGraphSeries<DataPoint>().apply {
            color = COLOR_Y
            thickness = 4
            isDrawDataPoints = true
            dataPointsRadius = 6f
            setAnimated(true)
            title = "Y-Axis"
        }

        seriesZ = LineGraphSeries<DataPoint>().apply {
            color = COLOR_Z
            thickness = 4
            isDrawDataPoints = true
            dataPointsRadius = 6f
            setAnimated(true)
            title = "Z-Axis"
        }

        // Add series to graph
        graph.addSeries(seriesX)
        graph.addSeries(seriesY)
        graph.addSeries(seriesZ)

        // Style the graph
        styleGraph()
    }

    // styleGraph fonksiyonunu güncelleyin - siyah arka plan için
    private fun styleGraph() {
        // Viewport settings
        graph.viewport.apply {
            isXAxisBoundsManual = true
            setMinX(0.0)
            setMaxX(20.0)

            isYAxisBoundsManual = true
            setMinY(-15.0)
            setMaxY(15.0)

            isScalable = true
            isScrollable = true
            setScrollableY(true)
            setScalableY(true)
        }

        // Grid label renderer styling
        graph.gridLabelRenderer.apply {
            gridColor = COLOR_GRID
            horizontalAxisTitle = "Time (samples)"
            verticalAxisTitle = "Acceleration (m/s²)"
            horizontalAxisTitleTextSize = 36f
            verticalAxisTitleTextSize = 36f
            textSize = 30f
            padding = 20
            verticalLabelsColor = Color.WHITE  // Siyah zemin için beyaz yazı
            horizontalLabelsColor = Color.WHITE  // Siyah zemin için beyaz yazı
            gridStyle = GridLabelRenderer.GridStyle.BOTH
            numHorizontalLabels = 5
            numVerticalLabels = 5
            labelsSpace = 30
        }

        // Legend renderer styling
        graph.legendRenderer.apply {
            isVisible = true
            backgroundColor = Color.argb(150, 40, 40, 40)  // Koyu, yarı saydam arkaplan
            textSize = 35f
            textColor = Color.WHITE  // Beyaz yazı
            margin = 20
            width = 0 // Auto width
            align = LegendRenderer.LegendAlign.TOP
        }

        // Secondary styling
        graph.apply {
            setBackgroundColor(COLOR_BACKGROUND)  // Siyah arka plan
            titleColor = Color.WHITE  // Beyaz başlık
            titleTextSize = 50f
            title = "Accelerometer Data"
        }
    }

    private fun setupButtonListeners() {
        startButton.setOnClickListener {
            if (!isRunning) {
                isRunning = true
                dbHelper.clearAllData()
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)

                // Animate button and status changes
                it.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start()
                stopButton.animate().alpha(1f).setDuration(300).start()
                updateUIState(true)

                // Reset graph for new recording
                resetGraph()
            }
        }

        stopButton.setOnClickListener {
            if (isRunning) {
                isRunning = false
                sensorManager.unregisterListener(this)

                // Animate button and status changes
                it.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start()
                showDataButton.animate().alpha(1f).setDuration(300).start()
                updateUIState(false)

                // Save data to file
                saveDataToFile()
            }
        }

        showDataButton.setOnClickListener {
            // Animate button press
            it.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    it.animate().scaleX(1f).scaleY(1f).setDuration(100).start()

                    // Navigate to data list activity
                    val intent = Intent(this, DataListActivity::class.java)
                    startActivity(intent)

                    // Add transition animation
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                }.start()
        }
    }

    private fun updateUIState(isRecording: Boolean) {
        // Update status text
        statusText.text = if (isRecording) "Recording..." else "Ready"
        statusText.setTextColor(if (isRecording) COLOR_X else COLOR_TEXT)

        // Update button states
        startButton.isEnabled = !isRecording
        stopButton.isEnabled = isRecording

        // showDataButton her zaman aktif olacak
        showDataButton.isEnabled = true

        // Visual feedback for active/inactive buttons
        startButton.alpha = if (isRecording) 0.5f else 1f
        stopButton.alpha = if (isRecording) 1f else 0.5f
        showDataButton.alpha = 1f  // Her zaman tam görünür

        // Show/hide info card with animation
        if (isRecording) {
            infoCard.visibility = View.VISIBLE
            infoCard.alpha = 0f
            infoCard.animate().alpha(1f).setDuration(300).start()
        } else if (infoCard.visibility == View.VISIBLE) {
            infoCard.animate().alpha(0f).setDuration(300)
                .withEndAction { infoCard.visibility = View.GONE }.start()
        }

    }

    private fun resetGraph() {
        // Reset data and graph view
        lastXValue = 0.0
        lastYValue = 0.0
        lastZValue = 0.0

        seriesX.resetData(arrayOf(DataPoint(0.0, 0.0)))
        seriesY.resetData(arrayOf(DataPoint(0.0, 0.0)))
        seriesZ.resetData(arrayOf(DataPoint(0.0, 0.0)))

        graph.viewport.setMinX(0.0)
        graph.viewport.setMaxX(20.0)
    }

    // saveDataToFile fonksiyonunu güncelleyin, showDataButton'un aktif olduğunu göstermek için
    private fun saveDataToFile() {
        val timestamp = System.currentTimeMillis()
        fileName = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date(timestamp)) + ".txt"
        val file = File(getExternalFilesDir(null), fileName)

        try {
            if (!file.exists()) {
                file.createNewFile()
            }

            val sensorDataList = dbHelper.getAllSensorData()
            file.bufferedWriter().use { out ->
                sensorDataList.forEach { sensorData ->
                    out.write("${sensorData.timestamp},${sensorData.x},${sensorData.y},${sensorData.z}\n")
                }
            }

            // Show success message
            statusText.text = "Saved as $fileName"
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))

            // Ensure the button is enabled and visible after saving data
            showDataButton.isEnabled = true
            showDataButton.alpha = 1f

        } catch (e: IOException) {
            Log.e("MainActivity", "File creation error: ${e.message}")

            // Show error message
            statusText.text = "Error saving file"
            statusText.setTextColor(Color.RED)
        }
    }

    private fun updateGraph(x: Float, y: Float, z: Float) {
        // Update graph with new data points
        seriesX.appendData(DataPoint(lastXValue++, x.toDouble()), true, 100)
        seriesY.appendData(DataPoint(lastYValue++, y.toDouble()), true, 100)
        seriesZ.appendData(DataPoint(lastZValue++, z.toDouble()), true, 100)

        // Auto-scroll viewport to show latest data
        val viewportWidth = 20.0
        val minX = if (lastXValue > viewportWidth) lastXValue - viewportWidth else 0.0
        val maxX = if (lastXValue > viewportWidth) lastXValue else viewportWidth

        graph.viewport.setMinX(minX)
        graph.viewport.setMaxX(maxX)

        // Update current values display
        xValueText.text = "X: ${"%.2f".format(x)} m/s²"
        yValueText.text = "Y: ${"%.2f".format(y)} m/s²"
        zValueText.text = "Z: ${"%.2f".format(z)} m/s²"

        // Set text colors to match graph lines
        xValueText.setTextColor(COLOR_X)
        yValueText.setTextColor(COLOR_Y)
        zValueText.setTextColor(COLOR_Z)
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