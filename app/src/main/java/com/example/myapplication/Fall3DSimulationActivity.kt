package com.example.myapplication

import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.IOException
import java.util.Timer
import java.util.TimerTask

class Fall3DSimulationActivity : AppCompatActivity() {

    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var phoneRenderer: PhoneRenderer
    private lateinit var sensorDataList: List<SensorData>
    private lateinit var btnPlayPause: Button
    private lateinit var btnReset: Button
    private lateinit var seekBarSimulation: SeekBar
    private lateinit var tvSimulationProgress: TextView
    private lateinit var tvSimulationInfo: TextView
    private lateinit var glContainer: FrameLayout

    private var isPlaying = false
    private var currentFrameIndex = 0
    private var timer: Timer? = null
    private val handler = Handler(Looper.getMainLooper())

    // Ek filtreleme parametreleri
    private var lastAccelX = 0f
    private var lastAccelY = 0f
    private var lastAccelZ = 0f
    private var lastRotX = 0f
    private var lastRotY = 0f
    private var lastRotZ = 0f

    // Filtreleme için katsayılar
    private val FILTER_ALPHA = 0.2f // 0.2 değeri jitteri azaltacak (0-1 arası)

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
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnReset = findViewById(R.id.btnReset)
        seekBarSimulation = findViewById(R.id.seekBarSimulation)
        tvSimulationProgress = findViewById(R.id.tvSimulationProgress)
        tvSimulationInfo = findViewById(R.id.tvSimulationInfo)
        glContainer = findViewById(R.id.sceneContainer) // Layout'taki FrameLayout

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

        // Simülasyon bilgilerini göster
        tvSimulationInfo.text = "Sensör Verisi Simülasyonu: ${sensorDataList.size} veri noktası"

        // OpenGL ES view oluştur ve ayarla
        setupGLView()

