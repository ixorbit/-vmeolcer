package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FreeFallDetailsAdapter(private val freeFallReports: List<FreeFallAnalysis.FreeFallReport>) :
    RecyclerView.Adapter<FreeFallDetailsAdapter.ViewHolder>() {

    private val dateFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val timeTextView: TextView = itemView.findViewById(R.id.tvFreeFallTime)
        val startTimeTextView: TextView = itemView.findViewById(R.id.tvFreeFallStartTime)
        val durationTextView: TextView = itemView.findViewById(R.id.tvFreeFallDuration)
        val heightTextView: TextView = itemView.findViewById(R.id.tvFreeFallHeight)
        val minAccelTextView: TextView = itemView.findViewById(R.id.tvFreeFallMinAccel)
        val impactAccelTextView: TextView = itemView.findViewById(R.id.tvFreeFallImpactAccel)
        val qualityTextView: TextView = itemView.findViewById(R.id.tvFreeFallQuality)
        val qualityProgressBar: ProgressBar = itemView.findViewById(R.id.progressFreeFallQuality)
        val iconImageView: ImageView = itemView.findViewById(R.id.ivFreeFallIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_free_fall_detail, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val report = freeFallReports[position]

        // Format the times
        holder.timeTextView.text = dateFormatter.format(Date(report.startTime))
        holder.startTimeTextView.text = dateFormatter.format(Date(report.startTime))

        // Duration
        holder.durationTextView.text = "${report.duration} ms"

        // Height
        holder.heightTextView.text = "${String.format("%.2f", report.estimatedHeight)} metre"

        // Acceleration values
        holder.minAccelTextView.text = "${String.format("%.2f", report.minAcceleration)} m/s²"
        holder.impactAccelTextView.text = "${String.format("%.2f", report.impactAcceleration)} m/s²"

        // Quality
        holder.qualityTextView.text = "%${report.quality}"
        holder.qualityProgressBar.progress = report.quality
    }

    override fun getItemCount(): Int = freeFallReports.size
}