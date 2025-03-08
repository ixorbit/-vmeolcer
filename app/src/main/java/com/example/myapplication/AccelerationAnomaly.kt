package com.example.myapplication

import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

data class AccelerationAnomaly(
    val timestamp: Long,
    val magnitude: Float,
    val description: String,
    val type: AnomalyType
) {
    enum class AnomalyType {
        SUDDEN_ACCELERATION,
        SUDDEN_DECELERATION,
        HIGH_VIBRATION,
        FREE_FALL,
        CUSTOM
    }

    fun getFormattedTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}