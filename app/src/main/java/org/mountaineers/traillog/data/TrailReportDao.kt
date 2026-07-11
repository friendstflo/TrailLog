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

    @Query("SELECT * FROM reports ORDER BY timestamp DESC")
    suspend fun getAllOnce(): List<TrailReport>

    @Query("SELECT * FROM reports WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TrailReport?

    /** Creates / updates waiting to upload (not soft-deleted). */
    @Query("SELECT * FROM reports WHERE isOfflineCreated = 1 AND isInvalidated = 0")
    suspend fun getPendingUploads(): List<TrailReport>

    /** Soft-deleted rows waiting for remote delete. */
    @Query("SELECT * FROM reports WHERE isInvalidated = 1")
    suspend fun getPendingDeletes(): List<TrailReport>

    /** Pending creates/updates + pending deletes (for sync banner). */
    @Query(
        """
        SELECT
          (SELECT COUNT(*) FROM reports WHERE isOfflineCreated = 1 AND isInvalidated = 0) +
          (SELECT COUNT(*) FROM reports WHERE isInvalidated = 1)
        """
    )
    fun pendingSyncCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: TrailReport)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reports: List<TrailReport>)

    @Update
    suspend fun update(report: TrailReport)

    @Query("DELETE FROM reports WHERE id = :id")
    suspend fun deleteById(id: String)
}
