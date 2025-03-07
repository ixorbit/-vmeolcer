package com.example.myapplication

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

enum class SensorType {
    ACCELEROMETER,
    GYROSCOPE,
    MAGNETOMETER,
    LIGHT,
    PROXIMITY,
    PRESSURE,
    TEMPERATURE,
    OTHER
}

@Parcelize
data class SensorData(
    val id: Long = 0,
    val timestamp: Long,
    val x: Float,
    val y: Float,
    val z: Float,
    val sensorName: String = "Accelerometer",
    val sensorType: SensorType = SensorType.ACCELEROMETER,
    val description: String? = null
) : Parcelable {
    // Uyumlu bir şekilde mevcut kodla çalışması için values özelliği
    val values: FloatArray
        get() = floatArrayOf(x, y, z)

    // Eşitlik kontrolü için özel metotlar
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SensorData

        if (id != other.id) return false
        if (timestamp != other.timestamp) return false
        if (x != other.x) return false
        if (y != other.y) return false
        if (z != other.z) return false
        if (sensorName != other.sensorName) return false
        if (sensorType != other.sensorType) return false
        if (description != other.description) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + x.hashCode()
        result = 31 * result + y.hashCode()
        result = 31 * result + z.hashCode()
        result = 31 * result + sensorName.hashCode()
        result = 31 * result + sensorType.hashCode()
        result = 31 * result + (description?.hashCode() ?: 0)
        return result
    }
}