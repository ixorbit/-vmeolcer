package com.example.myapplication

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.IOException
import java.lang.Exception

class DataAnalyticsActivity : AppCompatActivity() {

    private lateinit var sensorDataList: List<SensorData>
    private lateinit var anomaliesAdapter: AnomaliesAdapter
    private lateinit var freeFallDetailsAdapter: FreeFallDetailsAdapter

    private lateinit var rvAnomalies: RecyclerView
    private lateinit var rvFreeFallDetails: RecyclerView
    private lateinit var freeFallCard: CardView
    private lateinit var tvNoFreeFalls: TextView
    private lateinit var freeFallChartsContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_analytics)

        // Toolbar'ı ayarla
        supportActionBar?.apply {
            title = "Veri Analizi"
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        // UI elemanlarını bağla
        rvAnomalies = findViewById(R.id.rvAnomalies)
        rvFreeFallDetails = findViewById(R.id.rvFreeFallDetails)
        freeFallCard = findViewById(R.id.freeFallCard)
        tvNoFreeFalls = findViewById(R.id.tvNoFreeFalls)
        freeFallChartsContainer = findViewById(R.id.freeFallChartsContainer)

        // Intent'ten dosya adını al
        val fileName = intent.getStringExtra("FILE_NAME") ?: ""
        if (fileName.isEmpty()) {
            Toast.makeText(this, "Dosya bulunamadı", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Dosyadan verileri oku
        sensorDataList = readSensorDataFromFile(fileName)
        if (sensorDataList.isEmpty()) {
            Toast.makeText(this, "Veri okunamadı", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Anomalileri tespit et
        val anomalies = AnomalyDetector.detectAnomalies(sensorDataList)

        // RecyclerView'ları ayarla
        setupAnomaliesRecyclerView(anomalies)
        setupFreeFallDetailsRecyclerView(anomalies)

        // Serbest düşüş grafiklerini oluştur
        createFreeFallCharts(anomalies)

        // İstatistik verilerini doldur
        populateStatistics()
    }

    private fun setupAnomaliesRecyclerView(anomalies: List<AccelerationAnomaly>) {
        anomaliesAdapter = AnomaliesAdapter(anomalies)
        rvAnomalies.adapter = anomaliesAdapter
        rvAnomalies.layoutManager = LinearLayoutManager(this)

        // Anomali yoksa bunu göster
        if (anomalies.isEmpty()) {
            findViewById<TextView>(R.id.tvNoAnomalies).visibility = View.VISIBLE
        }
    }

    private fun setupFreeFallDetailsRecyclerView(anomalies: List<AccelerationAnomaly>) {
        // Serbest düşüş anomalilerini filtrele
        val freeFallAnomalies = anomalies.filter {
            it.type == AccelerationAnomaly.AnomalyType.FREE_FALL
        }

        if (freeFallAnomalies.isEmpty()) {
            // Serbest düşüş yoksa bilgi mesajı göster
            freeFallCard.visibility = View.VISIBLE
            tvNoFreeFalls.visibility = View.VISIBLE
            rvFreeFallDetails.visibility = View.GONE
            return
        }

        // Her bir serbest düşüş için ayrıntılı analiz yap
        val freeFallReports = freeFallAnomalies.map { anomaly ->
            FreeFallAnalysis.analyzeFreeFall(anomaly, sensorDataList)
        }

        // Adapter'ı ayarla
        freeFallDetailsAdapter = FreeFallDetailsAdapter(freeFallReports)
        rvFreeFallDetails.adapter = freeFallDetailsAdapter
        rvFreeFallDetails.layoutManager = LinearLayoutManager(this)

        // Görünürlüğü ayarla
        freeFallCard.visibility = View.VISIBLE
        tvNoFreeFalls.visibility = View.GONE
        rvFreeFallDetails.visibility = View.VISIBLE
    }

    /**
     * Serbest düşüş grafiklerini oluşturur
     */
    private fun createFreeFallCharts(anomalies: List<AccelerationAnomaly>) {
        // Serbest düşüş anomalilerini filtrele
        val freeFallAnomalies = anomalies.filter {
            it.type == AccelerationAnomaly.AnomalyType.FREE_FALL
        }

        if (freeFallAnomalies.isEmpty()) {
            // Serbest düşüş yoksa grafikler bölümünü gizle
            findViewById<CardView>(R.id.freeFallChartsCard).visibility = View.GONE
            return
        }

        // Her bir serbest düşüş için ayrıntılı analiz yap
        val freeFallReports = freeFallAnomalies.map { anomaly ->
            FreeFallAnalysis.analyzeFreeFall(anomaly, sensorDataList)
        }

        // Her rapor için bir grafik oluştur
        freeFallReports.forEach { report ->
            // Grafik yöneticisi oluştur ve grafiği ekle
            val chartManager = FreeFallChartManager(this, report, sensorDataList)
            val chartView = chartManager.createChartView()

            // Grafiği konteyner'a ekle
            freeFallChartsContainer.addView(chartView)
        }

        // Görünürlüğü ayarla
        findViewById<CardView>(R.id.freeFallChartsCard).visibility = View.VISIBLE
    }

    private fun populateStatistics() {
        try {
            if (sensorDataList.isEmpty()) {
                Toast.makeText(this, "Analiz için veri bulunamadı", Toast.LENGTH_SHORT).show()
                return
            }

            // Toplam veri noktası sayısı
            findViewById<TextView>(R.id.tvTotalDataPoints).text = sensorDataList.size.toString()

            // Kayıt süresi hesaplama
            val startTime = sensorDataList.minOfOrNull { it.timestamp } ?: 0
            val endTime = sensorDataList.maxOfOrNull { it.timestamp } ?: 0
            val duration = endTime - startTime
            findViewById<TextView>(R.id.tvRecordDuration).text = formatDuration(duration)

            // X Ekseni istatistikleri
            findViewById<TextView>(R.id.tvMeanX).text =
                String.format("%.2f m/s²", AccelerometerAnalytics.calculateMean(sensorDataList, "x"))
            findViewById<TextView>(R.id.tvStdDevX).text =
                String.format("%.2f m/s²", AccelerometerAnalytics.calculateStandardDeviation(sensorDataList, "x"))

            val minMaxX = AccelerometerAnalytics.findMinMaxValues(sensorDataList)["x"]
            if (minMaxX != null) {
                findViewById<TextView>(R.id.tvMinMaxX).text =
                    String.format("%.2f / %.2f m/s²", minMaxX.first, minMaxX.second)
            }

            // Y Ekseni istatistikleri
            findViewById<TextView>(R.id.tvMeanY).text =
                String.format("%.2f m/s²", AccelerometerAnalytics.calculateMean(sensorDataList, "y"))
            findViewById<TextView>(R.id.tvStdDevY).text =
                String.format("%.2f m/s²", AccelerometerAnalytics.calculateStandardDeviation(sensorDataList, "y"))

            val minMaxY = AccelerometerAnalytics.findMinMaxValues(sensorDataList)["y"]
            if (minMaxY != null) {
                findViewById<TextView>(R.id.tvMinMaxY).text =
                    String.format("%.2f / %.2f m/s²", minMaxY.first, minMaxY.second)
            }

            // Z Ekseni istatistikleri
            findViewById<TextView>(R.id.tvMeanZ).text =
                String.format("%.2f m/s²", AccelerometerAnalytics.calculateMean(sensorDataList, "z"))
            findViewById<TextView>(R.id.tvStdDevZ).text =
                String.format("%.2f m/s²", AccelerometerAnalytics.calculateStandardDeviation(sensorDataList, "z"))

            val minMaxZ = AccelerometerAnalytics.findMinMaxValues(sensorDataList)["z"]
            if (minMaxZ != null) {
                findViewById<TextView>(R.id.tvMinMaxZ).text =
                    String.format("%.2f / %.2f m/s²", minMaxZ.first, minMaxZ.second)
            }

            // Maksimum toplam ivme
            val maxAcceleration = sensorDataList.maxOfOrNull {
                AccelerometerAnalytics.calculateMagnitude(it.x, it.y, it.z)
            } ?: 0f
            findViewById<TextView>(R.id.tvMaxAcceleration).text = String.format("%.2f m/s²", maxAcceleration)

        } catch (e: Exception) {
            Log.e("DataAnalyticsActivity", "İstatistik hesaplanırken hata oluştu: ${e.message}")
            Toast.makeText(this, "İstatistik hesaplanırken hata oluştu", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatDuration(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = (millis / (1000 * 60 * 60))

        return when {
            hours > 0 -> String.format("%d saat %d dakika %d saniye", hours, minutes, seconds)
            minutes > 0 -> String.format("%d dakika %d saniye", minutes, seconds)
            else -> String.format("%d saniye", seconds)
        }
    }

    private fun readSensorDataFromFile(fileName: String): List<SensorData> {
        val sensorDataList = mutableListOf<SensorData>()
        val file = File(getExternalFilesDir(null), fileName)

        try {
            if (!file.exists() || !file.canRead()) {
                Log.e("DataAnalyticsActivity", "Dosya bulunamadı veya okunamıyor: $fileName")
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
                                Log.e("DataAnalyticsActivity", "Veri ayrıştırma hatası: $line", e)
                            }
                        }
                    }
                }
            }
        } catch (e: IOException) {
            Log.e("DataAnalyticsActivity", "Dosya okuma hatası: $fileName", e)
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