package com.example.myapplication

import kotlin.math.abs

class AnomalyDetector {

    companion object {
        // Varsayılan eşik değerleri
        private const val HIGH_ACCELERATION_THRESHOLD = 15.0f  // m/s²
        private const val SUDDEN_CHANGE_THRESHOLD = 10.0f      // m/s² (değişim miktarı)
        private const val VIBRATION_WINDOW = 500L              // 500 milisaniye
        private const val VIBRATION_THRESHOLD = 2.0f           // Standart sapma eşiği
        private const val FREE_FALL_THRESHOLD = 1.0f           // m/s² (serbest düşüş 0'a yakın olur)

        fun detectAnomalies(sensorDataList: List<SensorData>,
                            customThresholds: Map<String, Float> = emptyMap()): List<AccelerationAnomaly> {
            if (sensorDataList.size < 2) return emptyList()

            val anomalies = mutableListOf<AccelerationAnomaly>()

            // Eşik değerlerini özelleştirmeye izin ver
            val highAccelThreshold = customThresholds["high_accel"] ?: HIGH_ACCELERATION_THRESHOLD
            val suddenChangeThreshold = customThresholds["sudden_change"] ?: SUDDEN_CHANGE_THRESHOLD
            val vibrationThreshold = customThresholds["vibration"] ?: VIBRATION_THRESHOLD
            val freeFallThreshold = customThresholds["free_fall"] ?: FREE_FALL_THRESHOLD

            // Yüksek ivme tespiti
            for (data in sensorDataList) {
                val magnitude = AccelerometerAnalytics.calculateMagnitude(data.x, data.y, data.z)

                if (magnitude > highAccelThreshold) {
                    anomalies.add(AccelerationAnomaly(
                        timestamp = data.timestamp,
                        magnitude = magnitude,
                        description = "Yüksek İvme: ${"%.2f".format(magnitude)} m/s²",
                        type = AccelerationAnomaly.AnomalyType.SUDDEN_ACCELERATION
                    ))
                }

                if (magnitude < freeFallThreshold) {
                    anomalies.add(AccelerationAnomaly(
                        timestamp = data.timestamp,
                        magnitude = magnitude,
                        description = "Serbest Düşüş Algılandı: ${"%.2f".format(magnitude)} m/s²",
                        type = AccelerationAnomaly.AnomalyType.FREE_FALL
                    ))
                }
            }

            // Ani ivme değişimleri tespiti
            for (i in 1 until sensorDataList.size) {
                val prevData = sensorDataList[i-1]
                val currentData = sensorDataList[i]

                val prevMagnitude = AccelerometerAnalytics.calculateMagnitude(prevData.x, prevData.y, prevData.z)
                val currentMagnitude = AccelerometerAnalytics.calculateMagnitude(currentData.x, currentData.y, currentData.z)

                val change = abs(currentMagnitude - prevMagnitude)

                if (change > suddenChangeThreshold) {
                    val type = if (currentMagnitude > prevMagnitude)
                        AccelerationAnomaly.AnomalyType.SUDDEN_ACCELERATION
                    else
                        AccelerationAnomaly.AnomalyType.SUDDEN_DECELERATION

                    val description = if (type == AccelerationAnomaly.AnomalyType.SUDDEN_ACCELERATION)
                        "Ani Hızlanma: ${"%.2f".format(change)} m/s² artış"
                    else
                        "Ani Yavaşlama: ${"%.2f".format(change)} m/s² azalış"

                    anomalies.add(AccelerationAnomaly(
                        timestamp = currentData.timestamp,
                        magnitude = currentMagnitude,
                        description = description,
                        type = type
                    ))
                }
            }

            // Titreşim tespiti (zaman aralıklarına göre gruplama ve standart sapma analizi)
            val timeGroups = AccelerometerAnalytics.groupDataByTimeInterval(sensorDataList, VIBRATION_WINDOW)

            for ((timestamp, dataGroup) in timeGroups) {
                // En az birkaç veri noktası gerektir
                if (dataGroup.size < 5) continue

                // X, Y, Z için standart sapmaları hesapla
                val stdDevX = AccelerometerAnalytics.calculateStandardDeviation(dataGroup, "x")
                val stdDevY = AccelerometerAnalytics.calculateStandardDeviation(dataGroup, "y")
                val stdDevZ = AccelerometerAnalytics.calculateStandardDeviation(dataGroup, "z")

                // Toplam sapma
                val totalStdDev = stdDevX + stdDevY + stdDevZ

                if (totalStdDev > vibrationThreshold) {
                    anomalies.add(AccelerationAnomaly(
                        timestamp = timestamp,
                        magnitude = totalStdDev,
                        description = "Yüksek Titreşim Algılandı: ${"%.2f".format(totalStdDev)} σ",
                        type = AccelerationAnomaly.AnomalyType.HIGH_VIBRATION
                    ))
                }
            }

            return anomalies.sortedBy { it.timestamp }
        }
    }
}