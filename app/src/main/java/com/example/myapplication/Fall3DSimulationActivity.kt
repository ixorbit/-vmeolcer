package com.example.myapplication

import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import dev.romainguy.kotlin.math.Float3
import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.TransformManager
import com.google.android.filament.gltfio.AssetLoader
import android.os.Handler
import android.os.Looper
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Direction
import io.github.sceneview.node.Node
import io.github.sceneview.light.DirectionalLight
import io.github.sceneview.material.MaterialFactory
import io.github.sceneview.model.ModelFactory
import com.google.android.filament.Color

class Fall3DSimulationActivity : AppCompatActivity() {

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

        // SceneView'i bul (XML'de eklememiz gerekecek)
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

        // Scene'i hazırla ve telefon modelini ekle
        setupScene()

        // UI kontrollerini ayarla
        setupUIControls()
    }

    private fun setupScene() {
        // Kamera ayarları
        sceneView.camera.position = Position(0f, 0f, 4f)
        sceneView.camera.lookAt(Position(0f, 0f, 0f))

        // Işık ekle
        val mainLight = DirectionalLight(sceneView.engine).apply {
            color = Color(1.0f, 1.0f, 1.0f)
            intensity = 60_000f
            direction = Direction(0.0f, -1.0f, 0.0f)
        }
        sceneView.scene.addChild(mainLight)

        // Zemin düzlemi ekle
        val material = MaterialFactory.makeTransparentWithColor(
            sceneView.engine,
            Color(0.5f, 0.5f, 0.5f, 0.5f)
        )
        val plane = ModelFactory.makePlane(sceneView.engine, material, 10f, 10f)
        Node().apply {
            setModel(plane)
            position = Position(0f, -2f, 0f)
            rotation = Rotation(90f, 0f, 0f)
            sceneView.scene.addChild(this)
        }

        // Telefon modelini ekle (basit bir kutu)
        val phoneMaterial = MaterialFactory.makeOpaqueWithColor(
            sceneView.engine,
            Color(0.1f, 0.1f, 0.8f)
        )
        val phoneModel = ModelFactory.makeBox(
            sceneView.engine,
            phoneMaterial,
            0.7f, 1.4f, 0.1f
        )

        phoneNode = Node().apply {
            setModel(phoneModel)
            position = Position(0f, 0f, 0f)
            sceneView.scene.addChild(this)
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
        phoneNode?.let { phone ->
            // İvme değerlerini doğrudan pozisyon olarak kullan (daha görünür olması için ölçeklendir)
            val posX = data.x * 0.2f
            val posY = data.y * 0.2f
            val posZ = (data.z - 9.81f) * 0.2f  // Yerçekimi düzeltmesi

            // Rotasyon değerlerini kullan
            val rotX = data.rotX * 57.3f  // Radyan -> Derece
            val rotY = data.rotY * 57.3f
            val rotZ = data.rotZ * 57.3f

            // Pozisyon ve rotasyonu uygula (animasyonlu)
            phone.position = Position(posX, posY, posZ)
            phone.rotation = Rotation(rotX, rotY, rotZ)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pauseAnimation()
        sceneView.destroy()
    }
}