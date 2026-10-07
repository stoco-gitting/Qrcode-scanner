package com.stoco.qrscanner.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sensors", indices = [Index(value = ["devEui"], unique = true)])
data class Sensor(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pn: String,
    val devEui: String,
    val appEui: String,
    val appKey: String,
    val scannedAt: Long = System.currentTimeMillis(),
)
