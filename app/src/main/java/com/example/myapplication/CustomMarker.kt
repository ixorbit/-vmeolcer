package com.example.myapplication

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.widget.TextView
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import java.text.SimpleDateFormat
import java.util.*

class CustomMarker(context: Context, private val sensorDataList: List<SensorData>) : MarkerView(context, R.layout.marker_view) {

    private val tvContent: TextView = findViewById(R.id.tvContent)
    private val dateFormat = SimpleDateFormat("mm:ss.SSS", Locale.getDefault())
    private var mOffset: MPPointF? = null

    companion object {
        const val TIME_FORMAT = "mm:ss.SSS"
    }

    init {
        // Set marker background color and border if needed
        tvContent.setTextColor(Color.WHITE)
        tvContent.textSize = 12f
    }

    override fun getOffset(): MPPointF {
        if (mOffset == null) {
            // Center marker horizontally and position above the entry
            mOffset = MPPointF((-(width / 2)).toFloat(), (-height).toFloat())
        }
        return mOffset!!
    }

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        if (e == null) {
            tvContent.text = "Veri yok"
            super.refreshContent(e, highlight)
            return
        }

        try {
            // Get X value (in seconds)
            val xValue = e.x

            // Safe check for empty data list
            if (sensorDataList.isEmpty()) {
                tvContent.text = "Veri bulunamadı"
                super.refreshContent(e, highlight)
                return
            }

            // Find first timestamp
            val firstTimestamp = sensorDataList.firstOrNull()?.timestamp ?: 0L

            // Convert seconds to milliseconds and add to first timestamp
            val timestamp = firstTimestamp + (xValue * 1000).toLong()
            val formattedTimestamp = try {
                dateFormat.format(Date(timestamp))
            } catch (ex: Exception) {
                "Bilinmeyen zaman"
            }

            // Find the nearest data point to the X value of the entry
            val sensorData = findNearestDataPoint(firstTimestamp, xValue)

            // Create marker text with sensor data
            val markerText = buildMarkerText(formattedTimestamp, sensorData)

            // Apply formatting to marker text
            val spannableString = formatMarkerText(markerText)

            tvContent.text = spannableString
        } catch (e: Exception) {
            tvContent.text = "Hata: ${e.message}"
        }

        super.refreshContent(e, highlight)
    }

    private fun findNearestDataPoint(firstTimestamp: Long, xValue: Float): SensorData? {
        return try {
            // Target timestamp in milliseconds
            val targetTimestamp = firstTimestamp + (xValue * 1000).toLong()

            // Find nearest data point safely
            sensorDataList.minByOrNull {
                kotlin.math.abs(it.timestamp - targetTimestamp)
            }
        } catch (ex: Exception) {
            null
        }
    }

    private fun buildMarkerText(formattedTimestamp: String, sensorData: SensorData?): String {
        return """
            Zaman: $formattedTimestamp
            X: ${"%.2f".format(sensorData?.x ?: 0f)} m/s²
            Y: ${"%.2f".format(sensorData?.y ?: 0f)} m/s²
            Z: ${"%.2f".format(sensorData?.z ?: 0f)} m/s²
        """.trimIndent()
    }

    private fun formatMarkerText(markerText: String): SpannableString {
        val spannableString = SpannableString(markerText)

        // Bold "Zaman:" label
        val timeIndex = markerText.indexOf("Zaman:")
        if (timeIndex >= 0) {
            spannableString.setSpan(StyleSpan(Typeface.BOLD), timeIndex, timeIndex + 6, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        // Bold axis labels
        boldAxisLabel(spannableString, markerText, "X:")
        boldAxisLabel(spannableString, markerText, "Y:")
        boldAxisLabel(spannableString, markerText, "Z:")

        // Reduce size of unit text
        val msIndex = markerText.indexOf("m/s²")
        if (msIndex >= 0) {
            spannableString.setSpan(RelativeSizeSpan(0.8f), msIndex, msIndex + 4, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannableString.setSpan(ForegroundColorSpan(Color.LTGRAY), msIndex, msIndex + 4, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        return spannableString
    }

    private fun boldAxisLabel(spannable: SpannableString, text: String, label: String) {
        val index = text.indexOf(label)
        if (index >= 0) {
            spannable.setSpan(StyleSpan(Typeface.BOLD), index, index + label.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}