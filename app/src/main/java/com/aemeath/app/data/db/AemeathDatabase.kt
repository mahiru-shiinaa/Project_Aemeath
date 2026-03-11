package com.aemeath.app.data.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.aemeath.app.data.db.dao.AccountDao
import com.aemeath.app.data.db.dao.AppSettingDao
import com.aemeath.app.data.db.dao.WebAppDao
import com.aemeath.app.data.db.entity.AccountEntity
import com.aemeath.app.data.db.entity.AppSettingEntity
import com.aemeath.app.data.db.entity.WebAppEntity

@Database(
    entities = [
        WebAppEntity::class,
        AccountEntity::class,
        AppSettingEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AemeathDatabase : RoomDatabase() {

    abstract fun webAppDao(): WebAppDao
    abstract fun accountDao(): AccountDao
    abstract fun appSettingDao(): AppSettingDao

    companion object {
        const val DATABASE_NAME = "aemeath.db"
    }
}