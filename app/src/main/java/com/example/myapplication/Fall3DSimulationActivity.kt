package com.example.myapplication

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.github.sceneview.SceneView
import io.github.sceneview.node.Node
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import java.io.File
import java.io.IOException

class Fall3DSimulationActivity(var modelScale: Float) : AppCompatActivity() {

    private lateinit var sceneView: SceneView
    private lateinit var sensorDataList: List<SensorData>
    private lateinit var btnPlayPause: Button
    private lateinit var btnReset: Button
    private lateinit var seekBarSimulation: SeekBar
    private lateinit var tvSimulationProgress: TextView
    private lateinit var tvSimulationInfo: TextView

    private var isPlaying = false
    private var currentFrameIndex = 0
    private var animationHandler = Handler(Looper.getMainLooper())
    private var phoneNode: Node? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fall_3d_simulation)

        // UI elemanlarını bağla
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnReset = findViewById(R.id.btnReset)
        seekBarSimulation = findViewById(R.id.seekBarSimulation)
        tvSimulationProgress = findViewById(R.id.tvSimulationProgress)
        tvSimulationInfo = findViewById(R.id.tvSimulationInfo)

        // SceneView'i bul
        sceneView = findViewById(R.id.sceneView)

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

        // SceneView'i hazırla
        setupScene()

        // UI kontrollerini ayarla
        setupUIControls()
    }

    private fun setupScene() {
        try {
            // Doğrudan BoxNode kullanımı yerine Node oluştur
            phoneNode = createPhoneModel()

            // 3D sahneyi oluşturmak için Android OpenGL kullanacağız

        } catch (e: Exception) {
            Log.e("Fall3DSimulationActivity", "Scene oluşturma hatası: ${e.message}")
            Toast.makeText(this, "3D görünüm oluşturulamadı: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createPhoneModel(): Node {
        // Android sürümünüze uygun şekilde Node oluştur
        return Node(engine = sceneView.engine).apply {
            // Başlangıç pozisyonu
            position = Position(0f, 0f, 0f)

            // SceneView API özelliklerine göre boyutlandırma
            // Doğrudan ölçek ayarlamak için:
            modelScale = 0.5f

            // Alternatif olarak telefon modelini yükleyebiliriz (eğer SceneView modelFactory destekliyorsa)
            // loadModelGlb(context = this@Fall3DSimulationActivity, glbFileLocation = "models/phone.glb")
        }
    }

    private fun setupUIControls() {
        // Butonlar
        btnPlayPause.setOnClickListener {
            if (isPlaying) {
                pauseAnimation()
                btnPlayPause.text = "Oynat"
            } else {
                startAnimation()
                btnPlayPause.text = "Durdur"
            }
        }

        btnReset.setOnClickListener {
            resetAnimation()
        }

        // İlerleme çubuğu
        seekBarSimulation.max = sensorDataList.size - 1
        seekBarSimulation.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentFrameIndex = progress
                    updatePhone(sensorDataList[progress])
                    tvSimulationProgress.text = "İlerleme: ${progress * 100 / (sensorDataList.size - 1)}%"
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                pauseAnimation()
                btnPlayPause.text = "Oynat"
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun startAnimation() {
        if (currentFrameIndex >= sensorDataList.size - 1) {
            currentFrameIndex = 0
        }

        isPlaying = true
        animateNextFrame()
    }

    private fun pauseAnimation() {
        isPlaying = false
        animationHandler.removeCallbacksAndMessages(null)
    }

    private fun resetAnimation() {
        pauseAnimation()
        currentFrameIndex = 0
        if (sensorDataList.isNotEmpty()) {
            updatePhone(sensorDataList[0])
        }
        seekBarSimulation.progress = 0
        tvSimulationProgress.text = "İlerleme: 0%"
        btnPlayPause.text = "Oynat"
    }

    private fun animateNextFrame() {
        if (!isPlaying || currentFrameIndex >= sensorDataList.size - 1) {
            isPlaying = false
            btnPlayPause.text = "Oynat"
            return
        }

        // Mevcut kareyi göster
        updatePhone(sensorDataList[currentFrameIndex])

        // İlerlemeyi güncelle
        val progress = currentFrameIndex * 100 / (sensorDataList.size - 1)
        tvSimulationProgress.text = "İlerleme: $progress%"
        seekBarSimulation.progress = currentFrameIndex

        // Sonraki kareye geç
        currentFrameIndex++

        // 100ms (10fps) sonra bir sonraki kareyi göster
        animationHandler.postDelayed({ animateNextFrame() }, 100)
    }

    private fun updatePhone(data: SensorData) {
        try {
            phoneNode?.let { phone ->
                // İvme değerlerini doğrudan pozisyon olarak kullan (daha görünür olması için ölçeklendir)
                val posX = data.x * 0.2f
                val posY = data.y * 0.2f
                val posZ = (data.z - 9.81f) * 0.2f  // Yerçekimi düzeltmesi

                // Rotasyon değerlerini kullan
                val rotX = data.rotX * 57.3f  // Radyan -> Derece
                val rotY = data.rotY * 57.3f
                val rotZ = data.rotZ * 57.3f

                // Pozisyon ve rotasyonu uygula
                phone.position = Position(posX, posY, posZ)
                phone.rotation = Rotation(rotX, rotY, rotZ)
            }
        } catch (e: Exception) {
            Log.e("Fall3DSimulationActivity", "Telefon güncelleme hatası: ${e.message}")
        }
    }

    // DataAnalyticsActivity'den kopyalanan dosya okuma fonksiyonu
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
                            // Tüm değerleri oku - en az 4 sütun olmalı
                            if (parts.size >= 4) {
                                val timestamp = parts[0].toLong()
                                val x = parts[1].toFloat()
                                val y = parts[2].toFloat()
                                val z = parts[3].toFloat()

                                // Ek verileri varsa ekle
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

            Log.d("Fall3DSimulationActivity", "Okunan sensör veri sayısı: ${sensorDataList.size}")

        } catch (e: IOException) {
            Log.e("Fall3DSimulationActivity", "Dosya okuma hatası: $fileName", e)
            Toast.makeText(this, "Dosya okuma hatası: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        return sensorDataList
    }

    override fun onDestroy() {
        super.onDestroy()
        pauseAnimation()
        sceneView.destroy()
    }
}