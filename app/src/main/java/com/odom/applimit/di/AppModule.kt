package com.odom.applimit.di

import android.content.Context
import com.odom.applimit.data.AppDatabase
import com.odom.applimit.data.AppLimitDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getInstance(context)

    @Provides
    @Singleton
    fun provideDao(db: AppDatabase): AppLimitDao = db.appLimitDao()
}
