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
import kotlin.math.sqrt

/**
 * OpenGL ES 2.0 kullanarak telefon modeli çizen ve sensör verilerine göre hareket ettiren sınıf
 * İyileştirilmiş model oryantasyonu ve filtreleme ile
 */
class PhoneRenderer : GLSurfaceView.Renderer {

    // Model view projection matrisleri
    private val mMVPMatrix = FloatArray(16)
    private val mProjectionMatrix = FloatArray(16)
    private val mViewMatrix = FloatArray(16)
    private val mModelMatrix = FloatArray(16)
    private val mRotationMatrix = FloatArray(16) // Yeni: rotasyon için ayrı bir matris

    // Shader program
    private var mProgram = 0

    // Telefon konumu ve rotasyonu - ham değerler
    private var posX = 0f
    private var posY = 0f
    private var posZ = 0f
    private var rotX = 0f
    private var rotY = 0f
    private var rotZ = 0f
    private var magX = 0f // Pusula sensörü için X değeri
    private var magY = 0f // Pusula sensörü için Y değeri
    private var magZ = 0f // Pusula sensörü için Z değeri

    // Filtre parametreleri - filtrelenmiş değerler
    private var filteredRotX = 0f
    private var filteredRotY = 0f
    private var filteredRotZ = 0f
    private var filteredPosX = 0f
    private var filteredPosY = 0f
    private var filteredPosZ = 0f
    private var deviceOrientation = FloatArray(3) { 0f } // Yeni: cihaz oryantasyonu

    // Filtreleme için önceki değerler
    private var prevRotX = 0f
    private var prevRotY = 0f
    private var prevRotZ = 0f
    private var prevPosX = 0f
    private var prevPosY = 0f
    private var prevPosZ = 0f

    // Yerçekimi ve lineer ivme değerleri - fizik için
    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 0f
    private var linAccX = 0f
    private var linAccY = 0f
    private var linAccZ = 0f

    // Kalibrasyon değerleri
    private var offsetRotX = 0f
    private var offsetRotY = 0f
    private var offsetRotZ = 0f
    private var isCalibrated = false

    // Filtreleme katsayıları - bu değerler titreşimi azaltmak için ayarlanabilir
    private val ACCEL_FILTER_ALPHA = 0.06f  // İvme için güçlü filtreleme (0.06)
    private val GYRO_FILTER_ALPHA = 0.1f    // Jiroskop için orta düzey filtreleme (0.1)
    private val COMP_FILTER_ALPHA = 0.02f   // Complementary filtre için düşük değer (0.02)
    private val MAG_FILTER_ALPHA = 0.05f    // Pusula için güçlü filtreleme (0.05)

    // Ölçekleme faktörleri
    private val POSITION_SCALE = 0.03f      // Konum değişimi için ölçekleme (daha az hareket)
    private val ROTATION_SCALE = 1.0f       // Rotasyon için tam ölçek (gerçekçi dönüş)

    // Arka plan rengi ve zemin tekstür değişkenleri
    private val bgColorR = 0.02f  // Koyu mavi tonları
    private val bgColorG = 0.02f
    private val bgColorB = 0.05f
    private val bgColorA = 1.0f

    // Fizik simülasyonu için
    private var velocity = Vector3(0f, 0f, 0f)
    private var lastUpdateTime = System.nanoTime()

    // OpenGL nesneleri
    private var phoneModel: Phone? = null
    private var floor: Floor? = null
    private var grid: Grid? = null  // Yeni: ızgara eklendi

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
        // Arka plan rengini ayarla - daha koyu ton
        GLES20.glClearColor(bgColorR, bgColorG, bgColorB, bgColorA)

        // Derinlik testini etkinleştir
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        // Shader programını oluştur ve yükle
        mProgram = createProgram()

        // Telefon modelini oluştur
        phoneModel = Phone(mProgram)

        // Zemin modelini oluştur
        floor = Floor(mProgram)

        // Izgara modelini oluştur
        grid = Grid(mProgram)

        // Rotasyon matrisini başlat
        Matrix.setIdentityM(mRotationMatrix, 0)

        // Log bilgisi
        Log.d("PhoneRenderer", "OpenGL surface oluşturuldu")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        // Viewport'u ekrana uygun şekilde ayarla
        GLES20.glViewport(0, 0, width, height)

        // Projeksiyon matrisini hesapla
        val ratio = width.toFloat() / height
        Matrix.frustumM(mProjectionMatrix, 0, -ratio, ratio, -1f, 1f, 1f, 40f)

