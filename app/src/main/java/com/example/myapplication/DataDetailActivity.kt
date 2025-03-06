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
            // Veri kontrolü
            if (sensorDataList.size < 2) {
                lineChart.setNoDataText("Yetersiz veri (en az 2 veri noktası gerekiyor)")
                lineChart.setNoDataTextColor(COLOR_TEXT)
                return
            }

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

            // X, Y ve Z eksenleri için Entry listeleri oluştur
            val entriesX = ArrayList<Entry>()
            val entriesY = ArrayList<Entry>()
            val entriesZ = ArrayList<Entry>()

            // İlk zaman damgasını referans olarak al
            val firstTimestamp = sensorDataList.firstOrNull()?.timestamp ?: 0L

            sensorDataList.forEach { sensorData ->
                // Zaman damgalarını saniyeye çevir
                val timeInSeconds = (sensorData.timestamp - firstTimestamp) / 1000f

                // Sadece geçerli değerler ekle (NaN ve Infinite değerleri filtrele)
                if (!timeInSeconds.isNaN() && !timeInSeconds.isInfinite() &&
                    !sensorData.x.isNaN() && !sensorData.x.isInfinite() &&
                    !sensorData.y.isNaN() && !sensorData.y.isInfinite() &&
                    !sensorData.z.isNaN() && !sensorData.z.isInfinite()) {
                    entriesX.add(Entry(timeInSeconds, sensorData.x))
                    entriesY.add(Entry(timeInSeconds, sensorData.y))
                    entriesZ.add(Entry(timeInSeconds, sensorData.z))
                }
            }

            // Veri kontrolü - boş veri setleri için erken çıkış
            if (entriesX.size < 2 || entriesY.size < 2 || entriesZ.size < 2) {
                lineChart.setNoDataText("Yetersiz geçerli veri noktası")
                lineChart.setNoDataTextColor(COLOR_TEXT)
                return
            }

            // X ekseni için veri seti oluştur ve görünümünü ayarla
            val dataSetX = LineDataSet(entriesX, "X Ekseni").apply {
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

            // Z ekseni için veri seti oluştur ve görünümünü ayarla
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

            // LineData oluştur ve veri setlerini ekle
            val lineData = LineData(dataSetX, dataSetY, dataSetZ)
            lineChart.data = lineData

            // Zaman etiketleri için formatlayıcı
            val timeFormatter = object : ValueFormatter() {
                private val dateFormat = SimpleDateFormat("mm:ss", Locale.getDefault())

                override fun getFormattedValue(value: Float): String {
                    val timestamp = firstTimestamp + (value * 1000).toLong()
                    return dateFormat.format(Date(timestamp))
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

                // Y ekseni sınırlarını ayarla
                val buffer = 1.0f  // Biraz ekstra boşluk
                val minY = sensorDataList.minOf { minOf(it.x, it.y, it.z) } - buffer
                val maxY = sensorDataList.maxOf { maxOf(it.x, it.y, it.z) } + buffer
                axisMinimum = minY
                axisMaximum = maxY
            }

            // Sağ Y eksenini devre dışı bırak
            lineChart.axisRight.isEnabled = false

            // Grafiğe özel işaretçi ekle
            val marker = CustomMarker(this, sensorDataList)
            lineChart.marker = marker

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

            // Animasyon ekle
            lineChart.animateX(1500)

            // Grafiği yenile
            lineChart.invalidate()
        } catch (e: Exception) {
            // Herhangi bir hata durumunda güvenli çıkış
            Log.e("DataDetailActivity", "Grafik oluşturma hatası", e)
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