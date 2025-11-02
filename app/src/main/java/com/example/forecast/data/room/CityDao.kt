package com.example.forecast.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CityDao {
    @Query("SELECT * FROM cities ORDER BY orderIndex ASC")
    suspend fun getActiveCities(): List<CityEntity>

    @Query("SELECT MAX(orderIndex) FROM cities")
    suspend fun getMaxOrderIndex(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CityEntity)

    @Query("DELETE FROM cities WHERE cityName = :cityName")
    suspend fun delete(cityName: String)
}