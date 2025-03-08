package com.example.myapplication

import android.content.Context
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.utils.Utils
import java.text.SimpleDateFormat
import java.util.*

/**
 * Serbest düşüş grafiğini yöneten sınıf
 */
class FreeFallChartManager(
    private val context: Context,
    private val freeFallReport: FreeFallAnalysis.FreeFallReport,
    private val sensorDataList: List<SensorData>
) {
    // Renk şeması
    private val colorMagnitude = Color.rgb(119, 210, 255)    // Mavi - toplam ivme
    private val colorFreeFall = Color.rgb(138, 255, 138)     // Yeşil - serbest düşüş bölgesi
    private val colorImpact = Color.rgb(255, 89, 94)         // Kırmızı - çarpma anı
    private val colorBackground = Color.rgb(18, 18, 18)      // Koyu arka plan
    private val colorGrid = Color.rgb(50, 50, 50)            // Izgara rengi
    private val colorText = Color.rgb(200, 200, 200)         // Metin rengi

    // Serbest düşüş eşik değeri çizgisi
    private val freeFallThreshold = 0.7f

    // Yerçekimi
    private val gravity = 9.81f

    // View öğeleri
    private lateinit var rootView: View
    private lateinit var chart: LineChart
    private lateinit var titleTextView: TextView
    private lateinit var infoTextView: TextView

    /**
     * Serbest düşüş grafiğini oluşturur
     * @return Oluşturulan grafik kartı
     */
    fun createChartView(): View {
        // Layout'u inflate et
        val inflater = LayoutInflater.from(context)
        rootView = inflater.inflate(R.layout.layout_free_fall_chart, null)

        // View elemanlarını bul
        chart = rootView.findViewById(R.id.freeFallChart)
        titleTextView = rootView.findViewById(R.id.tvChartTitle)
        infoTextView = rootView.findViewById(R.id.tvFallInfo)

        // Bilgileri ayarla
        setupChartInfo()

        // Grafiği yapılandır
        setupChart()

        // Verileri ekle
        addDataToChart()

        return rootView
    }

    /**
     * Grafik başlığı ve bilgisini ayarlar
     */
    private fun setupChartInfo() {
        val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        val startTime = formatter.format(Date(freeFallReport.startTime))
        val duration = freeFallReport.duration
        val height = String.format("%.2f", freeFallReport.estimatedHeight)
        val quality = freeFallReport.quality

        titleTextView.text = "Serbest Düşüş İvme Profili"
        infoTextView.text = "Başlangıç: $startTime, Süre: ${duration}ms, Yükseklik: ${height}m, Kalite: %$quality"
    }

    /**
     * Grafiği yapılandırır
     */
    private fun setupChart() {
        chart.apply {
            // Grafiği dokunmatik ve etkileşimli yap
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)

            // Arka plan ve genel görünüm
            setBackgroundColor(colorBackground)
            description.isEnabled = false
            setDrawGridBackground(false)

            // Kenar boşlukları
            setExtraOffsets(8f, 16f, 8f, 8f)

            // Eksenler
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = colorText
                axisLineColor = colorText
                gridColor = colorGrid
                setDrawGridLines(true)
                labelRotationAngle = 0f
                setAvoidFirstLastClipping(true)

                // Zaman gösterimi için özel formatlayıcı
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return String.format("%.1f s", value)
                    }
                }
            }

            // Sol Y ekseni
            axisLeft.apply {
                textColor = colorText
                axisLineColor = colorText
                gridColor = colorGrid
                setDrawGridLines(true)

                // Serbest düşüş eşik çizgisi ekle
                val freeFallLine = LimitLine(freeFallThreshold, "Serbest Düşüş Eşiği")
                freeFallLine.lineColor = colorFreeFall
                freeFallLine.lineWidth = 1f
                freeFallLine.textColor = colorFreeFall
                freeFallLine.textSize = 10f
                freeFallLine.enableDashedLine(10f, 5f, 0f)
                addLimitLine(freeFallLine)

                // Yerçekimi çizgisi ekle
                val gravityLine = LimitLine(gravity, "Yerçekimi (9.81)")
                gravityLine.lineColor = Color.YELLOW
                gravityLine.lineWidth = 1f
                gravityLine.textColor = Color.YELLOW
                gravityLine.textSize = 10f
                gravityLine.enableDashedLine(10f, 5f, 0f)
                addLimitLine(gravityLine)
            }

            // Sağ Y ekseni devre dışı
            axisRight.isEnabled = false

            // Açıklama
            legend.apply {
                textColor = colorText
                form = com.github.mikephil.charting.components.Legend.LegendForm.LINE
                isEnabled = true
            }

            // Marker ekle - tıklama anını göstermek için
            val marker = CustomMarker(context, sensorDataList)
            setMarker(marker)
        }
    }

    /**
     * Grafik verilerini ekler
     */
    private fun addDataToChart() {
        // İlgili sensör verilerini bul
        val startIndex = sensorDataList.indexOfFirst { it.timestamp == freeFallReport.startTime }
        val endIndex = sensorDataList.indexOfFirst { it.timestamp == freeFallReport.endTime }

        if (startIndex == -1 || endIndex == -1) return

        // Grafikte gösterilecek aralığı belirle (düşüş öncesi ve sonrası dahil)
        val windowBefore = 30 // düşüşten 30 veri noktası önce
        val windowAfter = 30  // düşüşten 30 veri noktası sonra

        val dataStartIndex = maxOf(0, startIndex - windowBefore)
        val dataEndIndex = minOf(sensorDataList.size - 1, endIndex + windowAfter)

        // İlgili veri aralığını al
        val relevantData = sensorDataList.subList(dataStartIndex, dataEndIndex + 1)

        // Zaman değerlerini normalize et (grafik başlangıcı 0 sn)
        val baseTime = relevantData.first().timestamp

        // İvme büyüklük değerlerini hesapla
        val magnitudeEntries = relevantData.mapIndexed { index, data ->
            val timeInSeconds = (data.timestamp - baseTime) / 1000f
            val magnitude = AccelerometerAnalytics.calculateMagnitude(data.x, data.y, data.z)
            Entry(timeInSeconds, magnitude)
        }

        // Serbest düşüş bölgesi için veri seti
        val fallStartTime = (freeFallReport.startTime - baseTime) / 1000f
        val fallEndTime = (freeFallReport.endTime - baseTime) / 1000f

        // Büyüklük veri setini oluştur
        val magnitudeDataSet = LineDataSet(magnitudeEntries, "İvme Büyüklüğü").apply {
            color = colorMagnitude
            lineWidth = 2f
            setDrawCircles(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            cubicIntensity = 0.2f
            setDrawValues(false)
            setDrawFilled(true)
            fillColor = colorMagnitude
            fillAlpha = 80
            highLightColor = Color.WHITE
        }

        // Serbest düşüş bölgesi için dikey sınır çizgileri oluştur
        val startLine = LimitLine(fallStartTime, "Düşüş Başlangıcı")
        startLine.apply {
            lineWidth = 2f
            lineColor = colorFreeFall
            textColor = colorFreeFall
            labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
            textSize = 10f
        }

        val endLine = LimitLine(fallEndTime, "Düşüş Sonu")
        endLine.apply {
            lineWidth = 2f
            lineColor = colorImpact
            textColor = colorImpact
            labelPosition = LimitLine.LimitLabelPosition.RIGHT_BOTTOM
            textSize = 10f
        }

        // Çizgileri X eksenine ekle
        chart.xAxis.removeAllLimitLines()
        chart.xAxis.addLimitLine(startLine)
        chart.xAxis.addLimitLine(endLine)

        // Veri setlerini ekle
        val dataSets = ArrayList<ILineDataSet>()
        dataSets.add(magnitudeDataSet)

        // Veriyi grafiğe yükle
        val lineData = LineData(dataSets)
        chart.data = lineData

        // Y ekseni sınırlarını ayarla
        val minY = magnitudeEntries.minByOrNull { it.y }?.y ?: 0f
        val maxY = magnitudeEntries.maxByOrNull { it.y }?.y ?: 10f
        chart.axisLeft.apply {
            axisMinimum = maxOf(0f, minY - 2f) // en az 0
            axisMaximum = maxY + 2f // biraz boşluk bırak
        }

        // X ekseni sınırlarını ayarla
        chart.xAxis.apply {
            axisMinimum = magnitudeEntries.first().x - 0.1f
            axisMaximum = magnitudeEntries.last().x + 0.1f
        }

        // Grafiği güncelle
        chart.invalidate()
        chart.animateX(1000)
    }

    /**
     * Düşüş bölgesi için vurgulanan alan oluşturur
     */
    private fun createFallHighlightDataSet(
        data: List<Entry>,
        startTime: Float,
        endTime: Float,
        color: Int,
        label: String
    ): LineDataSet {
        // Düşüş bölgesindeki verileri filtrele
        val fallEntries = data.filter { it.x in startTime..endTime }

        return LineDataSet(fallEntries, label).apply {
            this.color = color
            lineWidth = 3f
            setDrawCircles(false)
            setDrawValues(false)
            highLightColor = Color.WHITE
            setDrawHighlightIndicators(false)
            setDrawFilled(true)
            fillColor = color
            fillAlpha = 120
            mode = LineDataSet.Mode.CUBIC_BEZIER
            cubicIntensity = 0.2f
        }
    }

    /**
     * Çarpma anını gösteren veri seti oluşturur
     */
    private fun createImpactIndicator(
        data: List<Entry>,
        impactTime: Float,
        color: Int,
        label: String
    ): LineDataSet {
        // Çarpma anına en yakın veri noktasını bul
        val impactEntry = data.minByOrNull { kotlin.math.abs(it.x - impactTime) } ?: return LineDataSet(
            listOf(), label
        )

        return LineDataSet(listOf(impactEntry), label).apply {
            this.color = color
            setDrawCircles(true)
            circleRadius = 5f
            circleColors = listOf(color)
            setDrawValues(false)
        }
    }
}