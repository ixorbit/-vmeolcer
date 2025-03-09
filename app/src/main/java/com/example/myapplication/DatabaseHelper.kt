package com.example.myapplication

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_VERSION = 2 // Veritabanı versiyonunu arttırdık
        const val DATABASE_NAME = "SensorData.db"
        const val TABLE_NAME = "sensor_data"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_X = "x"
        const val COLUMN_Y = "y"
        const val COLUMN_Z = "z"
        // Yeni sütunlar
        const val COLUMN_ROT_X = "rot_x"
        const val COLUMN_ROT_Y = "rot_y"
        const val COLUMN_ROT_Z = "rot_z"
        const val COLUMN_GYRO_X = "gyro_x"
        const val COLUMN_GYRO_Y = "gyro_y"
        const val COLUMN_GYRO_Z = "gyro_z"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableSQL = ("CREATE TABLE $TABLE_NAME "
                + "($COLUMN_TIMESTAMP INTEGER, "
                + "$COLUMN_X REAL, "
                + "$COLUMN_Y REAL, "
                + "$COLUMN_Z REAL, "
                + "$COLUMN_ROT_X REAL, "
                + "$COLUMN_ROT_Y REAL, "
                + "$COLUMN_ROT_Z REAL, "
                + "$COLUMN_GYRO_X REAL, "
                + "$COLUMN_GYRO_Y REAL, "
                + "$COLUMN_GYRO_Z REAL)")
        db.execSQL(createTableSQL)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Eski sürümden yeni sürüme geçiş: Yeni sütunları ekleyin
            try {
                db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_ROT_X REAL DEFAULT 0")
                db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_ROT_Y REAL DEFAULT 0")
                db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_ROT_Z REAL DEFAULT 0")
                db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_GYRO_X REAL DEFAULT 0")
                db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_GYRO_Y REAL DEFAULT 0")
                db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_GYRO_Z REAL DEFAULT 0")
            } catch (e: Exception) {
                // Sütunlar zaten eklenmiş olabilir, hatayı yok sayın
            }
        }
    }

    fun getAllSensorData(): List<SensorData> {
        val sensorDataList = mutableListOf<SensorData>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_NAME", null)

        if (cursor.moveToFirst()) {
            val timestampIndex = cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)
            val xIndex = cursor.getColumnIndexOrThrow(COLUMN_X)
            val yIndex = cursor.getColumnIndexOrThrow(COLUMN_Y)
            val zIndex = cursor.getColumnIndexOrThrow(COLUMN_Z)

            // Yeni sütunlar için indeksleri al
            val rotXIndex = cursor.getColumnIndex(COLUMN_ROT_X)
            val rotYIndex = cursor.getColumnIndex(COLUMN_ROT_Y)
            val rotZIndex = cursor.getColumnIndex(COLUMN_ROT_Z)
            val gyroXIndex = cursor.getColumnIndex(COLUMN_GYRO_X)
            val gyroYIndex = cursor.getColumnIndex(COLUMN_GYRO_Y)
            val gyroZIndex = cursor.getColumnIndex(COLUMN_GYRO_Z)

            do {
                val timestamp = cursor.getLong(timestampIndex)
                val x = cursor.getFloat(xIndex)
                val y = cursor.getFloat(yIndex)
                val z = cursor.getFloat(zIndex)

                // Yeni sütunları oku (yoksa varsayılan değer 0f)
                val rotX = if (rotXIndex >= 0) cursor.getFloat(rotXIndex) else 0f
                val rotY = if (rotYIndex >= 0) cursor.getFloat(rotYIndex) else 0f
                val rotZ = if (rotZIndex >= 0) cursor.getFloat(rotZIndex) else 0f
                val gyroX = if (gyroXIndex >= 0) cursor.getFloat(gyroXIndex) else 0f
                val gyroY = if (gyroYIndex >= 0) cursor.getFloat(gyroYIndex) else 0f
                val gyroZ = if (gyroZIndex >= 0) cursor.getFloat(gyroZIndex) else 0f

                val sensorData = SensorData(
                    timestamp, x, y, z,
                    rotX, rotY, rotZ,
                    gyroX, gyroY, gyroZ
                )
                sensorDataList.add(sensorData)
            } while (cursor.moveToNext())
        }

        cursor.close()
        return sensorDataList
    }

    fun addSensorData(
        timestamp: Long,
        x: Float, y: Float, z: Float,
        rotX: Float = 0f, rotY: Float = 0f, rotZ: Float = 0f,
        gyroX: Float = 0f, gyroY: Float = 0f, gyroZ: Float = 0f
    ) {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(COLUMN_TIMESTAMP, timestamp)
        values.put(COLUMN_X, x)
        values.put(COLUMN_Y, y)
        values.put(COLUMN_Z, z)
        values.put(COLUMN_ROT_X, rotX)
        values.put(COLUMN_ROT_Y, rotY)
        values.put(COLUMN_ROT_Z, rotZ)
        values.put(COLUMN_GYRO_X, gyroX)
        values.put(COLUMN_GYRO_Y, gyroY)
        values.put(COLUMN_GYRO_Z, gyroZ)
        db.insert(TABLE_NAME, null, values)
        db.close()
    }

    fun clearAllData() {
        val db = this.writableDatabase
        db.delete(TABLE_NAME, null, null)
        db.close()
    }
}