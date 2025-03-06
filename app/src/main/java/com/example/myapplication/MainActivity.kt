package com.example.myapplication

import android.content.Intent
import android.graphics.Color
import android.graphics.DashPathEffect
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
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
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var lineChart: LineChart
    private lateinit var dataSetX: LineDataSet
    private lateinit var dataSetY: LineDataSet
    private lateinit var dataSetZ: LineDataSet
    private val entriesX = ArrayList<Entry>()
    private val entriesY = ArrayList<Entry>()
    private val entriesZ = ArrayList<Entry>()
    private var isRunning = false
    private lateinit var dbHelper: DatabaseHelper
    private var fileName: String = ""
    private var startTime: Long = 0L

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
        setContentView(R.layout.activity_main)

        // LineChart referansını al (XML'de id'si lineChart olarak değiştirilmeli)
        lineChart = findViewById(R.id.lineChart)

        // SensorManager ve sensor başlat
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Veritabanı yardımcısını başlat
        dbHelper = DatabaseHelper(this)

        // Chart'ı yapılandır
        setupChart()

        // Butonları yapılandır
        val startButton = findViewById<Button>(R.id.startButton)
        val stopButton = findViewById<Button>(R.id.stopButton)
        val showDataButton = findViewById<Button>(R.id.showDataButton)

        startButton.setOnClickListener {
            if (!isRunning) {
                startRecording()
            }
        }

        stopButton.setOnClickListener {
            if (isRunning) {
                stopRecording()
            }
        }

        showDataButton.setOnClickListener {
            val intent = Intent(this, DataListActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupChart() {
        // Grafik arkaplanını ve genel görünümü ayarla
        lineChart.setBackgroundColor(COLOR_BACKGROUND)
        lineChart.description.isEnabled = false
        lineChart.setDrawGridBackground(false)
        lineChart.setDrawBorders(true)
        lineChart.setBorderColor(COLOR_AXIS_LINE)
        lineChart.setBorderWidth(2f)

        // Kenar boşluklarını ayarla
        lineChart.setExtraOffsets(16f, 16f, 16f, 16f)

        // Dokunmatik özellikleri ve ölçeklendirmeyi ayarla
        lineChart.setTouchEnabled(true)
        lineChart.isDragEnabled = true
        lineChart.setScaleEnabled(true)
        lineChart.setPinchZoom(true)

        // X ekseni için veri seti oluştur ve görünümünü ayarla
        dataSetX = LineDataSet(entriesX, "X Ekseni").apply {
            color = COLOR_X
            lineWidth = 2.5f
            setDrawCircles(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER  // Pürüzsüz eğri
            cubicIntensity = 0.2f
            setDrawFilled(true)
            fillAlpha = 40  // Yarı saydam dolgu
            fillColor = COLOR_X
            setDrawValues(false)
            highLightColor = Color.WHITE
            setDrawHorizontalHighlightIndicator(false)
        }

        // Y ekseni için veri seti oluştur ve görünümünü ayarla
        dataSetY = LineDataSet(entriesY, "Y Ekseni").apply {
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

        // Z ekseni için veri seti oluştur ve görünümünü ayarla
        dataSetZ = LineDataSet(entriesZ, "Z Ekseni").apply {
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

        // LineData oluştur ve veri setlerini ekle
        val lineData = LineData(dataSetX, dataSetY, dataSetZ)
        lineChart.data = lineData

        // Zaman etiketleri için formatlayıcı
        val timeFormatter = object : ValueFormatter() {
            private val dateFormat = SimpleDateFormat("mm:ss", Locale.getDefault())

            override fun getFormattedValue(value: Float): String {
                // value saniye cinsinden zaman
                return String.format("%.1fs", value)
            }
        }

        // X eksenini yapılandır
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

        // Sol Y eksenini yapılandır
        lineChart.axisLeft.apply {
            textColor = COLOR_TEXT
            axisLineColor = COLOR_AXIS_LINE
            gridColor = COLOR_GRID
            setDrawGridLines(true)
            isEnabled = true
            axisLineWidth = 2f
            gridLineWidth = 0.7f

            // Y ekseni sınırlarını ayarla (-12 ile 12 m/s² tipik değerlerdir)
            axisMinimum = -12f
            axisMaximum = 12f
        }

        // Sağ Y eksenini devre dışı bırak
        lineChart.axisRight.isEnabled = false

        // Grafik açıklaması (legend) ayarları
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

        // Grafiği yenile
        lineChart.invalidate()
    }

    private fun startRecording() {
        // Grafik verilerini temizle
        entriesX.clear()
        entriesY.clear()
        entriesZ.clear()

        // Veritabanını temizle ve kayıt başlat
        dbHelper.clearAllData()

        // Başlangıç zamanını kaydet
        startTime = System.currentTimeMillis()

        // Kayıt durumunu güncelle
        isRunning = true

        // Sensör dinlemeyi başlat
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)

        // Verileri güncelle
        lineChart.data.notifyDataChanged()
        lineChart.notifyDataSetChanged()
        lineChart.invalidate()
    }

    private fun stopRecording() {
        isRunning = false
        sensorManager.unregisterListener(this)

        // Dosya adını zaman damgasıyla oluştur
        val timestamp = System.currentTimeMillis()
        fileName = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date(timestamp)) + ".txt"
        val file = File(getExternalFilesDir(null), fileName)

        try {
            if (!file.exists()) {
                file.createNewFile()
            }

            // Veritabanındaki verileri dosyaya yaz
            val sensorDataList = dbHelper.getAllSensorData()
            file.bufferedWriter().use { out ->
                sensorDataList.forEach { sensorData ->
                    out.write("${sensorData.timestamp},${sensorData.x},${sensorData.y},${sensorData.z}\n")
                }
            }
            Log.d("MainActivity", "Veri kaydedildi: $fileName")
        } catch (e: IOException) {
            Log.e("MainActivity", "Dosya yazma hatası: ${e.message}")
        }
    }

    private fun updateChart(timestamp: Long, x: Float, y: Float, z: Float) {
        // Zaman değerini saniye cinsinden hesapla (başlangıçtan itibaren)
        val timeInSeconds = (timestamp - startTime) / 1000f

        // Verileri grafik veri setlerine ekle
        entriesX.add(Entry(timeInSeconds, x))
        entriesY.add(Entry(timeInSeconds, y))
        entriesZ.add(Entry(timeInSeconds, z))

        // Veri setlerini güncelle
        dataSetX.notifyDataSetChanged()
        dataSetY.notifyDataSetChanged()
        dataSetZ.notifyDataSetChanged()
        lineChart.data.notifyDataChanged()

        // Grafiğin görünür kısmını güncelle
        lineChart.setVisibleXRangeMaximum(10f) // Son 10 saniyelik veriyi göster
        lineChart.moveViewToX(timeInSeconds - 0.5f) // Grafiği en son veri noktasına kaydır

        // Grafiği yenile
        lineChart.invalidate()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // İşlem yapılmıyor
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (isRunning && event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val timestamp = System.currentTimeMillis()
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            // Değerleri kaydet
            dbHelper.addSensorData(timestamp, x, y, z)

            // Grafik görüntüsünü güncelle
            updateChart(timestamp, x, y, z)
        }
    }

    override fun onResume() {
        super.onResume()
        if (isRunning) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }
}