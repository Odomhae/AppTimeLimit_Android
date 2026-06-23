package com.odom.applimit.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppLimitDao {
    @Query("SELECT * FROM app_limits ORDER BY sortOrder ASC")
    fun getAllLimits(): Flow<List<AppLimitEntity>>

    @Query("SELECT * FROM app_limits WHERE isEnabled = 1 ORDER BY packageName ASC")
    suspend fun getEnabledLimits(): List<AppLimitEntity>

    @Query("SELECT * FROM app_limits WHERE packageName = :packageName")
    suspend fun getLimitForPackage(packageName: String): AppLimitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(limit: AppLimitEntity)

    @Delete
    suspend fun delete(limit: AppLimitEntity)

    @Update
    suspend fun update(limit: AppLimitEntity)

    @Update
    suspend fun updateAll(limits: List<AppLimitEntity>)

    @Query("UPDATE app_limits SET lastWarningDate = '', lastBlockedDate = '', snoozedMinutes = 0")
    suspend fun resetDailyNotificationFlags()
}