        // UI kontrollerini ayarla
        setupUIControls()
    }

    private fun setupGLView() {
        try {
            // OpenGL ES Surface View oluştur
            glSurfaceView = GLSurfaceView(this)
            glSurfaceView.setEGLContextClientVersion(2) // OpenGL ES 2.0 kullan

            // Renderer oluştur ve bağla
            phoneRenderer = PhoneRenderer()
            glSurfaceView.setRenderer(phoneRenderer)

            // Rendermode'u RENDERMODE_WHEN_DIRTY olarak ayarla
            // Bu, yalnızca requestRender() çağrıldığında çizim yapılacağı anlamına gelir
            glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

            // GLSurfaceView'i container'a ekle
            glContainer.removeAllViews() // Önceki view'ları temizle
            glContainer.addView(glSurfaceView)

            Log.d("Fall3DSimulationActivity", "OpenGL ES view başarıyla kuruldu")
        } catch (e: Exception) {
            Log.e("Fall3DSimulationActivity", "OpenGL ES view oluşturma hatası: ${e.message}")
            Toast.makeText(this, "3D görüntüleme başlatılamadı: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupUIControls() {
        // Play/Pause butonu
        btnPlayPause.setOnClickListener {
            if (isPlaying) {
                pauseSimulation()
                btnPlayPause.text = "Oynat"
            } else {
                startSimulation()
                btnPlayPause.text = "Durdur"
            }
        }

        // Reset butonu
        btnReset.setOnClickListener {
            resetSimulation()
        }

        // İlerleme çubuğu
        seekBarSimulation.max = sensorDataList.size - 1
        seekBarSimulation.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentFrameIndex = progress
                    updateSimulation(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                pauseSimulation()
                btnPlayPause.text = "Oynat"
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun startSimulation() {
        isPlaying = true

        // Timer içinde simülasyonu ilerlet (30 FPS için ~33ms)
        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                if (isPlaying && currentFrameIndex < sensorDataList.size - 1) {
                    currentFrameIndex++
                    handler.post {
                        updateSimulation(currentFrameIndex)
                    }
                } else if (currentFrameIndex >= sensorDataList.size - 1) {
                    handler.post {
                        pauseSimulation()
                        btnPlayPause.text = "Oynat"
                    }
                }
            }
        }, 0, 33)
    }

    private fun pauseSimulation() {
        isPlaying = false
        timer?.cancel()
        timer = null
    }

    private fun resetSimulation() {
        pauseSimulation()
        currentFrameIndex = 0
        updateSimulation(currentFrameIndex)
        btnPlayPause.text = "Oynat"

        // Filtreleme değerlerini de sıfırla
        lastAccelX = 0f
        lastAccelY = 0f
        lastAccelZ = 0f
        lastRotX = 0f
        lastRotY = 0f
        lastRotZ = 0f
    }

    private fun updateSimulation(frameIndex: Int) {
        if (frameIndex < 0 || frameIndex >= sensorDataList.size) return

        val progress = (frameIndex * 100) / (sensorDataList.size - 1)
        tvSimulationProgress.text = "İlerleme: %$progress"
        seekBarSimulation.progress = frameIndex

        // Sensör verilerini renderer'a ilet
        val data = sensorDataList[frameIndex]

        // Filtreleme ekleyerek sensör verilerini yumuşat
        val filteredAccelX = lowPassFilter(data.x, lastAccelX, FILTER_ALPHA)
        val filteredAccelY = lowPassFilter(data.y, lastAccelY, FILTER_ALPHA)
        val filteredAccelZ = lowPassFilter(data.z, lastAccelZ, FILTER_ALPHA)

        val filteredRotX = lowPassFilter(data.rotX, lastRotX, FILTER_ALPHA)
        val filteredRotY = lowPassFilter(data.rotY, lastRotY, FILTER_ALPHA)
        val filteredRotZ = lowPassFilter(data.rotZ, lastRotZ, FILTER_ALPHA)

        // Son filtrelenmiş değerleri sakla
        lastAccelX = filteredAccelX
        lastAccelY = filteredAccelY
        lastAccelZ = filteredAccelZ
        lastRotX = filteredRotX
        lastRotY = filteredRotY
        lastRotZ = filteredRotZ

        // İvme ve rotasyon değerlerini phone renderer'a aktar
        // Güncellenmiş PhoneRenderer sensör değerlerini daha iyi filtreleyecek
        phoneRenderer.updatePhonePosition(
            filteredAccelX, filteredAccelY, filteredAccelZ,
            filteredRotX, filteredRotY, filteredRotZ,
            data.gravX, data.gravY, data.gravZ,
            data.linAccX, data.linAccY, data.linAccZ
        )

        // Render işlemini tetikle
        glSurfaceView.requestRender()
    }

    /**
     * Basit bir düşük geçiş filtresi - titreşim azaltma
     */
    private fun lowPassFilter(input: Float, lastOutput: Float, alpha: Float): Float {
        return lastOutput + alpha * (input - lastOutput)
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
                // Başlık satırını al ve incele
                var headerLine: String? = null
                var isFirstLine = true
                var gravityIndices: Triple<Int, Int, Int>? = null
                var linAccIndices: Triple<Int, Int, Int>? = null

                lines.forEach { line ->
                    if (isFirstLine) {
                        headerLine = line
                        isFirstLine = false

                        // Başlık satırından indeksleri belirle
                        val headers = line.split(",")

                        // Yerçekimi indeksleri
                        val gravXIndex = headers.indexOf("grav_x")
                        val gravYIndex = headers.indexOf("grav_y")
                        val gravZIndex = headers.indexOf("grav_z")

                        if (gravXIndex >= 0 && gravYIndex >= 0 && gravZIndex >= 0) {
                            gravityIndices = Triple(gravXIndex, gravYIndex, gravZIndex)
                        }

                        // Lineer ivme indeksleri
                        val linAccXIndex = headers.indexOf("linacc_x")
                        val linAccYIndex = headers.indexOf("linacc_y")
                        val linAccZIndex = headers.indexOf("linacc_z")

                        if (linAccXIndex >= 0 && linAccYIndex >= 0 && linAccZIndex >= 0) {
                            linAccIndices = Triple(linAccXIndex, linAccYIndex, linAccZIndex)
                        }

                        return@forEach
                    }

                    if (line.isNotBlank()) {
                        val parts = line.split(",")
                        try {
                            // Temel değerleri oku - en az 4 sütun olmalı
                            if (parts.size >= 4) {
                                val timestamp = parts[0].toLong()
                                val x = parts[1].toFloat()
                                val y = parts[2].toFloat()
                                val z = parts[3].toFloat()

                                // Rotasyon değerlerini oku (varsa)
                                val rotX = if (parts.size > 4) parts[4].toFloatOrNull() ?: 0f else 0f
                                val rotY = if (parts.size > 5) parts[5].toFloatOrNull() ?: 0f else 0f
                                val rotZ = if (parts.size > 6) parts[6].toFloatOrNull() ?: 0f else 0f

                                // Gyro değerlerini oku (varsa)
                                val gyroX = if (parts.size > 7) parts[7].toFloatOrNull() ?: 0f else 0f
                                val gyroY = if (parts.size > 8) parts[8].toFloatOrNull() ?: 0f else 0f
                                val gyroZ = if (parts.size > 9) parts[9].toFloatOrNull() ?: 0f else 0f

                                // Lineer ivme değerlerini oku (varsa)
                                var linAccX = 0f
                                var linAccY = 0f
                                var linAccZ = 0f
                                linAccIndices?.let { indices ->
                                    if (parts.size > indices.third) {
                                        linAccX = parts[indices.first].toFloatOrNull() ?: 0f
                                        linAccY = parts[indices.second].toFloatOrNull() ?: 0f
                                        linAccZ = parts[indices.third].toFloatOrNull() ?: 0f
                                    }
                                }

                                // Yerçekimi değerlerini oku (varsa)
                                var gravX = 0f
                                var gravY = 0f
                                var gravZ = 0f
                                gravityIndices?.let { indices ->
                                    if (parts.size > indices.third) {
                                        gravX = parts[indices.first].toFloatOrNull() ?: 0f
                                        gravY = parts[indices.second].toFloatOrNull() ?: 0f
                                        gravZ = parts[indices.third].toFloatOrNull() ?: 0f
                                    }
                                }

                                // SensorData nesnesini oluştur
                                sensorDataList.add(SensorData(
                                    timestamp, x, y, z,
                                    rotX, rotY, rotZ,
                                    gyroX, gyroY, gyroZ,
                                    linAccX, linAccY, linAccZ,
                                    gravX, gravY, gravZ
                                ))
                            }
                        } catch (e: NumberFormatException) {
                            Log.e("Fall3DSimulationActivity", "Veri ayrıştırma hatası: $line", e)
                        }
                    }
                }
            }

            Log.d("Fall3DSimulationActivity", "Okunan sensör veri sayısı: ${sensorDataList.size}")

        } catch (e: IOException) {
            Log.e("Fall3DSimulationActivity", "Dosya okuma hatası: $fileName", e)
            Toast.makeText(this, "Dosya okuma hatası: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        return sensorDataList
    }

    // Activity yaşam döngüsü ile OpenGL ES view'ı senkronize et
    override fun onPause() {
        super.onPause()
        pauseSimulation()
        glSurfaceView.onPause()
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()
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