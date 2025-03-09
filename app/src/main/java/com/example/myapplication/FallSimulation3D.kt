package com.example.myapplication

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import io.github.sceneview.SceneView
import io.github.sceneview.loaders.loadModelGlbAsync
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Serbest düşüş simülasyonunu 3D olarak gösteren sınıf
 */
class FallSimulation3D(
    private val context: Context,
    private val sensorDataList: List<SensorData>,
    private val freeFallReport: FreeFallAnalysis.FreeFallReport
) {
    // SceneView ve model düğümü
    private lateinit var sceneView: SceneView
    private lateinit var phoneModelNode: ModelNode

    // Simülasyon durumu
    private var isPlaying = false
    private var currentFrameIndex = 0
    private var simKeyframes = mutableListOf<SimulationKeyframe>()

    // Callback'ler
    private var onProgressUpdateListener: ((Int) -> Unit)? = null
    private var onSimulationPreparedListener: (() -> Unit)? = null

    // Sınıf veri modeli
    data class SimulationKeyframe(
        val position: Position,
        val rotation: Rotation,
        val timestamp: Long
    )

    /**
     * 3D simülasyonu başlat
     */
    fun startSimulation(container: ViewGroup) {
        // SceneView oluştur ve container'a ekle
        sceneView = SceneView(context)
        container.addView(sceneView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // Işıklandırma ayarla
        sceneView.lightEstimationMode = SceneView.LightEstimationMode.ENVIRONMENTAL_HDR

        // Kamera pozisyonu ayarla (uzaktan görmek için)
        sceneView.camera.position = Position(z = 1.0f, y = 0.5f)
        sceneView.camera.lookAt(Position(x = 0.0f, y = 0.0f, z = 0.0f))

        // Telefon modelini yükle
        loadPhoneModel()

        // Düşüş keyframe'lerini hesapla
        prepareSimulationKeyframes()
    }

    /**
     * Simülasyon için anahtar kareleri hazırla
     */
    private fun prepareSimulationKeyframes() {
        try {
            // Düşüş başlangıç ve bitiş indekslerini bul
            val startIndex = sensorDataList.indexOfFirst { it.timestamp == freeFallReport.startTime }
            val endIndex = sensorDataList.indexOfFirst { it.timestamp == freeFallReport.endTime }

            if (startIndex == -1 || endIndex == -1 || startIndex >= endIndex) {
                Log.e("FallSimulation3D", "Geçerli düşüş aralığı bulunamadı")
                return
            }

            // Minimum ve maksimum değerleri belirle (normalizasyon için)
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var minZ = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE
            var maxZ = Float.MIN_VALUE

            // Simülasyon verileri
            val fallData = sensorDataList.subList(startIndex, endIndex + 1)

            // İvme verilerinden pozisyon hesapla
            val positions = calculatePositionFromAcceleration(fallData)

            // Min/Max değerleri belirle
            for (pos in positions) {
                if (pos.x < minX) minX = pos.x
                if (pos.y < minY) minY = pos.y
                if (pos.z < minZ) minZ = pos.z
                if (pos.x > maxX) maxX = pos.x
                if (pos.y > maxY) maxY = pos.y
                if (pos.z > maxZ) maxZ = pos.z
            }

            // Değerleri normalize et ve keyframe'leri oluştur
            simKeyframes.clear()
            for (i in fallData.indices) {
                // Pozisyonu normalize et (-1 ile 1 aralığına)
                val normX = normalizeValue(positions[i].x, minX, maxX, -0.5f, 0.5f)
                // Düşüş yönü yukarıdan aşağıya - bu yüzden Y değerini düşüş boyunca değiştir
                val normY = 0.5f - (i.toFloat() / fallData.size.toFloat())
                val normZ = normalizeValue(positions[i].z, minZ, maxZ, -0.5f, 0.5f)

                // Rotasyonu hesapla
                val data = fallData[i]
                val rotX = data.rotX
                val rotY = data.rotY
                val rotZ = data.rotZ

                // Keyframe oluştur
                simKeyframes.add(
                    SimulationKeyframe(
                        position = Position(normX, normY, normZ),
                        rotation = Rotation(rotX, rotY, rotZ),
                        timestamp = data.timestamp
                    )
                )
            }

            // Hazırlık tamamlandı bildir
            onSimulationPreparedListener?.invoke()

        } catch (e: Exception) {
            Log.e("FallSimulation3D", "Simülasyon hazırlama hatası: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Normalize işlemi (değer aralığını yeni aralığa dönüştür)
     */
    private fun normalizeValue(value: Float, min: Float, max: Float, newMin: Float, newMax: Float): Float {
        if (min == max) return newMin
        val ratio = (value - min) / (max - min)
        return newMin + ratio * (newMax - newMin)
    }

    /**
     * İvme verilerinden pozisyon hesapla
     */
    private fun calculatePositionFromAcceleration(fallData: List<SensorData>): List<Position> {
        val positions = mutableListOf<Position>()
        var velocityX = 0f
        var velocityY = 0f
        var velocityZ = 0f
        var posX = 0f
        var posY = 0f
        var posZ = 0f
        var lastTime = fallData.firstOrNull()?.timestamp ?: 0L

        for (data in fallData) {
            // Zaman farkını hesapla (saniye cinsinden)
            val dt = (data.timestamp - lastTime) / 1000f
            lastTime = data.timestamp

            // Yerçekimi etkisini kaldır (9.81 m/s²)
            var adjustedX = data.x
            var adjustedY = data.y
            var adjustedZ = data.z - 9.81f // Normalde Z yukarı yönde

            // Küçük değerleri filtrele (gürültüyü azalt)
            if (abs(adjustedX) < 0.1f) adjustedX = 0f
            if (abs(adjustedY) < 0.1f) adjustedY = 0f
            if (abs(adjustedZ) < 0.1f) adjustedZ = 0f

            // Hızları hesapla (ivme × zaman)
            velocityX += adjustedX * dt
            velocityY += adjustedY * dt
            velocityZ += adjustedZ * dt

            // Pozisyonları hesapla (hız × zaman)
            posX += velocityX * dt
            posY += velocityY * dt
            posZ += velocityZ * dt

            positions.add(Position(posX, posY, posZ))
        }

        return positions
    }

    /**
     * Telefon modelini yükle
     */
    private fun loadPhoneModel() {
        val modelNode = ModelNode()
        sceneView.addChildNode(modelNode)

        // Modeli yükle veya varsayılan küp oluştur
        try {
            // Asset klasöründen glb modeli yükle
            sceneView.lifecycleScope.loadModelGlbAsync(
                context = context,
                glbFileLocation = "models/phone.glb",
                autoAnimate = false
            ) { modelInstance ->
                modelNode.apply {
                    modelInstance = modelInstance
                    position = Position(x = 0.0f, y = 0.0f, z = 0.0f)
                    scale = Scale(x = 0.5f, y = 0.5f, z = 0.5f)
                }

                phoneModelNode = modelNode
                // Model yüklendiğinde ilk kareyi ayarla
                if (simKeyframes.isNotEmpty()) {
                    updateModelToFrame(0)
                }
            }
        } catch (e: Exception) {
            // Model yüklenemezse basit bir küp oluştur
            Log.e("FallSimulation3D", "Model yükleme hatası: ${e.message}")
            createCubePlaceholder(modelNode)
        }
    }

    /**
     * Yerine model yüklenemezse basit bir küp oluştur
     */
    private fun createCubePlaceholder(modelNode: Node) {
        // SceneView ile basit bir küp oluştur
        val cubeNode = Node()
        cubeNode.position = Position(x = 0.0f, y = 0.0f, z = 0.0f)
        cubeNode.scale = Scale(x = 0.1f, y = 0.2f, z = 0.05f) // Telefon benzeri uzunluk

        sceneView.addChildNode(cubeNode)
        phoneModelNode = cubeNode as ModelNode

        // Model yüklendiğinde ilk kareyi ayarla
        if (simKeyframes.isNotEmpty()) {
            updateModelToFrame(0)
        }
    }

    /**
     * Modeli belirli bir kareye güncelle
     */
    private fun updateModelToFrame(frameIndex: Int) {
        if (frameIndex < 0 || frameIndex >= simKeyframes.size || !::phoneModelNode.isInitialized) {
            return
        }

        val keyframe = simKeyframes[frameIndex]
        phoneModelNode.position = keyframe.position
        phoneModelNode.rotation = keyframe.rotation

        // İlerleme bildirimi yap
        val progress = (frameIndex * 100) / simKeyframes.size
        onProgressUpdateListener?.invoke(progress)
    }

    /**
     * Simülasyonu başlat
     */
    fun play() {
        if (simKeyframes.isEmpty() || !::phoneModelNode.isInitialized) {
            return
        }

        isPlaying = true
        animateFrames()
    }

    /**
     * Simülasyonu durdur
     */
    fun pause() {
        isPlaying = false
    }

    /**
     * Simülasyonu sıfırla
     */
    fun reset() {
        currentFrameIndex = 0
        updateModelToFrame(currentFrameIndex)
        isPlaying = false
    }

    /**
     * Simülasyonu belirli bir ilerleme yüzdesine ayarla
     */
    fun seekTo(progressPercent: Int) {
        if (simKeyframes.isEmpty()) return

        val targetFrame = (progressPercent * simKeyframes.size) / 100
        currentFrameIndex = targetFrame.coerceIn(0, simKeyframes.size - 1)
        updateModelToFrame(currentFrameIndex)
    }

    /**
     * Kareleri animasyonlu olarak göster
     */
    private fun animateFrames() {
        if (!isPlaying || currentFrameIndex >= simKeyframes.size - 1) {
            return
        }

        updateModelToFrame(currentFrameIndex)
        currentFrameIndex++

        // Sonraki kareyi göstermek için zamanlayıcı ayarla
        // Gerçek düşüş hızından biraz daha yavaş göster
        val delay = 50L // Milisaniye cinsinden
        sceneView.postDelayed({ animateFrames() }, delay)
    }

    /**
     * İlerleme güncellemelerini dinlemek için listener
     */
    fun setOnProgressUpdateListener(listener: (Int) -> Unit) {
        onProgressUpdateListener = listener
    }

    /**
     * Simülasyon hazır olduğunda bildirim almak için listener
     */
    fun setOnSimulationPreparedListener(listener: () -> Unit) {
        onSimulationPreparedListener = listener
    }

    /**
     * Temizlik işlemleri
     */
    fun cleanup() {
        if (::sceneView.isInitialized) {
            sceneView.destroy()
        }
    }
}