package com.aemeath.app.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor() {

    // Key chỉ sống trong memory, không bao giờ ghi xuống disk
    private var _encryptionKey: SecretKey? = null

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _wrongAttempts = MutableStateFlow(0)
    val wrongAttempts: StateFlow<Int> = _wrongAttempts.asStateFlow()

    companion object {
        const val MAX_WRONG_ATTEMPTS = 5
        const val LOCKOUT_DURATION_MS = 30_000L // 30 seconds
    }

    private var lockoutUntil: Long = 0L

    fun unlock(key: SecretKey) {
        _encryptionKey = key
        _isUnlocked.value = true
        _wrongAttempts.value = 0
        lockoutUntil = 0L
    }

    fun lock() {
        _encryptionKey = null
        _isUnlocked.value = false
    }

    fun getKey(): SecretKey? = _encryptionKey

    fun requireKey(): SecretKey {
        return _encryptionKey ?: throw IllegalStateException("App is locked. Please unlock first.")
    }

    fun recordWrongAttempt() {
        val current = _wrongAttempts.value + 1
        _wrongAttempts.value = current
        if (current >= MAX_WRONG_ATTEMPTS) {
            lockoutUntil = System.currentTimeMillis() + LOCKOUT_DURATION_MS
        }
    }

    fun resetWrongAttempts() {
        _wrongAttempts.value = 0
        lockoutUntil = 0L
    }

    fun isLockedOut(): Boolean {
        return System.currentTimeMillis() < lockoutUntil
    }

    fun lockoutRemainingMs(): Long {
        val remaining = lockoutUntil - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0L
    }
}