package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class SensorDataAdapter(private val sensorDataList: List<SensorData>) :
    RecyclerView.Adapter<SensorDataAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sensor_data, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val sensorData = sensorDataList[position]
        val timestamp = SimpleDateFormat("HH:mm:ss:SSS", Locale.getDefault()).format(Date(sensorData.timestamp))
        holder.timestampTextView.text = timestamp
        holder.xTextView.text = "X: ${sensorData.x}"
        holder.yTextView.text = "Y: ${sensorData.y}"
        holder.zTextView.text = "Z: ${sensorData.z}"
    }

    override fun getItemCount(): Int {
        return sensorDataList.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val timestampTextView: TextView = itemView.findViewById(R.id.timestampTextView)
        val xTextView: TextView = itemView.findViewById(R.id.xTextView)
        val yTextView: TextView = itemView.findViewById(R.id.yTextView)
        val zTextView: TextView = itemView.findViewById(R.id.zTextView)
    }
}
