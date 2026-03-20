package org.mountaineers.traillog.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface TrailReportDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reports: List<TrailReport>)

    @Update
    suspend fun update(report: TrailReport)

    @Query("DELETE FROM TrailReport WHERE id = :reportId")
    suspend fun delete(reportId: String)

    @Query("SELECT * FROM TrailReport WHERE isInvalidated = 0 ORDER BY timestamp DESC")
    suspend fun getAll(): List<TrailReport>
}