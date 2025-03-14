package com.example.myapplication

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import java.io.File
import java.io.IOException
import java.util.Timer
import java.util.TimerTask

class Fall3DSimulationActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var phoneRenderer: PhoneRenderer
    private lateinit var sensorDataList: List<SensorData>
    private lateinit var btnPlayPause: Button
    private lateinit var btnReset: Button
    private lateinit var cardCalibrate: CardView  // CardView olarak değiştirildi
    private lateinit var seekBarSimulation: SeekBar
    private lateinit var tvSimulationProgress: TextView
    private lateinit var tvSimulationInfo: TextView
    private lateinit var glContainer: FrameLayout

    private var isPlaying = false
    private var currentFrameIndex = 0
    private var timer: Timer? = null
    private val handler = Handler(Looper.getMainLooper())

    // Gerçek zamanlı sensör verileri için
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var magnetometer: Sensor? = null
    private var isRealtimeMode = false

    // Son sensör değerleri
    private var lastAccelValues = FloatArray(3) { 0f }
    private var lastGyroValues = FloatArray(3) { 0f }
    private var lastMagValues = FloatArray(3) { 0f }

    // Oryantasyon hesaplama matrisleri
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

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
        cardCalibrate = findViewById(R.id.btnCalibrate) // CardView olarak buluyoruz
        seekBarSimulation = findViewById(R.id.seekBarSimulation)
        tvSimulationProgress = findViewById(R.id.tvSimulationProgress)
        tvSimulationInfo = findViewById(R.id.tvSimulationInfo)
        glContainer = findViewById(R.id.sceneContainer) // Layout'taki FrameLayout

        // Sensor manager'ı başlat
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        // Intent'ten dosya adını al
        val fileName = intent.getStringExtra("FILE_NAME") ?: ""

        // Eğer bir dosya adı belirtilmişse, dosyadan verileri oku
        if (fileName.isNotEmpty()) {
            sensorDataList = readSensorDataFromFile(fileName)
            if (sensorDataList.isEmpty()) {
                Toast.makeText(this, "Dosya verisi okunamadı, gerçek zamanlı moda geçiliyor", Toast.LENGTH_SHORT).show()
                isRealtimeMode = true
            } else {
                // Simülasyon bilgilerini göster
                tvSimulationInfo.text = "Dosya Simülasyonu: ${sensorDataList.size} veri noktası"
                setupUIControls()
            }
        } else {
            // Dosya adı yoksa gerçek zamanlı moda geç
            isRealtimeMode = true
            tvSimulationInfo.text = "Gerçek Zamanlı Mod"
            // Gerçek zamanlı modda oynatma kontrollerini gösterme
            findViewById<View>(R.id.playbackControls).visibility = View.GONE
        }

        // OpenGL ES view oluştur ve ayarla
        setupGLView()

        // Kalibrasyon butonu
        cardCalibrate.setOnClickListener {
            phoneRenderer.calibrate()
            Toast.makeText(this, "Sensörler kalibre edildi", Toast.LENGTH_SHORT).show()
        }
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
        if (isRealtimeMode) return

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
    }

    private fun updateSimulation(frameIndex: Int) {
        if (isRealtimeMode) return

        if (frameIndex < 0 || frameIndex >= sensorDataList.size) return

        val progress = (frameIndex * 100) / (sensorDataList.size - 1)
        tvSimulationProgress.text = "İlerleme: %$progress"
        seekBarSimulation.progress = frameIndex

        // Sensör verilerini renderer'a ilet
        val data = sensorDataList[frameIndex]

        // İvme ve rotasyon değerlerini phone renderer'a aktar
        phoneRenderer.updatePhonePosition(
            data.x, data.y, data.z,
            data.rotX, data.rotY, data.rotZ,
            data.gravX, data.gravY, data.gravZ,
            data.linAccX, data.linAccY, data.linAccZ,
            0f, 0f, 0f  // Manyetik alan verisi yoksa 0 gönder
        )

        // Render işlemini tetikle
        glSurfaceView.requestRender()
    }

    /**
     * Gerçek zamanlı sensör verilerini işle
     */
    override fun onSensorChanged(event: SensorEvent) {
        if (!isRealtimeMode) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, lastAccelValues, 0, 3)

                // Yerçekimi ve lineer ivme hesapla (eğer bu sensör yoksa)
                val alpha = 0.8f
                val gravity = FloatArray(3)
                val linearAccel = FloatArray(3)

                // Yerçekimi bileşenini ayır
                gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0]
                gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1]
                gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2]

                // Lineer ivmeyi hesapla (toplam ivme - yerçekimi)
                linearAccel[0] = event.values[0] - gravity[0]
                linearAccel[1] = event.values[1] - gravity[1]
                linearAccel[2] = event.values[2] - gravity[2]

                // Sensör verileri ile telefon modelini güncelle
                updatePhoneModelWithSensors(
                    event.values[0], event.values[1], event.values[2],
                    lastGyroValues[0], lastGyroValues[1], lastGyroValues[2],
                    gravity[0], gravity[1], gravity[2],
                    linearAccel[0], linearAccel[1], linearAccel[2],
                    lastMagValues[0], lastMagValues[1], lastMagValues[2]
                )
            }
            Sensor.TYPE_GYROSCOPE -> {
                System.arraycopy(event.values, 0, lastGyroValues, 0, 3)

                // Sensör verileri ile telefon modelini güncelle
                updatePhoneModelWithSensors(
                    lastAccelValues[0], lastAccelValues[1], lastAccelValues[2],
                    event.values[0], event.values[1], event.values[2],
                    0f, 0f, 0f, // Yerçekimi verisi yok
                    0f, 0f, 0f, // Lineer ivme verisi yok
                    lastMagValues[0], lastMagValues[1], lastMagValues[2]
                )
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, lastMagValues, 0, 3)

                // Manyetik alan ve ivmeölçer verilerinden cihaz oryantasyonunu hesapla
                if (SensorManager.getRotationMatrix(rotationMatrix, null, lastAccelValues, event.values)) {
                    SensorManager.getOrientation(rotationMatrix, orientationAngles)

                    // Radyan değerlerini dereceye çevir
                    val azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat() // Z eksen dönüşü
                    val pitch = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()   // X eksen dönüşü
                    val roll = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()    // Y eksen dönüşü

                    // Log.d("Orientation", "Azimuth: $azimuth, Pitch: $pitch, Roll: $roll")

                    // Oryantasyon değerleri ile telefon modelini güncelle
                    updatePhoneModelWithSensors(
                        lastAccelValues[0], lastAccelValues[1], lastAccelValues[2],
                        pitch, roll, azimuth, // Oryantasyon açıları
                        0f, 0f, 0f, // Yerçekimi verisi yok
                        0f, 0f, 0f, // Lineer ivme verisi yok
                        event.values[0], event.values[1], event.values[2]
                    )
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Sensör doğruluk değişimlerini burada işleyebiliriz
    }

    /**
     * Gerçek zamanlı sensör verileri ile telefon modelini güncelle
     */
    private fun updatePhoneModelWithSensors(
        accelX: Float, accelY: Float, accelZ: Float,
        gyroX: Float, gyroY: Float, gyroZ: Float,
        gravX: Float, gravY: Float, gravZ: Float,
        linAccX: Float, linAccY: Float, linAccZ: Float,
        magX: Float, magY: Float, magZ: Float
    ) {
        // Telefon modelini güncelle
        phoneRenderer.updatePhonePosition(
            accelX, accelY, accelZ,
            gyroX, gyroY, gyroZ,
            gravX, gravY, gravZ,
            linAccX, linAccY, linAccZ,
            magX, magY, magZ
        )

        // Render işlemini tetikle
        glSurfaceView.requestRender()
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

    // Activity yaşam döngüsü ile OpenGL ES view'ı ve sensörleri senkronize et
    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()

        // Gerçek zamanlı mod aktifse sensörleri dinlemeye başla
        if (isRealtimeMode) {
            accelerometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
            gyroscope?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
            magnetometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        pauseSimulation()
        glSurfaceView.onPause()

        // Sensör dinlemeyi durdur
        if (isRealtimeMode) {
            sensorManager.unregisterListener(this)
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