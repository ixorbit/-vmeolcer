package com.example.myapplication

import android.content.Context
import android.opengl.GLSurfaceView
import android.opengl.GLU
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
        glSurfaceView.renderer.setKeyframe(
            if (keyframes.isNotEmpty()) keyframes[0] else
                FallKeyframe(0f, 0f, 0f, 0f, 0f, 0f, 0)
        )

        // Hazırlık tamamlandı
        onSimulationPreparedListener?.invoke()
    }

    /**
     * Anahtar kareleri hazırla
     */
    private fun prepareKeyframes() {
        try {
            // Düşüş başlangıç ve bitiş indekslerini bul
            val startIndex = sensorDataList.indexOfFirst { it.timestamp == freeFallReport.startTime }
            val endIndex = sensorDataList.indexOfFirst { it.timestamp == freeFallReport.endTime }

            if (startIndex == -1 || endIndex == -1 || startIndex >= endIndex) {
                return
            }

            // Simülasyon verileri
            val fallData = sensorDataList.subList(startIndex, endIndex + 1)

            // İvmelerden hesaplanan pozisyonlar
            var x = 0f
            var y = 0f
            var z = 0f
            var vx = 0f
            var vy = 0f
            var vz = 0f
            var lastTime = fallData.firstOrNull()?.timestamp ?: 0L

            keyframes.clear()
            for (data in fallData) {
                // Zaman farkını hesapla (saniye cinsinden)
                val dt = (data.timestamp - lastTime) / 1000f
                lastTime = data.timestamp

                // Hız değişimleri (ivme × zaman)
                vx += data.x * dt
                vy += data.y * dt
                vz += (data.z - 9.81f) * dt  // Yerçekimi düzeltmesi

                // Pozisyon değişimleri (hız × zaman)
                x += vx * dt
                y += vy * dt
                z += vz * dt

                // Keyframe oluştur
                keyframes.add(FallKeyframe(
                    x = x * 0.1f,  // Ölçek faktörü
                    y = y * 0.1f,
                    z = z * 0.1f,
                    rotX = data.rotX * 57.3f,  // Radyan -> Derece
                    rotY = data.rotY * 57.3f,
                    rotZ = data.rotZ * 57.3f,
                    timestamp = data.timestamp
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Simülasyonu oynat
     */
    fun play() {
        if (keyframes.isEmpty()) return
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
        if (keyframes.isNotEmpty()) {
            glSurfaceView.renderer.setKeyframe(keyframes[0])
        }
        isPlaying = false
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
    }

    /**
     * Kareleri animasyonlu göster
     */
    private fun animateFrames() {
        if (!isPlaying || currentFrameIndex >= keyframes.size - 1) {
            return
        }

        glSurfaceView.renderer.setKeyframe(keyframes[currentFrameIndex])
        val progress = (currentFrameIndex * 100) / keyframes.size
        onProgressUpdateListener?.invoke(progress)

        currentFrameIndex++

        // Sonraki kareyi göstermek için zamanlayıcı
        glSurfaceView.postDelayed({ animateFrames() }, 50L)
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

        // Telefon modeli (basit dikdörtgen prizma)
        private val vertices = floatArrayOf(
            // Ön yüz
            -0.5f, -1.0f, 0.15f,  // 0
            0.5f, -1.0f, 0.15f,   // 1
            0.5f, 1.0f, 0.15f,    // 2
            -0.5f, 1.0f, 0.15f,   // 3

            // Arka yüz
            -0.5f, -1.0f, -0.15f, // 4
            0.5f, -1.0f, -0.15f,  // 5
            0.5f, 1.0f, -0.15f,   // 6
            -0.5f, 1.0f, -0.15f   // 7
        )

        // Yüz indeksleri
        private val indices = byteArrayOf(
            // Ön
            0, 1, 2, 0, 2, 3,
            // Sağ
            1, 5, 6, 1, 6, 2,
            // Arka
            5, 4, 7, 5, 7, 6,
            // Sol
            4, 0, 3, 4, 3, 7,
            // Üst
            3, 2, 6, 3, 6, 7,
            // Alt
            4, 5, 1, 4, 1, 0
        )

        // Renkler (çizilecek her köşe için)
        private val colors = floatArrayOf(
            // Ön yüz
            0.0f, 0.5f, 1.0f, 1.0f, // mavi
            0.0f, 0.5f, 1.0f, 1.0f,
            0.0f, 0.5f, 1.0f, 1.0f,
            0.0f, 0.5f, 1.0f, 1.0f,

            // Arka yüz
            0.0f, 0.4f, 0.8f, 1.0f, // daha koyu mavi
            0.0f, 0.4f, 0.8f, 1.0f,
            0.0f, 0.4f, 0.8f, 1.0f,
            0.0f, 0.4f, 0.8f, 1.0f
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
            // Arkaplan rengi (koyu mavi)
            gl.glClearColor(0.0f, 0.0f, 0.2f, 1.0f)

            // Derinlik testi ve culling etkinleştir
            gl.glEnable(GL10.GL_DEPTH_TEST)
            gl.glDepthFunc(GL10.GL_LEQUAL)
            gl.glEnable(GL10.GL_CULL_FACE)
            gl.glCullFace(GL10.GL_BACK)

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

            // Kamera pozisyonu
            GLU.gluLookAt(gl, 0f, 0f, 5f, 0f, 0f, 0f, 0f, 1f, 0f)

            // Telefon pozisyonu ve rotasyonu
            gl.glTranslatef(
                currentKeyframe.x,
                currentKeyframe.y,
                currentKeyframe.z
            )

            // Rotasyon - rölatif açıları uygula
            gl.glRotatef(currentKeyframe.rotX, 1f, 0f, 0f)
            gl.glRotatef(currentKeyframe.rotY, 0f, 1f, 0f)
            gl.glRotatef(currentKeyframe.rotZ, 0f, 0f, 1f)

            // Işıklandırma etkinleştir
            gl.glEnable(GL10.GL_LIGHTING)
            gl.glEnable(GL10.GL_LIGHT0)

            // Ambient ve diffuse ışık renkleri
            val ambientLight = floatArrayOf(0.2f, 0.2f, 0.2f, 1f)
            val diffuseLight = floatArrayOf(1f, 1f, 1f, 1f)
            val lightPosition = floatArrayOf(0f, 0f, 5f, 1f)

            gl.glLightfv(GL10.GL_LIGHT0, GL10.GL_AMBIENT, ambientLight, 0)
            gl.glLightfv(GL10.GL_LIGHT0, GL10.GL_DIFFUSE, diffuseLight, 0)
            gl.glLightfv(GL10.GL_LIGHT0, GL10.GL_POSITION, lightPosition, 0)

            // Malzeme özellikleri
            val materialAmbient = floatArrayOf(0.1f, 0.1f, 0.6f, 1f)
            val materialDiffuse = floatArrayOf(0.2f, 0.2f, 0.8f, 1f)

            gl.glMaterialfv(GL10.GL_FRONT_AND_BACK, GL10.GL_AMBIENT, materialAmbient, 0)
            gl.glMaterialfv(GL10.GL_FRONT_AND_BACK, GL10.GL_DIFFUSE, materialDiffuse, 0)

            // Vertex ve indeks buffer kullanarak çiz
            gl.glEnableClientState(GL10.GL_VERTEX_ARRAY)
            gl.glEnableClientState(GL10.GL_COLOR_ARRAY)

            gl.glVertexPointer(3, GL10.GL_FLOAT, 0, vertexBuffer)
            gl.glColorPointer(4, GL10.GL_FLOAT, 0, colorBuffer)
            gl.glDrawElements(GL10.GL_TRIANGLES, indices.size, GL10.GL_UNSIGNED_BYTE, indexBuffer)

            gl.glDisableClientState(GL10.GL_COLOR_ARRAY)
            gl.glDisableClientState(GL10.GL_VERTEX_ARRAY)
            gl.glDisable(GL10.GL_LIGHTING)
        }
    }
}