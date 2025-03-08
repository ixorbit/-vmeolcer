package com.example.myapplication

import kotlin.math.pow
import kotlin.math.sqrt

class AccelerometerAnalytics {

    companion object {
        // Temel istatistik hesaplama fonksiyonları
        fun calculateMean(dataList: List<SensorData>, axis: String): Float {
            if (dataList.isEmpty()) return 0f

            val sum = when (axis) {
                "x" -> dataList.sumOf { it.x.toDouble() }
                "y" -> dataList.sumOf { it.y.toDouble() }
                "z" -> dataList.sumOf { it.z.toDouble() }
                else -> 0.0
            }

            return (sum / dataList.size).toFloat()
        }

        fun calculateStandardDeviation(dataList: List<SensorData>, axis: String): Float {
            if (dataList.isEmpty() || dataList.size == 1) return 0f

            val mean = calculateMean(dataList, axis)

            val sumOfSquares = when (axis) {
                "x" -> dataList.sumOf { (it.x - mean).pow(2).toDouble() }
                "y" -> dataList.sumOf { (it.y - mean).pow(2).toDouble() }
                "z" -> dataList.sumOf { (it.z - mean).pow(2).toDouble() }
                else -> 0.0
            }

            return sqrt(sumOfSquares / (dataList.size - 1)).toFloat()
        }

        fun findMinMaxValues(dataList: List<SensorData>): Map<String, Pair<Float, Float>> {
            if (dataList.isEmpty()) return emptyMap()

            val result = mutableMapOf<String, Pair<Float, Float>>()

            val minX = dataList.minOfOrNull { it.x } ?: 0f
            val maxX = dataList.maxOfOrNull { it.x } ?: 0f
            result["x"] = Pair(minX, maxX)

            val minY = dataList.minOfOrNull { it.y } ?: 0f
            val maxY = dataList.maxOfOrNull { it.y } ?: 0f
            result["y"] = Pair(minY, maxY)

            val minZ = dataList.minOfOrNull { it.z } ?: 0f
            val maxZ = dataList.maxOfOrNull { it.z } ?: 0f
            result["z"] = Pair(minZ, maxZ)

            return result
        }

        // Toplam ivme (vektörün büyüklüğü) hesaplama
        fun calculateMagnitude(x: Float, y: Float, z: Float): Float {
            return sqrt(x.pow(2) + y.pow(2) + z.pow(2))
        }

        // Zaman aralığına göre verileri gruplama
        fun groupDataByTimeInterval(dataList: List<SensorData>, intervalMillis: Long): Map<Long, List<SensorData>> {
            if (dataList.isEmpty()) return emptyMap()

            val result = mutableMapOf<Long, MutableList<SensorData>>()
            val startTime = dataList.first().timestamp

            for (data in dataList) {
                val timeGroup = ((data.timestamp - startTime) / intervalMillis) * intervalMillis + startTime
                if (!result.containsKey(timeGroup)) {
                    result[timeGroup] = mutableListOf()
                }
                result[timeGroup]?.add(data)
            }

            return result
        }
    }
}