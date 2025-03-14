package com.example.myapplication

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * OpenGL ES 2.0 kullanarak telefon modeli çizen sınıf
 * Geliştirilmiş sensör verisi filtreleme ile
 */
class PhoneRenderer : GLSurfaceView.Renderer {

    // Model view projection matrisleri
    private val mMVPMatrix = FloatArray(16)
    private val mProjectionMatrix = FloatArray(16)
    private val mViewMatrix = FloatArray(16)
    private val mModelMatrix = FloatArray(16)

    // Shader program
    private var mProgram = 0

    // Telefon konumu ve rotasyonu - ham değerler
    private var posX = 0f
    private var posY = 0f
    private var posZ = 0f
    private var rotX = 0f
    private var rotY = 0f
    private var rotZ = 0f

    // Filtre parametreleri - filtrelenmiş değerler
    private var filteredRotX = 0f
    private var filteredRotY = 0f
    private var filteredRotZ = 0f
    private var filteredPosX = 0f
    private var filteredPosY = 0f
    private var filteredPosZ = 0f

    // Filtreleme için önceki değerler - yeni eklendi
    private var prevAccelX = 0f
    private var prevAccelY = 0f
    private var prevAccelZ = 0f
    private var prevGyroX = 0f
    private var prevGyroY = 0f
    private var prevGyroZ = 0f

    // Yerçekimi ve lineer ivme değerleri - fizik için
    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 0f
    private var linAccX = 0f
    private var linAccY = 0f
    private var linAccZ = 0f

    // Filtreleme katsayıları - bu değerler titreşimi azaltmak için ayarlanabilir
    private val ACCEL_FILTER_ALPHA = 0.08f  // Düşük değer = daha az titreşim, daha gecikmeli
    private val GYRO_FILTER_ALPHA = 0.15f   // Gyro için biraz daha hızlı tepki
    private val COMP_FILTER_ALPHA = 0.02f   // Complementary filtre katsayısı

    // Ölçekleme faktörleri
    private val ACCELERATION_SCALE = 0.05f // İvmeyi azaltmak için ölçekleme faktörü
    private val ROTATION_SCALE = 0.6f      // Rotasyonu azaltmak için ölçekleme faktörü

    // Fizik simülasyonu için
    private var velocity = Vector3(0f, 0f, 0f)
    private var isInFreeFall = false
    private var lastUpdateTime = System.nanoTime()

    // OpenGL nesneleri
    private var phoneModel: Phone? = null
    private var floor: Floor? = null

    // Vertex shader kodu - değişmedi
    private val vertexShaderCode =
        "uniform mat4 uMVPMatrix;" +
                "attribute vec4 vPosition;" +
                "attribute vec4 vColor;" +
                "varying vec4 fragmentColor;" +
                "void main() {" +
                "  gl_Position = uMVPMatrix * vPosition;" +
                "  fragmentColor = vColor;" +
                "}"

    // Fragment shader kodu - değişmedi
    private val fragmentShaderCode =
        "precision mediump float;" +
                "varying vec4 fragmentColor;" +
                "void main() {" +
                "  gl_FragColor = fragmentColor;" +
                "}"

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Arka plan rengini ayarla - koyu mavi-gri
        GLES20.glClearColor(0.05f, 0.05f, 0.1f, 1.0f)

        // Derinlik testini etkinleştir
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        // Shader programını oluştur ve yükle
        mProgram = createProgram()

        // Telefon modelini oluştur
        phoneModel = Phone(mProgram)

        // Zemin modelini oluştur
        floor = Floor(mProgram)

        // Log bilgisi
        Log.d("PhoneRenderer", "OpenGL surface oluşturuldu")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        // Viewport'u ekrana uygun şekilde ayarla
        GLES20.glViewport(0, 0, width, height)

        // Projeksiyon matrisini hesapla
        val ratio = width.toFloat() / height
        Matrix.frustumM(mProjectionMatrix, 0, -ratio, ratio, -1f, 1f, 2f, 20f)

