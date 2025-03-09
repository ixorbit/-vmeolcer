package com.example.myapplication

data class SensorData(
    val timestamp: Long,
    val x: Float,
    val y: Float,
    val z: Float,
    // Gyroscope verileri (rotasyon verileri)
    val rotX: Float = 0f,
    val rotY: Float = 0f,
    val rotZ: Float = 0f,
    // Açısal hız (gyroscope ham değerleri)
    val gyroX: Float = 0f,
    val gyroY: Float = 0f,
    val gyroZ: Float = 0f
)