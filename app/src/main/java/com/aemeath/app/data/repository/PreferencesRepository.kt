package com.aemeath.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aemeath_prefs")

@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val dataStore = context.dataStore

    // ─── Keys ─────────────────────────────────────────────────────────────────
    companion object {
        val KEY_IS_SETUP_COMPLETE = booleanPreferencesKey("is_setup_complete")
        val KEY_PASSWORD_HASH = stringPreferencesKey("password_hash")
        val KEY_ENCRYPTION_SALT = stringPreferencesKey("encryption_salt")
        val KEY_BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val KEY_BIOMETRIC_ENCRYPTED_KEY = stringPreferencesKey("biometric_encrypted_key")
        val KEY_THEME = stringPreferencesKey("theme") // "light" | "dark" | "system"
        val KEY_AUTO_LOCK_MINUTES = intPreferencesKey("auto_lock_minutes")
        val KEY_CLIPBOARD_CLEAR_SECONDS = intPreferencesKey("clipboard_clear_seconds")
        val KEY_LAST_AUTO_BACKUP = longPreferencesKey("last_auto_backup")
        val KEY_AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val KEY_BACKUP_INTERVAL_DAYS = intPreferencesKey("backup_interval_days")
        val KEY_LIST_VIEW_MODE = stringPreferencesKey("list_view_mode") // "list" | "grid"
    }

    // ─── Setup ────────────────────────────────────────────────────────────────

    val isSetupComplete: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[KEY_IS_SETUP_COMPLETE] ?: false }

    suspend fun setSetupComplete(complete: Boolean) {
        dataStore.edit { it[KEY_IS_SETUP_COMPLETE] = complete }
    }

    // ─── Password ─────────────────────────────────────────────────────────────

    suspend fun savePasswordHash(hash: String) {
        dataStore.edit { it[KEY_PASSWORD_HASH] = hash }
    }

    suspend fun getPasswordHash(): String? {
        return dataStore.data.first()[KEY_PASSWORD_HASH]
    }

    suspend fun saveEncryptionSalt(saltBase64: String) {
        dataStore.edit { it[KEY_ENCRYPTION_SALT] = saltBase64 }
    }

    suspend fun getEncryptionSalt(): String? {
        return dataStore.data.first()[KEY_ENCRYPTION_SALT]
    }

    // ─── Biometric ────────────────────────────────────────────────────────────

    val isBiometricEnabled: Flow<Boolean> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[KEY_BIOMETRIC_ENABLED] ?: false }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun saveBiometricEncryptedKey(encryptedKeyBase64: String) {
        dataStore.edit { it[KEY_BIOMETRIC_ENCRYPTED_KEY] = encryptedKeyBase64 }
    }

    suspend fun getBiometricEncryptedKey(): String? {
        return dataStore.data.first()[KEY_BIOMETRIC_ENCRYPTED_KEY]
    }

    // ─── Theme ────────────────────────────────────────────────────────────────

    val theme: Flow<String> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[KEY_THEME] ?: "system" }

    suspend fun setTheme(theme: String) {
        dataStore.edit { it[KEY_THEME] = theme }
    }

    // ─── Security Settings ────────────────────────────────────────────────────

    val autoLockMinutes: Flow<Int> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[KEY_AUTO_LOCK_MINUTES] ?: 5 }

    suspend fun setAutoLockMinutes(minutes: Int) {
        dataStore.edit { it[KEY_AUTO_LOCK_MINUTES] = minutes }
    }

    val clipboardClearSeconds: Flow<Int> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[KEY_CLIPBOARD_CLEAR_SECONDS] ?: 30 }

    // ─── View Mode ────────────────────────────────────────────────────────────

    val listViewMode: Flow<String> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[KEY_LIST_VIEW_MODE] ?: "list" }

    suspend fun setListViewMode(mode: String) {
        dataStore.edit { it[KEY_LIST_VIEW_MODE] = mode }
    }
}