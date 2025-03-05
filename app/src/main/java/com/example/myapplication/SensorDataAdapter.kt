package com.example.myapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SensorDataAdapter(private val sensorDataList: List<SensorData>) :
    RecyclerView.Adapter<SensorDataAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val timestampTextView: TextView = itemView.findViewById(R.id.timestampTextView)
        val xTextView: TextView = itemView.findViewById(R.id.xTextView)
        val yTextView: TextView = itemView.findViewById(R.id.yTextView)
        val zTextView: TextView = itemView.findViewById(R.id.zTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int):ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sensor_data, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val sensorData = sensorDataList[position]
        holder.timestampTextView.text = "Timestamp: ${sensorData.timestamp}"
        holder.xTextView.text = "X: ${"%.2f".format(sensorData.x)}"
        holder.yTextView.text = "Y: ${"%.2f".format(sensorData.y)}"
        holder.zTextView.text = "Z: ${"%.2f".format(sensorData.z)}"
    }

    override fun getItemCount(): Int = sensorDataList.size
}