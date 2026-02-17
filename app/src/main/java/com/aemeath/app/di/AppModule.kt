package com.aemeath.app.di

import android.content.Context
import androidx.room.Room
import com.aemeath.app.data.db.AemeathDatabase
import com.aemeath.app.data.db.dao.AccountDao
import com.aemeath.app.data.db.dao.AppSettingDao
import com.aemeath.app.data.db.dao.WebAppDao
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
    fun provideDatabase(@ApplicationContext context: Context): AemeathDatabase {
        return Room.databaseBuilder(
            context,
            AemeathDatabase::class.java,
            AemeathDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration() // Dev phase - đổi sau khi release
            .build()
    }

    @Provides
    fun provideWebAppDao(db: AemeathDatabase): WebAppDao = db.webAppDao()

    @Provides
    fun provideAccountDao(db: AemeathDatabase): AccountDao = db.accountDao()

    @Provides
    fun provideAppSettingDao(db: AemeathDatabase): AppSettingDao = db.appSettingDao()
}