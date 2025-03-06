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
        // İşaretçinin arka plan rengini ve kenarlığını değiştirebilirsiniz
        // background = resources.getDrawable(R.drawable.marker_background)
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
        try {
            e?.let { entry ->
                // X değerini al (saniye cinsinden)
                val xValue = entry.x

                // Eğer sensorDataList boşsa veya e.x değeri geçersizse güvenli bir varsayılan metin göster
                if (sensorDataList.isEmpty()) {
                    tvContent.text = "Veri bulunamadı"
                    return
                }

                // İlk zaman damgasını bul
                val firstTimestamp = sensorDataList.firstOrNull()?.timestamp ?: 0L

                // Saniyeyi milisaniyeye çevir ve ilk zaman damgasını ekle
                val timestamp = firstTimestamp + (xValue * 1000).toLong()
                val formattedTimestamp = try {
                    dateFormat.format(Date(timestamp))
                } catch (ex: Exception) {
                    "Bilinmeyen zaman"
                }

                // Entry'nin X değerine en yakın veri noktasını bul
                val sensorData = try {
                    // Grafikte zaman saniye cinsinden, ama timestamp milisaniye cinsinden
                    val targetTimestamp = firstTimestamp + (xValue * 1000).toLong()

                    // En yakın veri noktasını bul
                    sensorDataList.minByOrNull {
                        Math.abs(it.timestamp - targetTimestamp)
                    } ?: sensorDataList.firstOrNull()
                } catch (ex: Exception) {
                    null
                }

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
                if (timeIndex >= 0) {
                    spannableString.setSpan(StyleSpan(Typeface.BOLD), timeIndex, timeIndex + 6, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }

                // "X:", "Y:", "Z:" kelimelerini kalın yap
                val xIndex = markerText.indexOf("X:")
                if (xIndex >= 0) {
                    spannableString.setSpan(StyleSpan(Typeface.BOLD), xIndex, xIndex + 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }

                val yIndex = markerText.indexOf("Y:")
                if (yIndex >= 0) {
                    spannableString.setSpan(StyleSpan(Typeface.BOLD), yIndex, yIndex + 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }

                val zIndex = markerText.indexOf("Z:")
                if (zIndex >= 0) {
                    spannableString.setSpan(StyleSpan(Typeface.BOLD), zIndex, zIndex + 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }

                // "m/s²" kelimesini küçült
                val msIndex = markerText.indexOf("m/s²")
                if (msIndex >= 0) {
                    spannableString.setSpan(RelativeSizeSpan(0.8f), msIndex, msIndex + 4, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spannableString.setSpan(ForegroundColorSpan(Color.LTGRAY), msIndex, msIndex + 4, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }

                tvContent.text = spannableString
            } ?: run {
                tvContent.text = "Veri yok"
            }
        } catch (e: Exception) {
            tvContent.text = "Hata: ${e.message}"
        }
        super.refreshContent(e, highlight)
    }
}