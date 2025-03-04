package com.example.myapplication

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CustomMarker @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : MarkerView(context, attrs, defStyleAttr) {

    private val tvContent: TextView = findViewById(R.id.tvContent)
    private val dateFormat = SimpleDateFormat("ss.SSS", Locale.getDefault())

    init {
        // Layout resource ID'sini belirtin
        layoutResource = R.layout.marker_view
    }

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        e?.let {
            val timestamp = (it.x * 1000).toLong()
            val value = context.getString(
                R.string.marker_info,
                dateFormat.format(Date(timestamp)),
                "%.2f".format(it.y)
            )
            tvContent.text = value
        }
        super.refreshContent(e, highlight)
    }

    override fun getXOffset(xpos: Float) = -width / 2
    override fun getYOffset(ypos: Float) = -height
}