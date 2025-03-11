package com.example.myapplication

data class SensorData(
    val timestamp: Long,
    val x: Float,             // İvme X (accelerometer)
    val y: Float,             // İvme Y (accelerometer)
    val z: Float,             // İvme Z (accelerometer)
    val rotX: Float = 0f,     // Rotasyon X (pitch)
    val rotY: Float = 0f,     // Rotasyon Y (roll)
    val rotZ: Float = 0f,     // Rotasyon Z (yaw/azimuth)
    val gyroX: Float = 0f,    // Jiroskop X
    val gyroY: Float = 0f,    // Jiroskop Y
    val gyroZ: Float = 0f,    // Jiroskop Z
    val linAccX: Float = 0f,  // Lineer İvme X (yerçekimi etkisi olmadan)
    val linAccY: Float = 0f,  // Lineer İvme Y
    val linAccZ: Float = 0f,  // Lineer İvme Z
    val gravX: Float = 0f,    // Yerçekimi X
    val gravY: Float = 0f,    // Yerçekimi Y
    val gravZ: Float = 0f     // Yerçekimi Z
)