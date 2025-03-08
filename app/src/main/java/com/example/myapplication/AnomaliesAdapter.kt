package com.example.myapplication

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AnomaliesAdapter(private val anomalies: List<AccelerationAnomaly>) :
    RecyclerView.Adapter<AnomaliesAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val timeTextView: TextView = itemView.findViewById(R.id.tvAnomalyTime)
        val descTextView: TextView = itemView.findViewById(R.id.tvAnomalyDesc)
        val iconImageView: ImageView = itemView.findViewById(R.id.ivAnomalyIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_anomaly, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val anomaly = anomalies[position]

        // Zaman formatını göster
        holder.timeTextView.text = anomaly.getFormattedTime()

        // Anomali açıklamasını göster
        holder.descTextView.text = anomaly.description

        // Anomali tipine göre ikon ve renk seç
        when (anomaly.type) {
            AccelerationAnomaly.AnomalyType.SUDDEN_ACCELERATION -> {
                holder.iconImageView.setImageResource(R.drawable.ic_acceleration)
                holder.iconImageView.setColorFilter(Color.rgb(255, 89, 94)) // Kırmızı
            }
            AccelerationAnomaly.AnomalyType.SUDDEN_DECELERATION -> {
                holder.iconImageView.setImageResource(R.drawable.ic_deceleration)
                holder.iconImageView.setColorFilter(Color.rgb(255, 180, 94)) // Turuncu
            }
            AccelerationAnomaly.AnomalyType.HIGH_VIBRATION -> {
                holder.iconImageView.setImageResource(R.drawable.ic_vibration)
                holder.iconImageView.setColorFilter(Color.rgb(255, 220, 90)) // Sarı
            }
            AccelerationAnomaly.AnomalyType.FREE_FALL -> {
                holder.iconImageView.setImageResource(R.drawable.ic_free_fall)
                holder.iconImageView.setColorFilter(Color.rgb(138, 255, 138)) // Yeşil
            }
            else -> {
                holder.iconImageView.setImageResource(R.drawable.ic_warning)
                holder.iconImageView.setColorFilter(Color.LTGRAY)
            }
        }
    }

    override fun getItemCount(): Int = anomalies.size
}