        // Kamera pozisyonunu ayarla
        Matrix.setLookAtM(mViewMatrix, 0,
            0f, 2f, 5f,  // Kamera pozisyonu (x, y, z)
            0f, 0f, 0f,  // Bakış noktası (look-at point)
            0f, 1f, 0f   // Yukarı vektörü (up vector)
        )
    }

    override fun onDrawFrame(gl: GL10?) {
        // Fizik güncellemesi
        updatePhysics()

        // Ekranı temizle
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // Shader programını kullan
        GLES20.glUseProgram(mProgram)

        // Model matrisi - zemin için
        Matrix.setIdentityM(mModelMatrix, 0)
        Matrix.translateM(mModelMatrix, 0, 0f, -2f, 0f)  // Zemini aşağıya taşı

        // Model-View-Projection matrisini hesapla
        Matrix.multiplyMM(mMVPMatrix, 0, mViewMatrix, 0, mModelMatrix, 0)
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mMVPMatrix, 0)

        // Zemini çiz
        floor?.draw(mMVPMatrix)

        // Model matrisi - telefon için
        Matrix.setIdentityM(mModelMatrix, 0)

        // Telefon pozisyonunu ve rotasyonunu uygula - filtrelenmiş değerleri kullan
        Matrix.translateM(mModelMatrix, 0, filteredPosX * ACCELERATION_SCALE,
            filteredPosY * ACCELERATION_SCALE,
            filteredPosZ * ACCELERATION_SCALE)

        // Jiroskop verilerini X ve Y eksenleri için kullan
        Matrix.rotateM(mModelMatrix, 0, filteredRotX * ROTATION_SCALE, 1f, 0f, 0f)  // X ekseni rotasyonu
        Matrix.rotateM(mModelMatrix, 0, filteredRotY * ROTATION_SCALE, 0f, 1f, 0f)  // Y ekseni rotasyonu

        // İvmeölçer verisini Z ekseni için kullan
        Matrix.rotateM(mModelMatrix, 0, filteredRotZ * ROTATION_SCALE, 0f, 0f, 1f)  // Z ekseni rotasyonu

        // Model-View-Projection matrisini hesapla
        Matrix.multiplyMM(mMVPMatrix, 0, mViewMatrix, 0, mModelMatrix, 0)
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mMVPMatrix, 0)

        // Telefonu çiz
        phoneModel?.draw(mMVPMatrix)
    }
    /**
     * Fizik simülasyonunu güncelle
     * İyileştirilmiş sürüm: Daha yumuşak hareket ve titreşim filtreleme
     */
    private fun updatePhysics() {
        // Zaman delta hesapla
        val currentTime = System.nanoTime()
        val deltaTime = (currentTime - lastUpdateTime) / 1_000_000_000f // saniye cinsinden
        lastUpdateTime = currentTime

        // Temel dünya fizik özellikleri
        val GRAVITY_ACCEL = 9.81f // m/s²
        val FLOOR_Y = -2f         // Zemin Y pozisyonu
        val DAMPING = 0.92f        // Hız sönümlemesi (0-1 arasında)

        // Filtrelenmiş pozisyon değerlerini güncelle (düşük geçiş filtresi)
        filteredPosX = lowPassFilter(posX, filteredPosX, ACCEL_FILTER_ALPHA)
        filteredPosY = lowPassFilter(posY, filteredPosY, ACCEL_FILTER_ALPHA)
        filteredPosZ = lowPassFilter(posZ, filteredPosZ, ACCEL_FILTER_ALPHA)

        // Rotasyon değerlerini güncelle - gyro X ve Y için, accel Z için
        // Jitter azaltmak için complementary filter kullan
        filteredRotX = complementaryFilterAngle(rotX, prevGyroX * deltaTime, COMP_FILTER_ALPHA)
        filteredRotY = complementaryFilterAngle(rotY, prevGyroY * deltaTime, COMP_FILTER_ALPHA)

        // Z rotasyonu için ivmeölçeri kullan
        // Z ekseni için düşük geçiş filtresi - Z ekseninin yavaş değişmesi daha doğal görünür
        filteredRotZ = lowPassFilter(rotZ, filteredRotZ, ACCEL_FILTER_ALPHA / 2f) // Z için daha az titreşim

        // Önceki değerleri kaydet
        prevGyroX = rotX
        prevGyroY = rotY
        prevAccelX = posX
        prevAccelY = posY
        prevAccelZ = posZ

        // Lineer ivme büyüklüğünü hesapla (serbest düşüş tespiti için)
        val linAccMagnitude = kotlin.math.sqrt(
            linAccX * linAccX + linAccY * linAccY + linAccZ * linAccZ
        )

        // Serbest düşüş tespiti ve simülasyonu
        isInFreeFall = linAccMagnitude < 0.5f

        if (isInFreeFall) {
            // Düşüş sırasında yerçekimi etkisini ekle
            velocity.y -= GRAVITY_ACCEL * deltaTime

            // Hızı kullanarak pozisyonu güncelle
            filteredPosY += velocity.y * deltaTime

            // Zemine çarpma kontrolü
            if (filteredPosY < FLOOR_Y) {
                filteredPosY = FLOOR_Y
                // Sıçrama efekti (elastik çarpışma)
                velocity.y = -velocity.y * 0.6f // 60% enerji korunumu

                // Sürtünme etkisi (yatay hızı azalt)
                velocity.x *= 0.8f
                velocity.z *= 0.8f
            }
        } else {
            // Normal hareket - sensör verilerini doğrudan kullan
            // Position değerleri updatePhonePosition() ile güncellenir

            // Genel bir hız sönümlemesi ekle - hareket daha yumuşak olacak
            velocity.x *= DAMPING
            velocity.y *= DAMPING
            velocity.z *= DAMPING

            // Hafif bir dengeleme ekle - yavaşça yere çök
            if (filteredPosY > FLOOR_Y && abs(velocity.y) < 0.1f) {
                filteredPosY = filteredPosY * 0.99f + FLOOR_Y * 0.01f // Yumuşak dengeleme
            }
        }

        // Yatay hareket (X ve Z) - lineer ivme verilerini kullan
        velocity.x += linAccX * deltaTime * 0.1f // Daha yavaş hareket için ölçekle
        velocity.z += linAccZ * deltaTime * 0.1f

        // Hızı kullanarak yatay pozisyonu güncelle
        filteredPosX += velocity.x * deltaTime
        filteredPosZ += velocity.z * deltaTime

        // Fizik sınırları uygula
        val MAX_VELOCITY = 10f
        velocity.x = max(-MAX_VELOCITY, min(MAX_VELOCITY, velocity.x))
        velocity.y = max(-MAX_VELOCITY, min(MAX_VELOCITY, velocity.y))
        velocity.z = max(-MAX_VELOCITY, min(MAX_VELOCITY, velocity.z))
    }

    /**
     * Düşük geçiş filtresi - titreşim azaltma
     * @param input Yeni değer
     * @param lastOutput Son filtrelenmiş değer
     * @param alpha Filtre katsayısı (0-1 arası), küçük değer = daha fazla filtreleme
     * @return Filtrelenmiş değer
     */
    private fun lowPassFilter(input: Float, lastOutput: Float, alpha: Float): Float {
        return lastOutput + alpha * (input - lastOutput)
    }

    /**
     * Complementary filtre - jiroskop ve ivmeölçer verilerini birleştirmek için
     * @param accelAngle İvmeölçerden gelen açı (uzun vadeli referans)
     * @param gyroAngleDelta Jiroskoptan gelen açı değişimi (kısa vadeli doğruluk)
     * @param alpha Filtre katsayısı (0-1 arası)
     * @return Filtrelenmiş açı
     */
    private fun complementaryFilterAngle(accelAngle: Float, gyroAngleDelta: Float, alpha: Float): Float {
        return alpha * accelAngle + (1 - alpha) * (filteredRotX + gyroAngleDelta)
    }

    /**
     * Telefon pozisyonunu ve rotasyonunu güncelle
     * İyileştirilmiş sürüm: İlave sensör verileri ile daha iyi titreşim filtreleme
     */
    fun updatePhonePosition(
        x: Float, y: Float, z: Float,
        rx: Float, ry: Float, rz: Float,
        gravX: Float = 0f, gravY: Float = 0f, gravZ: Float = 0f,
        linearAccX: Float = 0f, linearAccY: Float = 0f, linearAccZ: Float = 0f
    ) {
        // Ham değerleri kaydet
        posX = x
        posY = y
        posZ = z

        // Jiroskop için X ve Y rotasyonlarını kullan
        rotX = rx
        rotY = ry

        // İvmeölçer için Z rotasyonunu kullan (istediğiniz gibi)
        rotZ = rz

        // Yerçekimi ve lineer ivme değerlerini güncelle
        gravityX = gravX
        gravityY = gravY
        gravityZ = gravZ

        // Aşırı değişimleri sınırla - ani titreşimleri azaltır
        val maxAccelChange = 2.5f // m/s² - 2.5 değeri daha az ani değişim sağlar
        linAccX = clampChange(linearAccX, linAccX, maxAccelChange)
        linAccY = clampChange(linearAccY, linAccY, maxAccelChange)
        linAccZ = clampChange(linearAccZ, linAccZ, maxAccelChange)
    }

    /**
     * Değişimi belirli bir maksimum değerle sınırlar
     * Aşırı ani değişimleri önlemek için
     */
    private fun clampChange(newValue: Float, oldValue: Float, maxChange: Float): Float {
        val change = newValue - oldValue
        return when {
            change > maxChange -> oldValue + maxChange
            change < -maxChange -> oldValue - maxChange
            else -> newValue
        }
    }

    /**
     * Shader programı oluştur ve yükle
     */
    private fun createProgram(): Int {
        // Vertex shader'ı derleme
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)

        // Fragment shader'ı derleme
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        // Shader programını oluştur
        val program = GLES20.glCreateProgram()

        // Shader'ları programa ekle
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)

        // Programı link et
        GLES20.glLinkProgram(program)

        // Link durumunu kontrol et
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)

        if (linkStatus[0] != GLES20.GL_TRUE) {
            val info = GLES20.glGetProgramInfoLog(program)
            Log.e("PhoneRenderer", "Shader program link hatası: $info")
            GLES20.glDeleteProgram(program)
            return 0
        }

        return program
    }

    /**
     * Shader kodu yükle ve derle
     */
    private fun loadShader(type: Int, shaderCode: String): Int {
        // Shader oluştur
        val shader = GLES20.glCreateShader(type)

        // Shader kodunu yükle ve derle
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)

        // Derleme durumunu kontrol et
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)

        if (compileStatus[0] != GLES20.GL_TRUE) {
            val info = GLES20.glGetShaderInfoLog(shader)
            Log.e("PhoneRenderer", "Shader derleme hatası: $info")
            GLES20.glDeleteShader(shader)
            return 0
        }

        return shader
    }

    /**
     * 3D vektör veri sınıfı - değişmedi
     */
    data class Vector3(var x: Float, var y: Float, var z: Float)
    /**
     * Telefon modeli sınıfı
     */
    inner class Phone(private val program: Int) {
        // Telefon koordinatları (3D kutu şeklinde)
        private val coords = floatArrayOf(
            // Ön yüz
            -0.4f, -0.8f, 0.05f,  // 0: sol alt ön
            0.4f, -0.8f, 0.05f,  // 1: sağ alt ön
            0.4f,  0.8f, 0.05f,  // 2: sağ üst ön
            -0.4f,  0.8f, 0.05f,  // 3: sol üst ön

            // Arka yüz
            -0.4f, -0.8f, -0.05f, // 4: sol alt arka
            0.4f, -0.8f, -0.05f, // 5: sağ alt arka
            0.4f,  0.8f, -0.05f, // 6: sağ üst arka
            -0.4f,  0.8f, -0.05f, // 7: sol üst arka

            // Ekran (telefon önünün biraz önünde)
            -0.35f, -0.75f, 0.051f, // 8: sol alt ekran
            0.35f, -0.75f, 0.051f, // 9: sağ alt ekran
            0.35f,  0.75f, 0.051f, // 10: sağ üst ekran
            -0.35f,  0.75f, 0.051f  // 11: sol üst ekran
        )

        // Yüzey indeksleri
        private val indices = shortArrayOf(
            // Gövde
            0, 1, 2, 0, 2, 3,   // Ön
            4, 5, 6, 4, 6, 7,   // Arka
            0, 1, 5, 0, 5, 4,   // Alt
            3, 2, 6, 3, 6, 7,   // Üst
            0, 3, 7, 0, 7, 4,   // Sol
            1, 2, 6, 1, 6, 5,   // Sağ

            // Ekran
            8, 9, 10, 8, 10, 11
        )

        // Renkler
        private val colors = floatArrayOf(
            // Gövde rengi - koyu gri (6 yüzey, her yüzey 4 köşe)
            0.2f, 0.2f, 0.2f, 1.0f,  // 0: sol alt ön
            0.2f, 0.2f, 0.2f, 1.0f,  // 1: sağ alt ön
            0.2f, 0.2f, 0.2f, 1.0f,  // 2: sağ üst ön
            0.2f, 0.2f, 0.2f, 1.0f,  // 3: sol üst ön

            0.1f, 0.1f, 0.1f, 1.0f,  // 4: sol alt arka
            0.1f, 0.1f, 0.1f, 1.0f,  // 5: sağ alt arka
            0.1f, 0.1f, 0.1f, 1.0f,  // 6: sağ üst arka
            0.1f, 0.1f, 0.1f, 1.0f,  // 7: sol üst arka

            // Ekran rengi - mavi (4 köşe)
            0.0f, 0.5f, 0.8f, 1.0f,  // 8: sol alt ekran
            0.0f, 0.5f, 0.8f, 1.0f,  // 9: sağ alt ekran
            0.0f, 0.5f, 0.8f, 1.0f,  // 10: sağ üst ekran
            0.0f, 0.5f, 0.8f, 1.0f   // 11: sol üst ekran
        )

        // OpenGL buffer'ları
        private val vertexBuffer: FloatBuffer
        private val colorBuffer: FloatBuffer
        private val indexBuffer: ShortBuffer

        // OpenGL attributes
        private var positionHandle = 0
        private var colorHandle = 0
        private var mvpMatrixHandle = 0

        init {
            // Vertex buffer oluştur
            vertexBuffer = ByteBuffer.allocateDirect(coords.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(coords)
                    position(0)
                }

            // Renk buffer oluştur
            colorBuffer = ByteBuffer.allocateDirect(colors.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(colors)
                    position(0)
                }

            // İndeks buffer oluştur
            indexBuffer = ByteBuffer.allocateDirect(indices.size * 2)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer()
                .apply {
                    put(indices)
                    position(0)
                }
        }

        fun draw(mvpMatrix: FloatArray) {
            // Shader attribute ve uniform handle'larını al
            positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
            colorHandle = GLES20.glGetAttribLocation(program, "vColor")
            mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")

            // Position attribute'unu etkinleştir
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(
                positionHandle,
                3,          // 3 float per vertex (x, y, z)
                GLES20.GL_FLOAT,
                false,
                0,          // stride
                vertexBuffer
            )

            // Color attribute'unu etkinleştir
            GLES20.glEnableVertexAttribArray(colorHandle)
            GLES20.glVertexAttribPointer(
                colorHandle,
                4,          // 4 float per color (r, g, b, a)
                GLES20.GL_FLOAT,
                false,
                0,          // stride
                colorBuffer
            )

            // MVP matrisini ayarla
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

            // Telefonu çiz
            GLES20.glDrawElements(
                GLES20.GL_TRIANGLES,
                indices.size,
                GLES20.GL_UNSIGNED_SHORT,
                indexBuffer
            )

            // Attribute'ları devre dışı bırak
            GLES20.glDisableVertexAttribArray(positionHandle)
            GLES20.glDisableVertexAttribArray(colorHandle)
        }
    }

    /**
     * Zemin sınıfı - 3D dünyaya bir zemin ekler
     */
    inner class Floor(private val program: Int) {

        // Zemin koordinatları (kare şeklinde)
        private val coords = floatArrayOf(
            -10f, 0f, -10f,  // 0: sol arka
            10f, 0f, -10f,  // 1: sağ arka
            10f, 0f,  10f,  // 2: sağ ön
            -10f, 0f,  10f   // 3: sol ön
        )

        // Yüzey indeksleri
        private val indices = shortArrayOf(
            0, 1, 2, 0, 2, 3  // Üst yüz
        )

        // Renkler - ızgara görünümü için iki ton
        private val colors = floatArrayOf(
            0.3f, 0.3f, 0.4f, 1.0f,  // 0: sol arka
            0.3f, 0.3f, 0.4f, 1.0f,  // 1: sağ arka
            0.3f, 0.3f, 0.4f, 1.0f,  // 2: sağ ön
            0.3f, 0.3f, 0.4f, 1.0f   // 3: sol ön
        )

        // OpenGL buffer'ları
        private val vertexBuffer: FloatBuffer
        private val colorBuffer: FloatBuffer
        private val indexBuffer: ShortBuffer

        // OpenGL attributes
        private var positionHandle = 0
        private var colorHandle = 0
        private var mvpMatrixHandle = 0

        init {
            // Vertex buffer oluştur
            vertexBuffer = ByteBuffer.allocateDirect(coords.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(coords)
                    position(0)
                }

            // Renk buffer oluştur
            colorBuffer = ByteBuffer.allocateDirect(colors.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(colors)
                    position(0)
                }

            // İndeks buffer oluştur
            indexBuffer = ByteBuffer.allocateDirect(indices.size * 2)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer()
                .apply {
                    put(indices)
                    position(0)
                }
        }

        fun draw(mvpMatrix: FloatArray) {
            // Shader attribute ve uniform handle'larını al
            positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
            colorHandle = GLES20.glGetAttribLocation(program, "vColor")
            mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")

            // Position attribute'unu etkinleştir
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(
                positionHandle,
                3,
                GLES20.GL_FLOAT,
                false,
                0,
                vertexBuffer
            )

            // Color attribute'unu etkinleştir
            GLES20.glEnableVertexAttribArray(colorHandle)
            GLES20.glVertexAttribPointer(
                colorHandle,
                4,
                GLES20.GL_FLOAT,
                false,
                0,
                colorBuffer
            )

            // MVP matrisini ayarla
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

            // Zemini çiz
            GLES20.glDrawElements(
                GLES20.GL_TRIANGLES,
                indices.size,
                GLES20.GL_UNSIGNED_SHORT,
                indexBuffer
            )

            // Attribute'ları devre dışı bırak
            GLES20.glDisableVertexAttribArray(positionHandle)
            GLES20.glDisableVertexAttribArray(colorHandle)
        }
    }
}