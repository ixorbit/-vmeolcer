package com.example.myapplication


import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_VERSION = 1
        const val DATABASE_NAME = "SensorData.db"
        const val TABLE_NAME = "sensor_data"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_X = "x"
        const val COLUMN_Y = "y"
        const val COLUMN_Z = "z"
    }


    override fun onCreate(db: SQLiteDatabase) {
        val createTableSQL = ("CREATE TABLE $TABLE_NAME "
                + "($COLUMN_TIMESTAMP INTEGER, "
                + "$COLUMN_X REAL, "
                + "$COLUMN_Y REAL, "
                + "$COLUMN_Z REAL)")
        db.execSQL(createTableSQL)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun getAllSensorData(): List<SensorData> {
        val sensorDataList = mutableListOf<SensorData>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_NAME", null)
        while (cursor.moveToNext()) {
            val timestamp = cursor.getLong(cursor.getColumnIndex(COLUMN_TIMESTAMP))
            val x = cursor.getFloat(cursor.getColumnIndex(COLUMN_X))
            val y = cursor.getFloat(cursor.getColumnIndex(COLUMN_Y))
            val z = cursor.getFloat(cursor.getColumnIndex(COLUMN_Z))
            val sensorData = SensorData(timestamp, x, y, z)
            sensorDataList.add(sensorData)
        }
        cursor.close()
        return sensorDataList
    }

    fun addSensorData(timestamp: Long, x: Float, y: Float, z: Float) {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(COLUMN_TIMESTAMP, timestamp)
        values.put(COLUMN_X, x)
        values.put(COLUMN_Y, y)
        values.put(COLUMN_Z, z)
        db.insert(TABLE_NAME, null, values)
        db.close()
    }

    fun clearAllData() {
        val db = this.writableDatabase
        db.delete(TABLE_NAME, null, null)
        db.close()
    }
}
