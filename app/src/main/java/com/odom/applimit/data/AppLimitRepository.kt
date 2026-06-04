package com.odom.applimit.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLimitRepository @Inject constructor(private val dao: AppLimitDao) {
    fun getAllLimits(): Flow<List<AppLimitEntity>> = dao.getAllLimits()

    suspend fun getLimitForPackage(packageName: String): AppLimitEntity? =
        dao.getLimitForPackage(packageName)

    suspend fun upsert(limit: AppLimitEntity) = dao.upsert(limit)

    suspend fun delete(limit: AppLimitEntity) = dao.delete(limit)

    suspend fun resetDailyNotificationFlags() = dao.resetDailyNotificationFlags()
}
