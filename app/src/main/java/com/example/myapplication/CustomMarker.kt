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
        // İşaretçinin arka plan rengini ve kenarlığını değiştirebilirsinizbackground = resources.getDrawable(R.drawable.marker_background)
        // Yazı tipi ve boyutunu değiştirebilirsiniz
        tvContent.setTextColor(Color.WHITE)
        tvContent.textSize = 12f
    }

    override fun getOffset(): MPPointF {
        if (mOffset == null) {
            // İşaretçiyi yatay olarak ortala ve girişe yukarıya yerleştir
            mOffset = MPPointF((-(width / 2)).toFloat(), (-height).toFloat())
        }
        return mOffset!!
    }

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        e?.let { entry ->
            val timestamp = (entry.x * 1000).toLong()
            val formattedTimestamp = dateFormat.format(Date(timestamp))
            val xValue = highlight?.x ?: 0f // X değerini al

            // SensorData listesinden ilgili veriyi bul
            val sensorData = sensorDataList.find { it.timestamp.toFloat() == xValue }

            // İşaretçide gösterilecek metni oluştur
            val markerText = """
                Zaman: $formattedTimestamp
                X: ${"%.2f".format(sensorData?.x ?: 0f)} m/s²
                Y: ${"%.2f".format(sensorData?.y ?: 0f)} m/s²
                Z: ${"%.2f".format(sensorData?.z ?: 0f)} m/s²
            """.trimIndent()

            val spannableString = SpannableString(markerText)

            // "Zaman:" kelimesini kalın yap
            val timeIndex = markerText.indexOf("Zaman:")
            spannableString.setSpan(StyleSpan(Typeface.BOLD), timeIndex, timeIndex + 6, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            // "X:", "Y:", "Z:" kelimelerini kalın yap
            val xIndex = markerText.indexOf("X:")
            spannableString.setSpan(StyleSpan(Typeface.BOLD), xIndex, xIndex + 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            val yIndex = markerText.indexOf("Y:")
            spannableString.setSpan(StyleSpan(Typeface.BOLD), yIndex, yIndex + 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            val zIndex = markerText.indexOf("Z:")
            spannableString.setSpan(StyleSpan(Typeface.BOLD), zIndex, zIndex + 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            // "m/s²" kelimesini küçült
            val msIndex = markerText.indexOf("m/s²")
            spannableString.setSpan(RelativeSizeSpan(0.8f), msIndex, msIndex + 4, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannableString.setSpan(ForegroundColorSpan(Color.LTGRAY), msIndex, msIndex + 4, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            tvContent.text = spannableString
        }
        super.refreshContent(e, highlight)
    }
}