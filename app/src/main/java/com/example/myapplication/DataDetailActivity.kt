package com.example.myapplication

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DataDetailActivity : AppCompatActivity(), OnChartValueSelectedListener {

    private lateinit var chart: LineChart
    private lateinit var sensorDataList: List<SensorData>
    private val dateFormat = SimpleDateFormat("ss.SSS", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_detail)

        chart = findViewById(R.id.graph)
        val fileName = intent.getStringExtra("FILE_NAME") ?: run {
            showError(getString(R.string.file_not_found))
            finish()
            return
        }

        sensorDataList = readSensorDataFromFile(fileName)
        if (sensorDataList.isEmpty()) {
            showError(getString(R.string.invalid_data))
            finish()
            return
        }

        setupChart()
    }

    private fun setupChart() {
        val entries = sensorDataList.mapIndexed { _, data ->
            Entry(
                (data.timestamp - sensorDataList.first().timestamp).toFloat() / 1000f,
                data.x // X ekseni için X değeri, isterseniz data.y/data.z kullanabilirsiniz
            )
        }

        val dataSet = LineDataSet(entries, getString(R.string.acceleration)).apply {
            color = Color.RED
            lineWidth = 2f
            setDrawCircles(true)
            circleRadius = 4f
            setDrawValues(false)
        }

        with(chart) {
            this.data = LineData(dataSet)
            description.isEnabled = false
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float) =
                        "${value.toInt()}s"
                }
            }
            axisLeft.apply {
                axisMinimum = -20f
                axisMaximum = 20f
                granularity = 5f
            }
            legend.apply {
                verticalAlignment = Legend.LegendVerticalAlignment.TOP
                horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
            }
            setOnChartValueSelectedListener(this@DataDetailActivity)
            animateXY(1000, 1000)
            invalidate()
        }
    }

    override fun onValueSelected(e: Entry?, h: Highlight?) {
        e?.let {
            val baseTime = sensorDataList.first().timestamp
            val timestamp = baseTime + (it.x * 1000).toLong()
            val valueText = getString(
                R.string.marker_info,
                dateFormat.format(Date(timestamp)),
                "%.2f".format(it.y)
            )
            Toast.makeText(this, valueText, Toast.LENGTH_SHORT).show()
        }
    }
    override fun onNothingSelected() {}

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Log.e("DataDetailActivity", message)
    }
}