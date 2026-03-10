package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StarDao {
    @Query("SELECT * FROM stars")
    fun getAllStars(): Flow<List<Star>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStars(stars: List<Star>)

    @Query("SELECT * FROM stars WHERE magnitude <= :maxMag")
    fun getVisibleStars(maxMag: Float): Flow<List<Star>>

    @Query("SELECT COUNT(*) FROM stars")
    suspend fun getCount(): Int
}
