package com.example.myapplication

import android.content.Context
import android.widget.TextView
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import java.text.SimpleDateFormat
import java.util.*

class CustomMarker(context: Context) : MarkerView(context, R.layout.marker_view) {

    private val tvContent: TextView = findViewById(R.id.tvContent)
    private val dateFormat = SimpleDateFormat("ss.SSS", Locale.getDefault())
    private var mOffset: MPPointF? = null

    // MPAndroidChart v3.1.0 için güncel metod imzaları
    override fun getOffset(): MPPointF {if (mOffset == null) {
        // center the marker horizontally and put it above the entry
        mOffset = MPPointF((-(width / 2)).toFloat(), (-height).toFloat())
    }
        return mOffset!!
    }

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        e?.let {
            val xValue = "X: ${"%.2f".format(highlight?.x)}"
            val yValue = "Y: ${"%.2f".format(e.y)}"
            tvContent.text = "$xValue\n$yValue"
            val timestamp = (it.x * 1000).toLong()
            tvContent.text = """
                Zaman: ${dateFormat.format(Date(timestamp))}
                Değer: ${"%.2f".format(it.y)} m/s²
            """.trimIndent()
        }
        super.refreshContent(e, highlight)
    }
}