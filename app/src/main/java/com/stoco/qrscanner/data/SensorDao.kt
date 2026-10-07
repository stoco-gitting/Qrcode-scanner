package com.stoco.qrscanner.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SensorDao {
    @Query("SELECT * FROM sensors ORDER BY scannedAt DESC")
    fun observeAll(): Flow<List<Sensor>>

    @Query("SELECT * FROM sensors ORDER BY scannedAt ASC")
    suspend fun getAll(): List<Sensor>

    /** Returns -1 when the DEVEUI already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(sensor: Sensor): Long

    @Delete
    suspend fun delete(sensor: Sensor)

    @Query("DELETE FROM sensors")
    suspend fun clear()
}