        // Kamera pozisyonunu ayarla - daha uzaktan bak
        Matrix.setLookAtM(mViewMatrix, 0,
            0f, 4f, 10f,  // Kamera pozisyonu (x, y, z) - daha yüksekten ve uzaktan bak
            0f, 0f, 0f,   // Bakış noktası (look-at point)
            0f, 1f, 0f    // Yukarı vektörü (up vector)
        )
    }

    override fun onDrawFrame(gl: GL10?) {
        // Fizik güncellemesi
        updatePhysics()

        // Ekranı temizle
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // Shader programını kullan
        GLES20.glUseProgram(mProgram)

        // ---- IZGARAYI ÇİZ ----
        Matrix.setIdentityM(mModelMatrix, 0)
        // Izgarayı yatay tutuyoruz, ancak daha aşağıda konumlandırıyoruz
        Matrix.translateM(mModelMatrix, 0, 0f, -2f, 0f)

        // Model-View-Projection matrisini hesapla
        Matrix.multiplyMM(mMVPMatrix, 0, mViewMatrix, 0, mModelMatrix, 0)
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mMVPMatrix, 0)

        // Izgarayı çiz
        grid?.draw(mMVPMatrix)

        // ---- ZEMİNİ ÇİZ ----
        Matrix.setIdentityM(mModelMatrix, 0)
        Matrix.translateM(mModelMatrix, 0, 0f, -2f, 0f)  // Zemini aşağıya taşı

        // Model-View-Projection matrisini hesapla
        Matrix.multiplyMM(mMVPMatrix, 0, mViewMatrix, 0, mModelMatrix, 0)
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mMVPMatrix, 0)

        // Zemini çiz
        floor?.draw(mMVPMatrix)

        // ---- TELEFONU ÇİZ ----
        // Model matrisi - telefon için
        Matrix.setIdentityM(mModelMatrix, 0)

        // İlk olarak modeli doğru pozisyona taşı
        Matrix.translateM(mModelMatrix, 0,
            filteredPosX * POSITION_SCALE,
            filteredPosY * POSITION_SCALE,
            filteredPosZ * POSITION_SCALE)

        // Sonra rotasyon matrisini uygula (oryantasyon için)
        // Not: Rotasyonları uygulamadan önce matris çoğaltmasını yap
        val tempMatrix = FloatArray(16)
        System.arraycopy(mModelMatrix, 0, tempMatrix, 0, 16)

        // Jiroskop verilerini kullanarak rotasyon matrisini oluştur
        createRotationMatrix()

        // Rotasyon matrisini modele uygula
        Matrix.multiplyMM(mModelMatrix, 0, tempMatrix, 0, mRotationMatrix, 0)

        // Model-View-Projection matrisini hesapla
        Matrix.multiplyMM(mMVPMatrix, 0, mViewMatrix, 0, mModelMatrix, 0)
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mMVPMatrix, 0)

        // Telefonu çiz
        phoneModel?.draw(mMVPMatrix)
    }
    /**
     * Rotasyon matrisini oluşturur
     * Jiroskop ve pusula verilerini kullanarak daha doğru bir oryantasyon sağlar
     */
    private fun createRotationMatrix() {
        // Önce matrisi sıfırla
        Matrix.setIdentityM(mRotationMatrix, 0)

        // Jiroskop verilerinden rotasyon yapılıyor
        // X ve Y eksenleri için jiroskop verileri kullanılır
        Matrix.rotateM(mRotationMatrix, 0, filteredRotX * ROTATION_SCALE, 1f, 0f, 0f)  // X ekseni rotasyonu
        Matrix.rotateM(mRotationMatrix, 0, filteredRotY * ROTATION_SCALE, 0f, 1f, 0f)  // Y ekseni rotasyonu

        // Z ekseni rotasyonu için jiroskop veya manyetik alan sensörü kullanılabilir
        // Burada jiroskop verilerini kullanıyoruz
        Matrix.rotateM(mRotationMatrix, 0, filteredRotZ * ROTATION_SCALE, 0f, 0f, 1f)  // Z ekseni rotasyonu
    }

    /**
     * Fizik simülasyonunu güncelle
     * İyileştirilmiş sürüm: Daha doğru oryantasyon ve daha az titreşim
     */
    private fun updatePhysics() {
        // Zaman delta hesapla
        val currentTime = System.nanoTime()
        val deltaTime = (currentTime - lastUpdateTime) / 1_000_000_000f // saniye cinsinden
        lastUpdateTime = currentTime

        // Temel dünya fizik özellikleri
        val GRAVITY_ACCEL = 9.81f // m/s²
        val FLOOR_Y = -2f         // Zemin Y pozisyonu
        val DAMPING = 0.94f       // Hız sönümlemesi (0-1 arasında) - 0.94 daha yumuşak hareket

        // Filtrelenmiş pozisyon değerlerini güncelle (düşük geçiş filtresi)
        filteredPosX = lowPassFilter(posX, filteredPosX, ACCEL_FILTER_ALPHA)
        filteredPosY = lowPassFilter(posY, filteredPosY, ACCEL_FILTER_ALPHA)
        filteredPosZ = lowPassFilter(posZ, filteredPosZ, ACCEL_FILTER_ALPHA)

        // Rotasyon değerlerini güncelle - cihaz oryantasyonu için
        // X ve Y için jiroskop verilerini kullan
        filteredRotX = complementaryFilterAngle(rotX, prevRotX, GYRO_FILTER_ALPHA)
        filteredRotY = complementaryFilterAngle(rotY, prevRotY, GYRO_FILTER_ALPHA)

        // Z için jiroskop verilerini kullan ya da pusula verilerinden hesapla
        filteredRotZ = complementaryFilterAngle(rotZ, prevRotZ, GYRO_FILTER_ALPHA)

        // Kalibrasyon değerlerini uygula eğer kalibre edilmişse
        if (isCalibrated) {
            filteredRotX -= offsetRotX
            filteredRotY -= offsetRotY
            filteredRotZ -= offsetRotZ
        }

        // Önceki değerleri güncelle
        prevRotX = filteredRotX
        prevRotY = filteredRotY
        prevRotZ = filteredRotZ
        prevPosX = filteredPosX
        prevPosY = filteredPosY
        prevPosZ = filteredPosZ

        // Yer çekimi etkisi altında pozisyon güncelleme
        if (gravityY != 0f) {
            // Yerçekimi etkisi altında yavaşça yere çök
            velocity.y += (gravityY - 9.8f) * deltaTime * 0.1f
        } else {
            // Yerçekimi sensörü yoksa, varsayılan davranış
            if (filteredPosY > FLOOR_Y) {
                velocity.y -= GRAVITY_ACCEL * deltaTime * 0.1f
            }
        }

        // Hızı kullanarak pozisyonu güncelle
        filteredPosY += velocity.y * deltaTime

        // Zemin kontrolü
        if (filteredPosY < FLOOR_Y) {
            filteredPosY = FLOOR_Y
            velocity.y = 0f
        }

        // Yatay hareket (X ve Z) - lineer ivme verilerini kullan
        if (abs(linAccX) > 0.2f) {  // Küçük değerleri yok say (gürültü azaltmak için)
            velocity.x += linAccX * deltaTime * 0.1f
        }

        if (abs(linAccZ) > 0.2f) {  // Küçük değerleri yok say (gürültü azaltmak için)
            velocity.z += linAccZ * deltaTime * 0.1f
        }

        // Hızı kullanarak yatay pozisyonu güncelle
        filteredPosX += velocity.x * deltaTime
        filteredPosZ += velocity.z * deltaTime

        // Sürtünme etkisi - hız sönümlemesi
        velocity.x *= DAMPING
        velocity.y *= DAMPING
        velocity.z *= DAMPING

        // Fizik sınırları uygula - çok yüksek hızları önle
        val MAX_VELOCITY = 8f
        velocity.x = max(-MAX_VELOCITY, min(MAX_VELOCITY, velocity.x))
        velocity.y = max(-MAX_VELOCITY, min(MAX_VELOCITY, velocity.y))
        velocity.z = max(-MAX_VELOCITY, min(MAX_VELOCITY, velocity.z))
    }

    /**
     * Modeli kalibre et - mevcut jiroskop değerlerini sıfır noktası olarak ayarla
     * Bu, cihaz düz tutulduğunda modelin de düz durmasını sağlar
     */
    fun calibrate() {
        // Mevcut rotasyon değerlerini offset olarak kaydet
        offsetRotX = filteredRotX
        offsetRotY = filteredRotY
        offsetRotZ = filteredRotZ
        isCalibrated = true

        Log.d("PhoneRenderer", "Kalibrasyon yapıldı: X=$offsetRotX, Y=$offsetRotY, Z=$offsetRotZ")
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
     * @param currentValue Mevcut sensör değeri
     * @param lastValue Son sensör değeri
     * @param alpha Filtre katsayısı (0-1 arası)
     * @return Filtrelenmiş değer
     */
    private fun complementaryFilterAngle(currentValue: Float, lastValue: Float, alpha: Float): Float {
        // Ani değişimleri sınırla
        val maxChange = 2.0f
        val change = currentValue - lastValue
        val limitedCurrent = if (abs(change) > maxChange) {
            lastValue + if (change > 0) maxChange else -maxChange
        } else {
            currentValue
        }

        // Tamamlayıcı filtre uygula
        return lastValue + alpha * (limitedCurrent - lastValue)
    }

    /**
     * Telefon pozisyonunu ve rotasyonunu güncelle
     * İyileştirilmiş sürüm: Jiroskop oryantasyonu ve pusula desteği ile
     */
    fun updatePhonePosition(
        x: Float, y: Float, z: Float,
        rx: Float, ry: Float, rz: Float,
        gravX: Float = 0f, gravY: Float = 0f, gravZ: Float = 0f,
        linearAccX: Float = 0f, linearAccY: Float = 0f, linearAccZ: Float = 0f,
        magneticX: Float = 0f, magneticY: Float = 0f, magneticZ: Float = 0f  // Pusula verisi
    ) {
        // Ham sensör değerlerini kaydet
        posX = x
        posY = y
        posZ = z

        // Jiroskop açı değerlerini kaydet
        rotX = rx
        rotY = ry
        rotZ = rz

        // Pusula değerlerini kaydet
        magX = magneticX
        magY = magneticY
        magZ = magneticZ

        // Yerçekimi ve lineer ivme değerlerini güncelle
        gravityX = gravX
        gravityY = gravY
        gravityZ = gravZ

        // Aşırı değişimleri sınırla - ani titreşimleri azaltır
        val maxAccelChange = 2.0f // m/s² - daha düşük değer daha az ani değişim sağlar
        linAccX = clampChange(linearAccX, linAccX, maxAccelChange)
        linAccY = clampChange(linearAccY, linAccY, maxAccelChange)
        linAccZ = clampChange(linearAccZ, linAccZ, maxAccelChange)

        // İlk çağrıda modeli kalibre et
        if (!isCalibrated && rx != 0f && ry != 0f && rz != 0f) {
            calibrate()
        }
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
     * 3D vektör veri sınıfı
     */
    data class Vector3(var x: Float, var y: Float, var z: Float)

    /**
     * Telefon modeli sınıfı - telefonun 3D görüntüsünü oluşturur
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

        // Renkler - iyileştirilmiş renkler
        private val colors = floatArrayOf(
            // Gövde rengi - koyu gri (6 yüzey, her yüzey 4 köşe)
            0.3f, 0.3f, 0.32f, 1.0f,  // 0: sol alt ön - biraz daha açık
            0.3f, 0.3f, 0.32f, 1.0f,  // 1: sağ alt ön
            0.3f, 0.3f, 0.32f, 1.0f,  // 2: sağ üst ön
            0.3f, 0.3f, 0.32f, 1.0f,  // 3: sol üst ön

            0.15f, 0.15f, 0.17f, 1.0f,  // 4: sol alt arka - daha koyu
            0.15f, 0.15f, 0.17f, 1.0f,  // 5: sağ alt arka
            0.15f, 0.15f, 0.17f, 1.0f,  // 6: sağ üst arka
            0.15f, 0.15f, 0.17f, 1.0f,  // 7: sol üst arka

            // Ekran rengi - parlak mavi (4 köşe) - daha parlak
            0.0f, 0.6f, 0.9f, 1.0f,  // 8: sol alt ekran
            0.0f, 0.6f, 0.9f, 1.0f,  // 9: sağ alt ekran
            0.0f, 0.6f, 0.9f, 1.0f,  // 10: sağ üst ekran
            0.0f, 0.6f, 0.9f, 1.0f   // 11: sol üst ekran
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
            -12f, 0f, -12f,  // 0: sol arka
            12f, 0f, -12f,  // 1: sağ arka
            12f, 0f,  12f,  // 2: sağ ön
            -12f, 0f,  12f   // 3: sol ön
        )

        // Yüzey indeksleri
        private val indices = shortArrayOf(
            0, 1, 2, 0, 2, 3  // Üst yüz
        )

        // Renkler - daha koyu zemin rengi
        private val colors = floatArrayOf(
            0.2f, 0.2f, 0.25f, 1.0f,  // 0: sol arka
            0.2f, 0.2f, 0.25f, 1.0f,  // 1: sağ arka
            0.2f, 0.2f, 0.25f, 1.0f,  // 2: sağ ön
            0.2f, 0.2f, 0.25f, 1.0f   // 3: sol ön
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

    /**
     * Izgara sınıfı - 3D dünyada ızgara çizer
     * Hareket yönünü daha iyi görmek için eklendi
     */
    inner class Grid(private val program: Int) {
        // Izgara çizgileri
        private val lines = mutableListOf<Float>()
        private val colors = mutableListOf<Float>()

        // Izgara boyutları ve rengi
        private val gridSize = 12f
        private val gridStep = 1f
        private val gridColor = floatArrayOf(0.4f, 0.4f, 0.45f, 0.6f) // Yarı şeffaf gri

        // OpenGL buffer'ları
        private val vertexBuffer: FloatBuffer
        private val colorBuffer: FloatBuffer

        // OpenGL attributes
        private var positionHandle = 0
        private var colorHandle = 0
        private var mvpMatrixHandle = 0

        init {
            // Izgara çizgilerini oluştur
            // X yönündeki çizgiler
            for (i in -gridSize.toInt()..gridSize.toInt() step gridStep.toInt()) {
                // Çizgi başlangıç
                lines.add(i.toFloat())
                lines.add(0f)
                lines.add(-gridSize)

                // Çizgi rengi
                colors.addAll(gridColor.toList())

                // Çizgi bitiş
                lines.add(i.toFloat())
                lines.add(0f)
                lines.add(gridSize)

                // Çizgi rengi
                colors.addAll(gridColor.toList())
            }

            // Z yönündeki çizgiler
            for (i in -gridSize.toInt()..gridSize.toInt() step gridStep.toInt()) {
                // Çizgi başlangıç
                lines.add(-gridSize)
                lines.add(0f)
                lines.add(i.toFloat())

                // Çizgi rengi
                colors.addAll(gridColor.toList())

                // Çizgi bitiş
                lines.add(gridSize)
                lines.add(0f)
                lines.add(i.toFloat())

                // Çizgi rengi
                colors.addAll(gridColor.toList())
            }

            // Ana eksenleri vurgula (X ve Z)
            // X ekseni (kırmızı)
            lines.add(-gridSize)
            lines.add(0f)
            lines.add(0f)
            colors.add(1f) // Kırmızı
            colors.add(0f)
            colors.add(0f)
            colors.add(1f)

            lines.add(gridSize)
            lines.add(0f)
            lines.add(0f)
            colors.add(1f) // Kırmızı
            colors.add(0f)
            colors.add(0f)
            colors.add(1f)

            // Z ekseni (mavi)
            lines.add(0f)
            lines.add(0f)
            lines.add(-gridSize)
            colors.add(0f)
            colors.add(0f)
            colors.add(1f) // Mavi
            colors.add(1f)

            lines.add(0f)
            lines.add(0f)
            lines.add(gridSize)
            colors.add(0f)
            colors.add(0f)
            colors.add(1f) // Mavi
            colors.add(1f)

            // Vertex buffer oluştur
            val linesArray = lines.toFloatArray()
            vertexBuffer = ByteBuffer.allocateDirect(linesArray.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(linesArray)
                    position(0)
                }

            // Renk buffer oluştur
            val colorsArray = colors.toFloatArray()
            colorBuffer = ByteBuffer.allocateDirect(colorsArray.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(colorsArray)
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
                3,  // 3 float per vertex
                GLES20.GL_FLOAT,
                false,
                0,
                vertexBuffer
            )

            // Color attribute'unu etkinleştir
            GLES20.glEnableVertexAttribArray(colorHandle)
            GLES20.glVertexAttribPointer(
                colorHandle,
                4,  // 4 float per color
                GLES20.GL_FLOAT,
                false,
                0,
                colorBuffer
            )

            // MVP matrisini ayarla
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

            // Izgarayı çiz (çizgiler)
            GLES20.glLineWidth(1.5f) // Çizgi kalınlığı
            GLES20.glDrawArrays(GLES20.GL_LINES, 0, lines.size / 3)

            // Attribute'ları devre dışı bırak
            GLES20.glDisableVertexAttribArray(positionHandle)
            GLES20.glDisableVertexAttribArray(colorHandle)
        }
    }
}