package com.nammameter.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Insert
    suspend fun insert(trip: TripEntity): Long

    @Query("SELECT * FROM trips WHERE id = :id")
    fun observe(id: Long): Flow<TripEntity?>

    @Query("SELECT * FROM trips ORDER BY startEpochMs DESC")
    fun observeAll(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE endEpochMs >= :fromEpochMs ORDER BY startEpochMs DESC")
    fun observeSince(fromEpochMs: Long): Flow<List<TripEntity>>

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun delete(id: Long)
}
