package com.example.myapplication

import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

data class AccelerationAnomaly(
    val timestamp: Long,
    val magnitude: Float,
    val description: String,
    val type: AnomalyType,
    val duration: Long = 0,            // Serbest düşüş süresi (ms)
    val impactMagnitude: Float = 0f,   // Çarpma şiddeti (varsa)
    val estimatedHeight: Float = 0f    // Tahmini düşüş yüksekliği (metre)
) {
    enum class AnomalyType {
        SUDDEN_ACCELERATION,
        SUDDEN_DECELERATION,
        HIGH_VIBRATION,
        FREE_FALL,
        IMPACT,                        // Düşüş sonrası çarpma
        CUSTOM
    }

    fun getFormattedTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    // Düşüş yüksekliğini tahmin eder (h = 1/2 * g * t²)
    fun calculateEstimatedHeight(duration: Long): Float {
        val seconds = duration / 1000f
        // Serbest düşme formülü: h = 1/2 * g * t², g = 9.8 m/s²
        return 0.5f * 9.8f * seconds * seconds
    }
}