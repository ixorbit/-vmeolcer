package com.example.myapplication

import kotlin.math.sqrt

/**
 * Serbest düşüş analizleri için yardımcı sınıf
 */
class FreeFallAnalysis {
    companion object {
        // Yerçekimi sabiti (m/s²)
        private const val GRAVITY = 9.81f

        /**
         * Serbest düşüş süresine göre düşüş yüksekliğini hesaplar
         * @param durationMs Düşüş süresi, milisaniye cinsinden
         * @return Tahmini düşüş yüksekliği, metre cinsinden
         */
        fun calculateFallHeight(durationMs: Long): Float {
            val seconds = durationMs / 1000f
            // h = 1/2 * g * t²
            return 0.5f * GRAVITY * seconds * seconds
        }

        /**
         * Düşüş yüksekliğine göre düşüş süresini hesaplar
         * @param heightMeters Düşüş yüksekliği, metre cinsinden
         * @return Tahmini düşüş süresi, milisaniye cinsinden
         */
        fun calculateFallDuration(heightMeters: Float): Long {
            // t = sqrt(2h/g)
            val seconds = sqrt((2 * heightMeters) / GRAVITY)
            return (seconds * 1000).toLong()
        }

        /**
         * Serbest düşüş öncesi ve sonrası ivme değerlerini analiz eder
         * @param freeFallEvent Serbest düşüş anomalisi
         * @param sensorDataList Tüm sensör verileri
         * @return Detaylı analiz raporu
         */
        fun analyzeFreeFall(freeFallEvent: AccelerationAnomaly, sensorDataList: List<SensorData>): FreeFallReport {
            // Serbest düşüş başlangıç indeksini bul
            val startIndex = sensorDataList.indexOfFirst { it.timestamp == freeFallEvent.timestamp }
            if (startIndex == -1) return FreeFallReport()

            // Düşüş öncesi ve sonrası veri için pencere boyutları
            val windowBefore = 20
            val windowAfter = 40

            // Düşüş öncesi verileri al (en fazla 20 veri noktası)
            val beforeStartIndex = maxOf(0, startIndex - windowBefore)
            val beforeFallData = sensorDataList.subList(beforeStartIndex, startIndex)

            // Düşüş sonrası verileri bul
            // Önce düşüş bitiş indeksini hesapla
            var endIndex = startIndex

            // Serbest düşüş bitişini bul: düşük ivme ardından yüksek ivme geldiği nokta
            for (i in (startIndex + 1) until minOf(sensorDataList.size, startIndex + 40)) {
                val magnitude = AccelerometerAnalytics.calculateMagnitude(
                    sensorDataList[i].x, sensorDataList[i].y, sensorDataList[i].z
                )

                if (magnitude > 12.0f) { // Çarpma eşiği
                    endIndex = i
                    break
                }
            }

            // Düşüş sonrası verileri al
            val afterEndIndex = minOf(sensorDataList.size, endIndex + windowAfter)
            val afterFallData = sensorDataList.subList(endIndex, afterEndIndex)

            // Düşüş süresi hesapla
            val duration = sensorDataList[endIndex].timestamp - sensorDataList[startIndex].timestamp

            // Düşüş sırasındaki verileri al
            val duringFallData = sensorDataList.subList(startIndex, endIndex + 1)

            // Düşüş öncesi ortalama ivme
            val beforeFallMagnitudes = beforeFallData.map {
                AccelerometerAnalytics.calculateMagnitude(it.x, it.y, it.z)
            }
            val avgBeforeFall = if (beforeFallMagnitudes.isNotEmpty())
                beforeFallMagnitudes.average().toFloat() else GRAVITY

            // Düşüş sırasındaki minimum ivme
            val duringFallMagnitudes = duringFallData.map {
                AccelerometerAnalytics.calculateMagnitude(it.x, it.y, it.z)
            }
            val minDuringFall = duringFallMagnitudes.minOrNull() ?: 0f

            // Çarpma anındaki maksimum ivme
            val afterFallMagnitudes = afterFallData.map {
                AccelerometerAnalytics.calculateMagnitude(it.x, it.y, it.z)
            }
            val maxAfterFall = afterFallMagnitudes.maxOrNull() ?: 0f

            // Tahmini düşüş yüksekliği
            val estimatedHeight = calculateFallHeight(duration)

            // Düşüş kalitesi (ne kadar serbest düşüş koşullarına uygun, 0-100 arası)
            val fallQuality = (100 * (1 - (minDuringFall / GRAVITY))).toInt().coerceIn(0, 100)

            return FreeFallReport(
                startTime = sensorDataList[startIndex].timestamp,
                endTime = sensorDataList[endIndex].timestamp,
                duration = duration,
                estimatedHeight = estimatedHeight,
                minAcceleration = minDuringFall,
                impactAcceleration = maxAfterFall,
                preFallAcceleration = avgBeforeFall,
                quality = fallQuality
            )
        }
    }

    /**
     * Serbest düşüş analiz raporu veri sınıfı
     */
    data class FreeFallReport(
        val startTime: Long = 0L,
        val endTime: Long = 0L,
        val duration: Long = 0L,
        val estimatedHeight: Float = 0f,
        val minAcceleration: Float = 0f,
        val impactAcceleration: Float = 0f,
        val preFallAcceleration: Float = 0f,
        val quality: Int = 0 // 0-100 arası kalite puanı
    )
}