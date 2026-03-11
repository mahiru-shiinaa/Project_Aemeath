package com.aemeath.app.data.db.dao

import androidx.room.*
import com.aemeath.app.data.db.entity.AccountEntity
import com.aemeath.app.data.db.entity.AppSettingEntity
import com.aemeath.app.data.db.entity.WebAppEntity
import kotlinx.coroutines.flow.Flow

// ─── WebApp DAO ───────────────────────────────────────────────────────────────
@Dao
interface WebAppDao {

    @Query("SELECT * FROM web_apps ORDER BY name ASC")
    fun getAllWebAppsFlow(): Flow<List<WebAppEntity>>

    @Query("SELECT * FROM web_apps ORDER BY name ASC")
    suspend fun getAllWebApps(): List<WebAppEntity>

    @Query("SELECT * FROM web_apps WHERE id = :id")
    suspend fun getWebAppById(id: Long): WebAppEntity?

    @Query("SELECT * FROM web_apps WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchWebApps(query: String): Flow<List<WebAppEntity>>

    @Query("SELECT COUNT(*) FROM accounts WHERE webAppId = :webAppId")
    fun getAccountCountForWebApp(webAppId: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(webApp: WebAppEntity): Long

    @Update
    suspend fun update(webApp: WebAppEntity)

    @Delete
    suspend fun delete(webApp: WebAppEntity)

    @Query("DELETE FROM web_apps WHERE id = :id")
    suspend fun deleteById(id: Long)
}

// ─── Account DAO ─────────────────────────────────────────────────────────────
@Dao
interface AccountDao {

//    @Query("SELECT * FROM accounts WHERE webAppId = :webAppId ORDER BY updatedAt DESC")
//    fun getAccountsByWebApp(webAppId: Long): Flow<List<AccountEntity>>
//
//    @Query("SELECT * FROM accounts WHERE webAppId = :webAppId ORDER BY updatedAt DESC")
//    suspend fun getAccountsByWebAppSync(webAppId: Long): List<AccountEntity>

    // Sắp xếp theo position tăng dần
    @Query("SELECT * FROM accounts WHERE webAppId = :webAppId ORDER BY position ASC, id DESC")
    fun getAccountsByWebApp(webAppId: Long): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE webAppId = :webAppId ORDER BY position ASC, id DESC")
    suspend fun getAccountsByWebAppSync(webAppId: Long): List<AccountEntity>



    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getAccountById(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts ORDER BY updatedAt DESC")
    suspend fun getAllAccounts(): List<AccountEntity>

    @Query("""
        SELECT a.* FROM accounts a
        JOIN web_apps w ON a.webAppId = w.id
        WHERE a.title LIKE '%' || :query || '%'
        OR a.username LIKE '%' || :query || '%'
        OR w.name LIKE '%' || :query || '%'
        ORDER BY a.updatedAt DESC
    """)
    fun searchAccounts(query: String): Flow<List<AccountEntity>>



    @Query("SELECT COUNT(*) FROM accounts")
    fun getTotalAccountCount(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT webAppId) FROM accounts")
    fun getTotalWebAppCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Update
    suspend fun updateList(accounts: List<AccountEntity>)

    @Delete
    suspend fun delete(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM accounts WHERE webAppId = :webAppId")
    suspend fun deleteAllByWebApp(webAppId: Long)
}

// ─── Settings DAO ────────────────────────────────────────────────────────────
@Dao
interface AppSettingDao {

    @Query("SELECT * FROM app_settings WHERE `key` = :key")
    suspend fun getSetting(key: String): AppSettingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSetting(setting: AppSettingEntity)

    @Query("DELETE FROM app_settings WHERE `key` = :key")
    suspend fun deleteSetting(key: String)
}