package org.mountaineers.traillog.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrailReportDao {

    @Query("SELECT * FROM reports ORDER BY timestamp DESC")
    fun getAll(): Flow<List<TrailReport>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: TrailReport)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reports: List<TrailReport>)

    @Update
    suspend fun update(report: TrailReport)

    @Query("DELETE FROM reports WHERE id = :id")
    suspend fun deleteById(id: String)
}