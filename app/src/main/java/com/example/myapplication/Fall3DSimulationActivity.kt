package com.example.myapplication

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Fall3DSimulationActivity : AppCompatActivity() {

    private lateinit var simulationContainer: FrameLayout
    private lateinit var btnPlayPause: Button
    private lateinit var btnReset: Button
    private lateinit var seekBarSimulation: SeekBar
    private lateinit var tvSimulationProgress: TextView
    private lateinit var tvSimulationInfo: TextView

    private lateinit var sensorDataList: List<SensorData>
    private lateinit var simulation3D: SimpleFallSimulation3D  // Basit 3D simülasyon sınıfı kullanılıyor

    private var isSimulationPlaying = false
    private var isSimulationPrepared = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fall_3d_simulation)

        // Toolbar'ı ayarla
        supportActionBar?.apply {
            title = "3D Düşüş Simülasyonu"
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        // UI elemanlarını bağla
        simulationContainer = findViewById(R.id.simulationContainer)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnReset = findViewById(R.id.btnReset)
        seekBarSimulation = findViewById(R.id.seekBarSimulation)
        tvSimulationProgress = findViewById(R.id.tvSimulationProgress)
        tvSimulationInfo = findViewById(R.id.tvSimulationInfo)

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

        // Serbest düşüş anomalilerini filtrele
        val freeFallAnomalies = anomalies.filter {
            it.type == AccelerationAnomaly.AnomalyType.FREE_FALL
        }

        if (freeFallAnomalies.isEmpty()) {
            tvSimulationInfo.text = "Bu kayıtta serbest düşüş tespit edilemedi."
            return
        }

        // İlk serbest düşüş için detaylı analiz yap
        val freeFallReport = FreeFallAnalysis.analyzeFreeFall(freeFallAnomalies.first(), sensorDataList)

        // Simülasyon bilgilerini göster
        val fallStartTime = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            .format(Date(freeFallReport.startTime))
        tvSimulationInfo.text = "Serbest Düşüş Simülasyonu: Süre: ${freeFallReport.duration}ms, " +
                "Tahmini Yükseklik: ${"%.2f".format(freeFallReport.estimatedHeight)}m, " +
                "Başlangıç: $fallStartTime"

        // 3D simülasyonu başlat
        initializeSimulation(freeFallReport)

        // UI kontrollerini ayarla
        setupUIControls()
    }

    private fun initializeSimulation(freeFallReport: FreeFallAnalysis.FreeFallReport) {
        // SimpleFallSimulation3D sınıfını kullan
        simulation3D = SimpleFallSimulation3D(this, sensorDataList, freeFallReport)

        // İlerleme güncellemeleri için listener
        simulation3D.setOnProgressUpdateListener { progress ->
            runOnUiThread {
                seekBarSimulation.progress = progress
                tvSimulationProgress.text = "Düşüş İlerleme: $progress%"
            }
        }

        // Simülasyon hazırlık bildirimi
        simulation3D.setOnSimulationPreparedListener {
            runOnUiThread {
                isSimulationPrepared = true
                btnPlayPause.isEnabled = true
                btnReset.isEnabled = true
                seekBarSimulation.isEnabled = true
                Toast.makeText(this, "Simülasyon hazır", Toast.LENGTH_SHORT).show()
            }
        }

        // Simülasyonu başlat
        simulation3D.startSimulation(simulationContainer)
    }

    private fun setupUIControls() {
        // Başlangıçta kontrolleri devre dışı bırak
        btnPlayPause.isEnabled = false
        btnReset.isEnabled = false
        seekBarSimulation.isEnabled = false

        // Oynat/Durdur butonu
        btnPlayPause.setOnClickListener {
            if (!isSimulationPrepared) return@setOnClickListener

            if (isSimulationPlaying) {
                // Durdur
                simulation3D.pause()
                btnPlayPause.text = "Oynat"
                isSimulationPlaying = false
            } else {
                // Oynat
                simulation3D.play()
                btnPlayPause.text = "Durdur"
                isSimulationPlaying = true
            }
        }

        // Sıfırla butonu
        btnReset.setOnClickListener {
            if (!isSimulationPrepared) return@setOnClickListener

            simulation3D.reset()
            isSimulationPlaying = false
            btnPlayPause.text = "Oynat"
            seekBarSimulation.progress = 0
            tvSimulationProgress.text = "Düşüş İlerleme: 0%"
        }

        // İlerleme çubuğu
        seekBarSimulation.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && isSimulationPrepared) {
                    simulation3D.seekTo(progress)
                    tvSimulationProgress.text = "Düşüş İlerleme: $progress%"
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                if (isSimulationPlaying) {
                    simulation3D.pause()
                    isSimulationPlaying = false
                    btnPlayPause.text = "Oynat"
                }
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Kullanıcı kaydırma çubuğunu bıraktığında bir şey yapma
            }
        })
    }

    private fun readSensorDataFromFile(fileName: String): List<SensorData> {
        val sensorDataList = mutableListOf<SensorData>()
        val file = File(getExternalFilesDir(null), fileName)

        try {
            if (!file.exists() || !file.canRead()) {
                Log.e("Fall3DSimulationActivity", "Dosya bulunamadı veya okunamıyor: $fileName")
                Toast.makeText(this, "Dosya bulunamadı: $fileName", Toast.LENGTH_SHORT).show()
                return emptyList()
            }

            file.bufferedReader().useLines { lines ->
                // Başlık satırını atla
                var isFirstLine = true

                lines.forEach { line ->
                    if (isFirstLine) {
                        isFirstLine = false
                        return@forEach
                    }

                    if (line.isNotBlank()) {
                        val parts = line.split(",")
                        try {
                            // Tüm değerleri oku
                            if (parts.size >= 4) {
                                val timestamp = parts[0].toLong()
                                val x = parts[1].toFloat()
                                val y = parts[2].toFloat()
                                val z = parts[3].toFloat()

                                // Rotasyon ve gyroscope verilerini al (varsa)
                                val rotX = if (parts.size > 4) parts[4].toFloatOrNull() ?: 0f else 0f
                                val rotY = if (parts.size > 5) parts[5].toFloatOrNull() ?: 0f else 0f
                                val rotZ = if (parts.size > 6) parts[6].toFloatOrNull() ?: 0f else 0f
                                val gyroX = if (parts.size > 7) parts[7].toFloatOrNull() ?: 0f else 0f
                                val gyroY = if (parts.size > 8) parts[8].toFloatOrNull() ?: 0f else 0f
                                val gyroZ = if (parts.size > 9) parts[9].toFloatOrNull() ?: 0f else 0f

                                sensorDataList.add(SensorData(
                                    timestamp, x, y, z,
                                    rotX, rotY, rotZ,
                                    gyroX, gyroY, gyroZ
                                ))
                            }
                        } catch (e: NumberFormatException) {
                            Log.e("Fall3DSimulationActivity", "Veri ayrıştırma hatası: $line", e)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            Log.e("Fall3DSimulationActivity", "Dosya okuma hatası: $fileName", e)
            Toast.makeText(this, "Dosya okuma hatası: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        return sensorDataList
    }

    // Simülasyonu durdur ve temizle
    override fun onDestroy() {
        super.onDestroy()
        if (::simulation3D.isInitialized) {
            simulation3D.cleanup()
        }
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