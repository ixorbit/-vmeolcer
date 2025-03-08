package com.example.myapplication

import kotlin.math.abs

class AnomalyDetector {

    companion object {
        // Varsayılan eşik değerleri
        private const val HIGH_ACCELERATION_THRESHOLD = 15.0f  // m/s²
        private const val SUDDEN_CHANGE_THRESHOLD = 10.0f      // m/s² (değişim miktarı)
        private const val VIBRATION_WINDOW = 500L              // 500 milisaniye
        private const val VIBRATION_THRESHOLD = 2.0f           // Standart sapma eşiği

        // Serbest düşüş için iyileştirilmiş değerler
        private const val FREE_FALL_THRESHOLD = 0.7f           // m/s² (serbest düşüş 0'a yakın olur)
        private const val FREE_FALL_MIN_DURATION = 150L        // Minimum 150ms sürmeli
        private const val FREE_FALL_MAX_DURATION = 1500L       // Maksimum 1.5s (gerçekçi bir üst limit)
        private const val FREE_FALL_IMPACT_THRESHOLD = 18.0f   // Düşüş sonrası çarpma eşiği

        fun detectAnomalies(sensorDataList: List<SensorData>,
                            customThresholds: Map<String, Float> = emptyMap()): List<AccelerationAnomaly> {
            if (sensorDataList.size < 2) return emptyList()

            val anomalies = mutableListOf<AccelerationAnomaly>()

            // Eşik değerlerini özelleştirmeye izin ver
            val highAccelThreshold = customThresholds["high_accel"] ?: HIGH_ACCELERATION_THRESHOLD
            val suddenChangeThreshold = customThresholds["sudden_change"] ?: SUDDEN_CHANGE_THRESHOLD
            val vibrationThreshold = customThresholds["vibration"] ?: VIBRATION_THRESHOLD
            val freeFallThreshold = customThresholds["free_fall"] ?: FREE_FALL_THRESHOLD
            val freeFallImpactThreshold = customThresholds["free_fall_impact"] ?: FREE_FALL_IMPACT_THRESHOLD

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

            // İyileştirilmiş Serbest Düşüş Tespiti
            detectFreeFall(sensorDataList, freeFallThreshold, freeFallImpactThreshold, anomalies)

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

        // Serbest düşüş tespiti için geliştirilmiş metod
        private fun detectFreeFall(
            sensorDataList: List<SensorData>,
            freeFallThreshold: Float,
            impactThreshold: Float,
            anomalies: MutableList<AccelerationAnomaly>
        ) {
            var inFreeFall = false
            var freeFallStartTime = 0L
            var freeFallStartIndex = 0

            // İvme büyüklük değerlerini önceden hesapla
            val magnitudes = sensorDataList.map {
                AccelerometerAnalytics.calculateMagnitude(it.x, it.y, it.z)
            }

            for (i in magnitudes.indices) {
                val magnitude = magnitudes[i]
                val timestamp = sensorDataList[i].timestamp

                // Serbest düşüş başlangıcı
                if (!inFreeFall && magnitude < freeFallThreshold) {
                    inFreeFall = true
                    freeFallStartTime = timestamp
                    freeFallStartIndex = i
                }
                // Serbest düşüş bitişi
                else if (inFreeFall && (magnitude > freeFallThreshold || i == magnitudes.size - 1)) {
                    val duration = timestamp - freeFallStartTime

                    // Minimum süre kontrolü
                    if (duration >= FREE_FALL_MIN_DURATION && duration <= FREE_FALL_MAX_DURATION) {
                        // Düşüş öncesi ortalama ivme (referans değer)
                        val preFreeFallStartIdx = maxOf(0, freeFallStartIndex - 5)
                        val preFreeFallMagnitudes = magnitudes.subList(preFreeFallStartIdx, freeFallStartIndex)
                        val preFreeFallAvg = if (preFreeFallMagnitudes.isNotEmpty())
                            preFreeFallMagnitudes.average() else 9.8

                        // Düşüş sonrası çarpma kontrolü (daha sonraki 10 değer içinde)
                        val postFreeFallEndIdx = minOf(magnitudes.size, i + 10)
                        val postFreeFallMagnitudes = magnitudes.subList(i, postFreeFallEndIdx)
                        val impactMagnitude = postFreeFallMagnitudes.maxOrNull() ?: 0f

                        // Çarpma etkisi kontrol
                        val hasImpact = impactMagnitude > impactThreshold

                        // Düşüş derinliği (ne kadar düşük ivme oldu)
                        val lowestMagnitude = magnitudes.subList(freeFallStartIndex, i).minOrNull() ?: 0f

                        // Serbest düşüş kalitesi (ne kadar sıfıra yaklaşıldı, 0-1 arası)
                        val freeFallQuality = 1 - (lowestMagnitude / freeFallThreshold)
                        val qualityPercent = (freeFallQuality * 100).toInt()

                        // Tahmini düşüş yüksekliği hesapla
                        val estimatedHeight = FreeFallAnalysis.calculateFallHeight(duration)

                        val description = if (hasImpact) {
                            "Serbest Düşüş + Çarpma: Süre: ${duration}ms, Yükseklik: ${"%.2f".format(estimatedHeight)}m, Kalite: %$qualityPercent"
                        } else {
                            "Serbest Düşüş: Süre: ${duration}ms, Yükseklik: ${"%.2f".format(estimatedHeight)}m, Kalite: %$qualityPercent"
                        }

                        anomalies.add(
                            AccelerationAnomaly(
                                timestamp = freeFallStartTime,
                                magnitude = lowestMagnitude,
                                description = description,
                                type = AccelerationAnomaly.AnomalyType.FREE_FALL,
                                duration = duration,
                                impactMagnitude = impactMagnitude,
                                estimatedHeight = estimatedHeight
                            )
                        )

                        // Çarpma etkisi için ayrı bir anomali
                        if (hasImpact) {
                            // Çarpma zamanını bul
                            val impactIndex = i + postFreeFallMagnitudes.indexOf(impactMagnitude)
                            if (impactIndex < sensorDataList.size) {
                                anomalies.add(
                                    AccelerationAnomaly(
                                        timestamp = sensorDataList[impactIndex].timestamp,
                                        magnitude = impactMagnitude,
                                        description = "Düşüş Sonrası Çarpma: ${"%.2f".format(impactMagnitude)} m/s²",
                                        type = AccelerationAnomaly.AnomalyType.IMPACT
                                    )
                                )
                            }
                        }
                    }

                    // Serbest düşüş durumunu sıfırla
                    inFreeFall = false
                }
            }
        }
    }
}