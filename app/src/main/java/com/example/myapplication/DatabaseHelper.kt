package com.example.myapplication

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_VERSION = 3 // Veritabanı sürümünü arttırdık
        const val DATABASE_NAME = "SensorData.db"
        const val TABLE_NAME = "sensor_data"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_X = "x"
        const val COLUMN_Y = "y"
        const val COLUMN_Z = "z"
        const val COLUMN_ROT_X = "rot_x"
        const val COLUMN_ROT_Y = "rot_y"
        const val COLUMN_ROT_Z = "rot_z"
        const val COLUMN_GYRO_X = "gyro_x"
        const val COLUMN_GYRO_Y = "gyro_y"
        const val COLUMN_GYRO_Z = "gyro_z"
        // Yeni sütunlar
        const val COLUMN_LINACC_X = "linacc_x"
        const val COLUMN_LINACC_Y = "linacc_y"
        const val COLUMN_LINACC_Z = "linacc_z"
        const val COLUMN_GRAV_X = "grav_x"
        const val COLUMN_GRAV_Y = "grav_y"
        const val COLUMN_GRAV_Z = "grav_z"
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
                + "$COLUMN_GYRO_Z REAL, "
                + "$COLUMN_LINACC_X REAL, "
                + "$COLUMN_LINACC_Y REAL, "
                + "$COLUMN_LINACC_Z REAL, "
                + "$COLUMN_GRAV_X REAL, "
                + "$COLUMN_GRAV_Y REAL, "
                + "$COLUMN_GRAV_Z REAL)")
        db.execSQL(createTableSQL)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            // Eski sürümden yeni sürüme geçiş: Yeni sütunları ekleyin
            try {
                // Mevcut sütunları ekleyin (eğer eklenmemişse)
                if (oldVersion < 2) {
                    db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_ROT_X REAL DEFAULT 0")
                    db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_ROT_Y REAL DEFAULT 0")
                    db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_ROT_Z REAL DEFAULT 0")
                    db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_GYRO_X REAL DEFAULT 0")
                    db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_GYRO_Y REAL DEFAULT 0")
                    db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_GYRO_Z REAL DEFAULT 0")
                }

                // Yeni sensör verileri için sütunlar ekleyin
                db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_LINACC_X REAL DEFAULT 0")
                db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_LINACC_Y REAL DEFAULT 0")
                db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_LINACC_Z REAL DEFAULT 0")
                db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_GRAV_X REAL DEFAULT 0")
                db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_GRAV_Y REAL DEFAULT 0")
                db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_GRAV_Z REAL DEFAULT 0")
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

            // Temel sensör indeksleri
            val rotXIndex = cursor.getColumnIndex(COLUMN_ROT_X)
            val rotYIndex = cursor.getColumnIndex(COLUMN_ROT_Y)
            val rotZIndex = cursor.getColumnIndex(COLUMN_ROT_Z)
            val gyroXIndex = cursor.getColumnIndex(COLUMN_GYRO_X)
            val gyroYIndex = cursor.getColumnIndex(COLUMN_GYRO_Y)
            val gyroZIndex = cursor.getColumnIndex(COLUMN_GYRO_Z)

            // Yeni sensör indeksleri
            val linAccXIndex = cursor.getColumnIndex(COLUMN_LINACC_X)
            val linAccYIndex = cursor.getColumnIndex(COLUMN_LINACC_Y)
            val linAccZIndex = cursor.getColumnIndex(COLUMN_LINACC_Z)
            val gravXIndex = cursor.getColumnIndex(COLUMN_GRAV_X)
            val gravYIndex = cursor.getColumnIndex(COLUMN_GRAV_Y)
            val gravZIndex = cursor.getColumnIndex(COLUMN_GRAV_Z)

            do {
                val timestamp = cursor.getLong(timestampIndex)
                val x = cursor.getFloat(xIndex)
                val y = cursor.getFloat(yIndex)
                val z = cursor.getFloat(zIndex)

                // Rotasyon ve gyro değerlerini oku
                val rotX = if (rotXIndex >= 0) cursor.getFloat(rotXIndex) else 0f
                val rotY = if (rotYIndex >= 0) cursor.getFloat(rotYIndex) else 0f
                val rotZ = if (rotZIndex >= 0) cursor.getFloat(rotZIndex) else 0f
                val gyroX = if (gyroXIndex >= 0) cursor.getFloat(gyroXIndex) else 0f
                val gyroY = if (gyroYIndex >= 0) cursor.getFloat(gyroYIndex) else 0f
                val gyroZ = if (gyroZIndex >= 0) cursor.getFloat(gyroZIndex) else 0f

                // Yeni sensör değerlerini oku
                val linAccX = if (linAccXIndex >= 0) cursor.getFloat(linAccXIndex) else 0f
                val linAccY = if (linAccYIndex >= 0) cursor.getFloat(linAccYIndex) else 0f
                val linAccZ = if (linAccZIndex >= 0) cursor.getFloat(linAccZIndex) else 0f
                val gravX = if (gravXIndex >= 0) cursor.getFloat(gravXIndex) else 0f
                val gravY = if (gravYIndex >= 0) cursor.getFloat(gravYIndex) else 0f
                val gravZ = if (gravZIndex >= 0) cursor.getFloat(gravZIndex) else 0f

                val sensorData = SensorData(
                    timestamp, x, y, z,
                    rotX, rotY, rotZ,
                    gyroX, gyroY, gyroZ,
                    linAccX, linAccY, linAccZ,
                    gravX, gravY, gravZ
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
        gyroX: Float = 0f, gyroY: Float = 0f, gyroZ: Float = 0f,
        linAccX: Float = 0f, linAccY: Float = 0f, linAccZ: Float = 0f,
        gravX: Float = 0f, gravY: Float = 0f, gravZ: Float = 0f
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
        values.put(COLUMN_LINACC_X, linAccX)
        values.put(COLUMN_LINACC_Y, linAccY)
        values.put(COLUMN_LINACC_Z, linAccZ)
        values.put(COLUMN_GRAV_X, gravX)
        values.put(COLUMN_GRAV_Y, gravY)
        values.put(COLUMN_GRAV_Z, gravZ)
        db.insert(TABLE_NAME, null, values)
        db.close()
    }

    fun clearAllData() {
        val db = this.writableDatabase
        db.delete(TABLE_NAME, null, null)
        db.close()
    }
}