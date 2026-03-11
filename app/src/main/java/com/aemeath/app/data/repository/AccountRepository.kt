package com.aemeath.app.data.repository

import com.aemeath.app.data.db.dao.AccountDao
import com.aemeath.app.data.db.dao.WebAppDao
import com.aemeath.app.data.db.entity.AccountEntity
import com.aemeath.app.data.db.entity.WebAppEntity
import com.aemeath.app.security.CryptoManager
import com.aemeath.app.security.SessionManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val webAppDao: WebAppDao,
    private val accountDao: AccountDao,
    private val cryptoManager: CryptoManager,
    private val sessionManager: SessionManager
) {
    // ─── WebApp ───────────────────────────────────────────────────────────────
    fun getAllWebAppsFlow(): Flow<List<WebAppEntity>> = webAppDao.getAllWebAppsFlow()
    suspend fun getAllWebApps(): List<WebAppEntity> = webAppDao.getAllWebApps()
    fun searchWebApps(query: String): Flow<List<WebAppEntity>> = webAppDao.searchWebApps(query)
    fun getAccountCountForWebApp(webAppId: Long): Flow<Int> = webAppDao.getAccountCountForWebApp(webAppId)
    suspend fun getWebAppById(id: Long): WebAppEntity? = webAppDao.getWebAppById(id)

    suspend fun insertWebApp(name: String, iconEmoji: String = "🌐"): Long {
        return webAppDao.insert(WebAppEntity(name = name, iconEmoji = iconEmoji))
    }

    suspend fun updateWebApp(webApp: WebAppEntity) = webAppDao.update(
        webApp.copy(updatedAt = System.currentTimeMillis())
    )

    suspend fun deleteWebApp(webApp: WebAppEntity) = webAppDao.delete(webApp)

    // ─── Account ─────────────────────────────────────────────────────────────
    fun getAccountsByWebApp(webAppId: Long): Flow<List<AccountEntity>> =
        accountDao.getAccountsByWebApp(webAppId)

    // Sync version dùng trong HomeViewModel để đếm account (không cần Flow)
    suspend fun getAccountsByWebAppSync(webAppId: Long): List<AccountEntity> =
        accountDao.getAccountsByWebAppSync(webAppId)

    fun searchAccounts(query: String): Flow<List<AccountEntity>> =
        accountDao.searchAccounts(query)

    fun getTotalAccountCount(): Flow<Int> = accountDao.getTotalAccountCount()
    fun getTotalWebAppCount(): Flow<Int> = accountDao.getTotalWebAppCount()

    suspend fun getAllAccounts(): List<AccountEntity> = accountDao.getAllAccounts()

    suspend fun getAccountById(id: Long): AccountEntity? = accountDao.getAccountById(id)

    suspend fun insertAccount(
        webAppId: Long,
        title: String,
        username: String,
        password: String,
        notes: String = ""
    ): Long {
        val key = sessionManager.requireKey()
        val encryptedPassword = cryptoManager.encryptString(password, key)
        return accountDao.insert(
            AccountEntity(
                webAppId = webAppId,
                title = title,
                username = username,
                encryptedPassword = encryptedPassword,
                notes = notes
            )
        )
    }

    suspend fun updateAccount(
        account: AccountEntity,
        newPassword: String? = null
    ) {
        val updatedAccount = if (newPassword != null) {
            val key = sessionManager.requireKey()
            val encryptedPassword = cryptoManager.encryptString(newPassword, key)
            account.copy(encryptedPassword = encryptedPassword, updatedAt = System.currentTimeMillis())
        } else {
            account.copy(updatedAt = System.currentTimeMillis())
        }
        accountDao.update(updatedAccount)
    }

    // Dùng cho ChangePasswordViewModel — lưu thẳng entity đã có encryptedPassword mới
    suspend fun updateAccountDirect(account: AccountEntity) {
        accountDao.update(account)
    }



    suspend fun deleteAccount(account: AccountEntity) = accountDao.delete(account)
    suspend fun deleteAccountById(id: Long) = accountDao.deleteById(id)

    fun decryptPassword(encryptedPassword: String): String {
        val key = sessionManager.requireKey()
        return cryptoManager.decryptString(encryptedPassword, key)
    }
}