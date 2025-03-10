package com.example.myapplication

import android.content.Context
import android.opengl.GLSurfaceView
import android.opengl.GLU
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Serbest düşüş simülasyonunu basit bir 3D görselleştirme ile gösteren sınıf
 * OpenGL ES 1.0 tabanlı
 */
class SimpleFallSimulation3D(
    private val context: Context,
    private val sensorDataList: List<SensorData>,
    private val freeFallReport: FreeFallAnalysis.FreeFallReport
) {
    private lateinit var glSurfaceView: FallGLSurfaceView
    private var isPlaying = false
    private var currentFrameIndex = 0
    private var keyframes = mutableListOf<FallKeyframe>()

    private var onProgressUpdateListener: ((Int) -> Unit)? = null
    private var onSimulationPreparedListener: (() -> Unit)? = null

    // Simülasyon için anahtar kare sınıfı
    data class FallKeyframe(
        val x: Float, val y: Float, val z: Float,
        val rotX: Float, val rotY: Float, val rotZ: Float,
        val timestamp: Long
    )

    /**
     * Simülasyonu başlat
     */
    fun startSimulation(container: ViewGroup) {
        // OpenGL yüzey görünümünü oluştur
        glSurfaceView = FallGLSurfaceView(context)
        container.addView(glSurfaceView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // Düşüş verileri için anahtar kareleri hesapla
        prepareKeyframes()

        // İlk kareyi ayarla
        if (keyframes.isNotEmpty()) {
            glSurfaceView.renderer.setKeyframe(keyframes[0])
            Log.d("SimpleFallSimulation3D", "İlk kare ayarlandı: ${keyframes[0]}")
        } else {
            Log.e("SimpleFallSimulation3D", "Keyframe hesaplanamadı!")
        }

        // Hazırlık tamamlandı
        onSimulationPreparedListener?.invoke()
    }

    /**
     * Anahtar kareleri hazırla - TÜM hareketleri kaydetmek için değiştirildi
     */
    private fun prepareKeyframes() {
        try {
            Log.d("SimpleFallSimulation3D", "Sensör veri sayısı: ${sensorDataList.size}")

            if (sensorDataList.isEmpty()) {
                Log.e("SimpleFallSimulation3D", "Sensör verisi boş!")
                keyframes.add(FallKeyframe(0f, 0f, 0f, 0f, 0f, 0f, System.currentTimeMillis()))
                return
            }

            keyframes.clear()

            // ---- YENİ YAKLAŞIM: DOĞRUDAN SENSÖR VERİLERİNİ KULLAN ----
            // İvme verilerini doğrudan göster, entegrasyon yapmadan

            // Bazı verileri atla (performans ve görsellik için)
            val skipFactor = if (sensorDataList.size > 1000) 10 else if (sensorDataList.size > 500) 5 else 2

            // Her bir veri noktası için keyframe oluştur
            Log.d("SimpleFallSimulation3D", "Skip factor: $skipFactor, işlenecek veri: ${sensorDataList.size / skipFactor}")

            sensorDataList.forEachIndexed { index, data ->
                // Her skipFactor veriyi atla
                if (index % skipFactor != 0 && index > 0) return@forEachIndexed

                // İVME VERİLERİNİ DOĞRUDAN KONUM OLARAK KULLAN (çok daha belirgin görmek için)
                // Bu yaklaşım fiziksel olarak doğru değil ama görsel olarak daha iyi
                keyframes.add(FallKeyframe(
                    x = data.x * 0.3f,       // İvme X = Konum X (ölçek 0.3)
                    y = data.y * 0.3f,       // İvme Y = Konum Y (ölçek 0.3)
                    z = (data.z - 9.81f) * 0.3f,  // Yerçekimi çıkarıldı
                    rotX = data.rotX * 57.3f,     // Radyan -> Derece
                    rotY = data.rotY * 57.3f,
                    rotZ = data.rotZ * 57.3f,
                    timestamp = data.timestamp
                ))
            }

            Log.d("SimpleFallSimulation3D", "Toplam ${keyframes.size} keyframe oluşturuldu")

            if (keyframes.isEmpty()) {
                keyframes.add(FallKeyframe(0f, 0f, 0f, 0f, 0f, 0f, System.currentTimeMillis()))
            }

            // Daha iyi log, ilk ve son keyframe bilgilerini göster
            if (keyframes.size > 1) {
                val first = keyframes.first()
                val last = keyframes.last()
                Log.d("SimpleFallSimulation3D", "İlk keyframe: (${first.x}, ${first.y}, ${first.z})")
                Log.d("SimpleFallSimulation3D", "Son keyframe: (${last.x}, ${last.y}, ${last.z})")
            }

        } catch (e: Exception) {
            Log.e("SimpleFallSimulation3D", "Keyframe oluşturma hatası", e)
            keyframes.clear()
            keyframes.add(FallKeyframe(0f, 0f, 0f, 0f, 0f, 0f, System.currentTimeMillis()))
        }
    }

    /**
     * Simülasyonu oynat
     */
    fun play() {
        if (keyframes.isEmpty()) {
            Log.e("SimpleFallSimulation3D", "Keyframe yok, oynatma iptal edildi")
            return
        }

        // Eğer zaten bitmiş bir animasyonsa başa sar
        if (currentFrameIndex >= keyframes.size - 1) {
            currentFrameIndex = 0
        }

        isPlaying = true
        Log.d("SimpleFallSimulation3D", "Simulasyon başlatıldı, ${keyframes.size} kare, şu anki kare: $currentFrameIndex")

        // İlk kareyi hemen göster
        try {
            glSurfaceView.renderer.setKeyframe(keyframes[currentFrameIndex])
        } catch (e: Exception) {
            Log.e("SimpleFallSimulation3D", "İlk kare gösterilirken hata: ${e.message}")
        }

        // Animasyonu başlat
        animateFrames()
    }

    /**
     * Simülasyonu durdur
     */
    fun pause() {
        isPlaying = false
        Log.d("SimpleFallSimulation3D", "Simülasyon duraklatıldı.")
    }

    /**
     * Simülasyonu sıfırla
     */
    fun reset() {
        isPlaying = false // Önce durdur
        currentFrameIndex = 0

        if (keyframes.isNotEmpty()) {
            glSurfaceView.renderer.setKeyframe(keyframes[0])
            Log.d("SimpleFallSimulation3D", "Simülasyon sıfırlandı.")
        }

        onProgressUpdateListener?.invoke(0)
    }

    /**
     * Belirli bir ilerleme yüzdesine git
     */
    fun seekTo(progressPercent: Int) {
        if (keyframes.isEmpty()) return

        val targetFrame = (progressPercent * keyframes.size) / 100
        currentFrameIndex = targetFrame.coerceIn(0, keyframes.size - 1)
        glSurfaceView.renderer.setKeyframe(keyframes[currentFrameIndex])
        onProgressUpdateListener?.invoke(progressPercent)
        Log.d("SimpleFallSimulation3D", "İlerleme: %$progressPercent, Kare: $currentFrameIndex")
    }

    /**
     * Kareleri animasyonlu göster
     */
    private fun animateFrames() {
        if (!isPlaying) {
            Log.d("SimpleFallSimulation3D", "Animasyon durduruldu.")
            return
        }

        try {
            // Son kareye geldik mi kontrol et
            if (currentFrameIndex >= keyframes.size - 1) {
                Log.d("SimpleFallSimulation3D", "Animasyon tamamlandı.")
                isPlaying = false
                onProgressUpdateListener?.invoke(100)
                return
            }

            // Şu anki kareyi göster ve sonrakine geç
            val currentFrame = keyframes[currentFrameIndex]
            glSurfaceView.renderer.setKeyframe(currentFrame)

            val progress = (currentFrameIndex * 100) / keyframes.size
            onProgressUpdateListener?.invoke(progress)

            currentFrameIndex++

            // Bir sonraki kareyi göster
            glSurfaceView.postDelayed({ animateFrames() }, 32L) // 30fps - daha yavaş animasyon için
        } catch (e: Exception) {
            Log.e("SimpleFallSimulation3D", "Animasyon hatası: ${e.message}")
            e.printStackTrace()
            isPlaying = false
        }
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
        if (::glSurfaceView.isInitialized) {
            glSurfaceView.onPause()
        }
    }

    /**
     * OpenGL yüzey görünümü sınıfı
     */
    inner class FallGLSurfaceView(context: Context) : GLSurfaceView(context) {
        val renderer = FallRenderer()

        init {
            // OpenGL ES sürümü ayarla
            setEGLContextClientVersion(1)  // OpenGL ES 1.0 kullan
            setRenderer(renderer)
            renderMode = RENDERMODE_WHEN_DIRTY  // Yalnızca gerektiğinde çiz
        }
    }

    /**
     * OpenGL Renderer sınıfı
     */
    inner class FallRenderer : GLSurfaceView.Renderer {
        private var currentKeyframe = FallKeyframe(0f, 0f, 0f, 0f, 0f, 0f, 0)

        // Telefon modeli (daha gerçekçi şekil)
        private val vertices = floatArrayOf(
            // Ön
            -0.6f, -1.2f, 0.1f,
            0.6f, -1.2f, 0.1f,
            0.6f, 1.2f, 0.1f,
            -0.6f, 1.2f, 0.1f,

            // Arka
            -0.6f, -1.2f, -0.1f,
            0.6f, -1.2f, -0.1f,
            0.6f, 1.2f, -0.1f,
            -0.6f, 1.2f, -0.1f,

            // Ekran (biraz çıkıntılı)
            -0.55f, -1.15f, 0.101f,
            0.55f, -1.15f, 0.101f,
            0.55f, 1.15f, 0.101f,
            -0.55f, 1.15f, 0.101f,
        )

        // Yüz indeksleri
        private val indices = byteArrayOf(
            // Gövde
            0, 1, 2, 0, 2, 3,  // Ön
            4, 5, 6, 4, 6, 7,  // Arka
            0, 1, 5, 0, 5, 4,  // Alt
            3, 2, 6, 3, 6, 7,  // Üst
            0, 3, 7, 0, 7, 4,  // Sol
            1, 2, 6, 1, 6, 5,  // Sağ

            // Ekran
            8, 9, 10, 8, 10, 11
        )

        // Renkler (her köşe için)
        private val colors = floatArrayOf(
            // Telefon gövdesi (koyu gri)
            0.3f, 0.3f, 0.3f, 1.0f,
            0.3f, 0.3f, 0.3f, 1.0f,
            0.3f, 0.3f, 0.3f, 1.0f,
            0.3f, 0.3f, 0.3f, 1.0f,

            0.25f, 0.25f, 0.25f, 1.0f,
            0.25f, 0.25f, 0.25f, 1.0f,
            0.25f, 0.25f, 0.25f, 1.0f,
            0.25f, 0.25f, 0.25f, 1.0f,

            // Ekran (açık mavi)
            0.0f, 0.5f, 0.9f, 1.0f,
            0.0f, 0.5f, 0.9f, 1.0f,
            0.0f, 0.5f, 0.9f, 1.0f,
            0.0f, 0.5f, 0.9f, 1.0f
        )

        private lateinit var vertexBuffer: FloatBuffer
        private lateinit var colorBuffer: FloatBuffer
        private lateinit var indexBuffer: ByteBuffer

        /**
         * Anahtar kareyi ayarla
         */
        fun setKeyframe(keyframe: FallKeyframe) {
            currentKeyframe = keyframe
            glSurfaceView.requestRender()
        }

        /**
         * Yüzey oluşturulduğunda
         */
        override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
            // Arkaplan rengi (koyu mavi-gri)
            gl.glClearColor(0.1f, 0.15f, 0.25f, 1.0f)

            // Derinlik testi, culling ve ışıklandırma
            gl.glEnable(GL10.GL_DEPTH_TEST)
            gl.glDepthFunc(GL10.GL_LEQUAL)
            gl.glEnable(GL10.GL_CULL_FACE)
            gl.glCullFace(GL10.GL_BACK)

            // Smooth shading
            gl.glShadeModel(GL10.GL_SMOOTH)

            // Vertex buffer oluştur
            val vbb = ByteBuffer.allocateDirect(vertices.size * 4)
            vbb.order(ByteOrder.nativeOrder())
            vertexBuffer = vbb.asFloatBuffer()
            vertexBuffer.put(vertices)
            vertexBuffer.position(0)

            // Renk buffer oluştur
            val cbb = ByteBuffer.allocateDirect(colors.size * 4)
            cbb.order(ByteOrder.nativeOrder())
            colorBuffer = cbb.asFloatBuffer()
            colorBuffer.put(colors)
            colorBuffer.position(0)

            // İndeks buffer oluştur
            indexBuffer = ByteBuffer.allocateDirect(indices.size)
            indexBuffer.put(indices)
            indexBuffer.position(0)
        }

        /**
         * Yüzey boyutu değiştiğinde
         */
        override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
            gl.glViewport(0, 0, width, height)
            gl.glMatrixMode(GL10.GL_PROJECTION)
            gl.glLoadIdentity()

            val ratio = width.toFloat() / height
            GLU.gluPerspective(gl, 45.0f, ratio, 0.1f, 100.0f)

            gl.glMatrixMode(GL10.GL_MODELVIEW)
            gl.glLoadIdentity()
        }

        /**
         * Her kare çizildiğinde
         */
        override fun onDrawFrame(gl: GL10) {
            // Ekranı temizle
            gl.glClear(GL10.GL_COLOR_BUFFER_BIT or GL10.GL_DEPTH_BUFFER_BIT)
            gl.glLoadIdentity()

            // Kamerayı daha uzağa yerleştir - modeli daha iyi görmek için
            GLU.gluLookAt(gl, 0f, 0f, 10f, 0f, 0f, 0f, 0f, 1f, 0f)

            // Zemin çizgilerini göster
            drawGrid(gl)

            // Debug bilgisi
            Log.d("FallRenderer", "Model pozisyonu: x=${currentKeyframe.x}, y=${currentKeyframe.y}, z=${currentKeyframe.z}")

            // Telefon modeli
            gl.glPushMatrix()

            // ÖNEMLİ: Daha büyük ölçekte hareket ettir
            gl.glTranslatef(
                currentKeyframe.x * 5.0f,  // 5 kat büyüt
                currentKeyframe.y * 5.0f,  // 5 kat büyüt
                currentKeyframe.z * 5.0f   // 5 kat büyüt
            )

            // Rotasyonu abartılı hale getir
            gl.glRotatef(currentKeyframe.rotX * 2.0f, 1f, 0f, 0f)
            gl.glRotatef(currentKeyframe.rotY * 2.0f, 0f, 1f, 0f)
            gl.glRotatef(currentKeyframe.rotZ * 2.0f, 0f, 0f, 1f)

            // Telefon modelini çiz
            gl.glEnableClientState(GL10.GL_VERTEX_ARRAY)
            gl.glEnableClientState(GL10.GL_COLOR_ARRAY)

            gl.glVertexPointer(3, GL10.GL_FLOAT, 0, vertexBuffer)
            gl.glColorPointer(4, GL10.GL_FLOAT, 0, colorBuffer)
            gl.glDrawElements(GL10.GL_TRIANGLES, indices.size, GL10.GL_UNSIGNED_BYTE, indexBuffer)

            gl.glDisableClientState(GL10.GL_COLOR_ARRAY)
            gl.glDisableClientState(GL10.GL_VERTEX_ARRAY)

            gl.glPopMatrix()
        }

        /**
         * Zemin grid çizimi
         */
        private fun drawGrid(gl: GL10) {
            gl.glDisable(GL10.GL_LIGHTING)

            gl.glLineWidth(1f)
            gl.glColor4f(0.5f, 0.5f, 0.5f, 0.5f)

            // Grid çiz - OpenGL ES 1.0 ile uyumlu kod
            val gridSize = 10
            val gridStep = 0.5f

            // Vertex array için buffer oluştur
            val vertexCount = (gridSize * 2 + 1) * 4 // Her çizgi için 2 nokta
            val vertices = FloatArray(vertexCount * 3) // Her nokta için x,y,z

            var index = 0
            for (i in -gridSize..gridSize) {
                val pos = i * gridStep

                // X çizgileri
                vertices[index++] = -gridSize * gridStep // x1
                vertices[index++] = -2f                  // y1
                vertices[index++] = pos                  // z1

                vertices[index++] = gridSize * gridStep  // x2
                vertices[index++] = -2f                  // y2
                vertices[index++] = pos                  // z2

                // Z çizgileri
                vertices[index++] = pos                  // x1
                vertices[index++] = -2f                  // y1
                vertices[index++] = -gridSize * gridStep // z1

                vertices[index++] = pos                  // x2
                vertices[index++] = -2f                  // y2
                vertices[index++] = gridSize * gridStep  // z2
            }

            // Buffer oluştur
            val vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
            vertexBuffer.put(vertices)
            vertexBuffer.position(0)

            // Çizgileri çiz
            gl.glEnableClientState(GL10.GL_VERTEX_ARRAY)
            gl.glVertexPointer(3, GL10.GL_FLOAT, 0, vertexBuffer)
            gl.glDrawArrays(GL10.GL_LINES, 0, vertexCount)
            gl.glDisableClientState(GL10.GL_VERTEX_ARRAY)

            gl.glEnable(GL10.GL_LIGHTING)
        }
    }
}