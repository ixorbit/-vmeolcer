package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class SensorDataAdapter(
    private val sensorDataList: List<SensorData>,
    private val onItemClick: ((SensorData) -> Unit)? = null
) : RecyclerView.Adapter<SensorDataAdapter.ViewHolder>() {

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Mevcut görünümler için referanslar
        val timestampTextView: TextView = itemView.findViewById(R.id.tvTimestamp)
        val xTextView: TextView = itemView.findViewById(R.id.tvSensorValue)
        val yTextView: TextView = itemView.findViewById(R.id.tvSensorValue)
        val zTextView: TextView = itemView.findViewById(R.id.tvSensorValue)

        // Yeni eklenen görünümler için referanslar (eğer layout dosyasını güncellediyseniz)
        val tvSensorName: TextView? = itemView.findViewById(R.id.tvSensorName)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick?.invoke(sensorDataList[position])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sensor_data, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val sensorData = sensorDataList[position]

        // Temel bilgileri görüntüle (önceki adaptörde olduğu gibi)
        holder.timestampTextView.text = "Timestamp: ${dateFormatter.format(Date(sensorData.timestamp))}"
        holder.xTextView.text = "X: ${"%.2f".format(sensorData.x)}"
        holder.yTextView.text = "Y: ${"%.2f".format(sensorData.y)}"
        holder.zTextView.text = "Z: ${"%.2f".format(sensorData.z)}"

        // Eğer yeni layout'u kullanıyorsanız sensör adını da göster

    }

    override fun getItemCount(): Int = sensorDataList.size
}