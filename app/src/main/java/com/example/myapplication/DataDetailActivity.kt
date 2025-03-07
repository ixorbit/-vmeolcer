package com.example.myapplication

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
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
    private lateinit var chartCardView: CardView
    private lateinit var noDataView: View
    private lateinit var dataFileName: String

    // Modern renk şeması
    private val COLOR_X = Color.rgb(255, 89, 94)  // Parlak kırmızı
    private val COLOR_Y = Color.rgb(138, 255, 138)  // Parlak yeşil
    private val COLOR_Z = Color.rgb(119, 210, 255)  // Parlak mavi
    private val COLOR_BACKGROUND = Color.rgb(18, 18, 18)  // Koyu gri arka plan
    private val COLOR_GRID = Color.rgb(50, 50, 50)  // Izgara çizgileri
    private val COLOR_TEXT = Color.rgb(200, 200, 200)  // Metin rengi
    private val COLOR_AXIS_LINE = Color.rgb(100, 100, 100)  // Eksen çizgisi
    private val COLOR_CARD_BACKGROUND = Color.rgb(30, 30, 30)  // Kart arkaplan rengi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_detail)

        // UI elemanlarını bağla
        recyclerView = findViewById(R.id.recyclerView)
        lineChart = findViewById(R.id.lineChart)
        chartCardView = findViewById(R.id.chartCardView)
        noDataView = findViewById(R.id.noDataView)

        // Toolbar ayarla
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            elevation = 8f
        }

        // Dosya adını Intent'ten al
        dataFileName = intent.getStringExtra("FILE_NAME") ?: ""
        supportActionBar?.title = dataFileName

        // CardView'ı modern görünüm için ayarla
        chartCardView.apply {
            radius = 16f
            cardElevation = 8f
            setCardBackgroundColor(COLOR_CARD_BACKGROUND)
        }

        // Başlangıçta animasyon ekle
        chartCardView.alpha = 0f
        chartCardView.visibility = View.VISIBLE
        chartCardView.animate().alpha(1f).setDuration(500).setInterpolator(AccelerateDecelerateInterpolator()).start()

        // Dosyadan verileri oku
        val sensorDataList = readSensorDataFromFile(dataFileName)

        // Eğer veriler başarıyla okunduysa
        if (sensorDataList.isNotEmpty()) {
            // Recyclerview'ı ayarla ve verileri göster
            setupRecyclerView(sensorDataList)

            // Grafiği ayarla
            if (sensorDataList.size >= 2) {
                setupChart(sensorDataList)
                noDataView.visibility = View.GONE
            } else {
                showNoDataUI("Grafik için en az 2 veri noktası gerekiyor")
            }
        } else {
            showNoDataUI("Dosya okunamadı veya boş dosya")
        }
    }

    private fun setupRecyclerView(sensorDataList: List<SensorData>) {
        val adapter = SensorDataAdapter(sensorDataList)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Animasyon ekle
        recyclerView.alpha = 0f
        recyclerView.animate().alpha(1f).setDuration(500).setStartDelay(300).start()
    }

    private fun showNoDataUI(message: String) {
        // Grafik kartını gizle
        chartCardView.visibility = View.GONE

        // Veri yok mesajını göster
        noDataView.visibility = View.VISIBLE
        val noDataTextView = noDataView.findViewById<TextView>(R.id.noDataTextView)
        noDataTextView.text = message

        // Kullanıcıya bilgi ver
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun setupChart(sensorDataList: List<SensorData>) {
        try {
            // Veri doğrulama
            if (sensorDataList.size < 2) {
                lineChart.setNoDataText("Yetersiz veri (en az 2 veri noktası gerekiyor)")
                lineChart.setNoDataTextColor(COLOR_TEXT)
                return
            }

            // Grafik arka planı ve genel görünümü ayarla
            lineChart.setBackgroundColor(COLOR_BACKGROUND)
            lineChart.description.isEnabled = false
            lineChart.setDrawGridBackground(false)
            lineChart.setDrawBorders(true)
            lineChart.setBorderColor(COLOR_AXIS_LINE)
            lineChart.setBorderWidth(2f)

            // Grafik kenar boşluklarını ayarla
            lineChart.setExtraOffsets(16f, 16f, 16f, 16f)

            // Dokunma özelliklerini ve ölçeklemeyi yapılandır
            lineChart.setTouchEnabled(true)
            lineChart.isDragEnabled = true
            lineChart.setScaleEnabled(true)
            lineChart.setPinchZoom(true)

            // X, Y, Z eksenleri için Entry listelerini oluştur
            val entriesX = ArrayList<Entry>()
            val entriesY = ArrayList<Entry>()
            val entriesZ = ArrayList<Entry>()

            // İlk zaman damgasını referans olarak kullan
            val firstTimestamp = sensorDataList.firstOrNull()?.timestamp ?: 0L

            // Veri noktalarını güvenli bir şekilde ekle
            for (sensorData in sensorDataList) {
                try {
                    // Zaman damgalarını saniyelere dönüştür
                    val timeInSeconds = (sensorData.timestamp - firstTimestamp) / 1000f

                    // Yalnızca geçerli değerleri ekle (NaN ve Sonsuz değerleri filtrele)
                    if (timeInSeconds.isFinite() &&
                        sensorData.x.isFinite() &&
                        sensorData.y.isFinite() &&
                        sensorData.z.isFinite()) {
                        entriesX.add(Entry(timeInSeconds, sensorData.x))
                        entriesY.add(Entry(timeInSeconds, sensorData.y))
                        entriesZ.add(Entry(timeInSeconds, sensorData.z))
                    }
                } catch (e: Exception) {
                    Log.e("DataDetailActivity", "Veri noktası ekleme hatası", e)
                }
            }

            // Veri doğrulama - veri kümeleri boşsa erken çık
            if (entriesX.size < 2 || entriesY.size < 2 || entriesZ.size < 2) {
                lineChart.setNoDataText("Yetersiz geçerli veri noktası")
                lineChart.setNoDataTextColor(COLOR_TEXT)
                return
            }

            // Düzgün çizim için girişleri X değerine göre sırala
            entriesX.sortBy { it.x }
            entriesY.sortBy { it.x }
            entriesZ.sortBy { it.x }

            // X ekseni için veri kümesi oluştur ve görünümü yapılandır
            val dataSetX = LineDataSet(entriesX, "X Ekseni").apply {
                color = COLOR_X
                lineWidth = 2.5f
                setDrawCircles(false)
                mode = LineDataSet.Mode.CUBIC_BEZIER  // Düzgün eğri
                cubicIntensity = 0.2f
                setDrawFilled(true)
                fillAlpha = 40  // Yarı saydam dolgu
                fillColor = COLOR_X
                setDrawValues(false)
                highLightColor = Color.WHITE
                setDrawHorizontalHighlightIndicator(false)
            }

            // Y ekseni için veri kümesi oluştur ve görünümü yapılandır
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

            // Z ekseni için veri kümesi oluştur ve görünümü yapılandır
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

            // LineData oluştur ve veri kümelerini ekle
            val lineData = LineData(dataSetX, dataSetY, dataSetZ)
            lineChart.data = lineData

            // Zaman etiketleri için biçimlendirici
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

                // Y ekseni limitlerini bir tamponla ayarla
                val buffer = 1.0f  // Ekstra alan ekle
                val minValues = sensorDataList.mapNotNull {
                    minOf(it.x, it.y, it.z).takeIf { it.isFinite() }
                }
                val maxValues = sensorDataList.mapNotNull {
                    maxOf(it.x, it.y, it.z).takeIf { it.isFinite() }
                }

                // Yalnızca geçerli min/max değerlerimiz varsa eksen limitlerini ayarla
                if (minValues.isNotEmpty() && maxValues.isNotEmpty()) {
                    val minY = minValues.minOrNull()!! - buffer
                    val maxY = maxValues.maxOrNull()!! + buffer
                    axisMinimum = minY
                    axisMaximum = maxY
                } else {
                    // Verilerden hesaplayamazsak varsayılan limitler
                    axisMinimum = -12f
                    axisMaximum = 12f
                }
            }

            // Sağ Y eksenini devre dışı bırak
            lineChart.axisRight.isEnabled = false

            // Grafik için özel işaretçi ekle
            val marker = CustomMarker(this, sensorDataList)
            lineChart.marker = marker

            // Grafik açıklamasını yapılandır
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
            if (!file.exists() || !file.canRead()) {
                Log.e("DataDetailActivity", "Dosya bulunamadı veya okunamıyor: $fileName")
                Toast.makeText(this, "Dosya bulunamadı: $fileName", Toast.LENGTH_SHORT).show()
                return emptyList()
            }

            file.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (line.isNotBlank()) {
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
            }
        } catch (e: IOException) {
            Log.e("DataDetailActivity", "Dosya okuma hatası: $fileName", e)
            Toast.makeText(this, "Dosya okuma hatası: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        return sensorDataList
    }

    // Geri düğmesi için destek
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    // Özel animasyonlu geri dönüş
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}