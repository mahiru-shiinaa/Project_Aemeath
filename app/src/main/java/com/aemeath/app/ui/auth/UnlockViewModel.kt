package com.aemeath.app.ui.auth

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aemeath.app.data.repository.PreferencesRepository
import com.aemeath.app.security.CryptoManager
import com.aemeath.app.security.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UnlockUiState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
    val isLockedOut: Boolean = false,
    val lockoutSecondsRemaining: Int = 0,
    val isBiometricAvailable: Boolean = false,
    val wrongAttempts: Int = 0
)

@HiltViewModel
class UnlockViewModel @Inject constructor(
    private val cryptoManager: CryptoManager,
    private val sessionManager: SessionManager,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UnlockUiState())
    val uiState: StateFlow<UnlockUiState> = _uiState.asStateFlow()

    val isBiometricEnabled = preferencesRepository.isBiometricEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    init {
        checkBiometricAvailability()
        startLockoutTimer()
    }

    private fun checkBiometricAvailability() {
        viewModelScope.launch {
            val enabled = preferencesRepository.isBiometricEnabled
                .stateIn(viewModelScope, SharingStarted.Eagerly, false).value
            _uiState.value = _uiState.value.copy(isBiometricAvailable = enabled)
        }
    }

    private fun startLockoutTimer() {
        viewModelScope.launch {
            while (isActive) {
                val isLockedOut = sessionManager.isLockedOut()
                val remaining = (sessionManager.lockoutRemainingMs() / 1000).toInt()
                _uiState.value = _uiState.value.copy(
                    isLockedOut = isLockedOut,
                    lockoutSecondsRemaining = remaining,
                    wrongAttempts = sessionManager.wrongAttempts.value
                )
                delay(1000)
            }
        }
    }

    fun unlockWithPassword(password: String) {
        if (sessionManager.isLockedOut()) {
            _uiState.value = _uiState.value.copy(
                error = "Quá nhiều lần sai. Vui lòng đợi."
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val storedHash = preferencesRepository.getPasswordHash()
                    ?: throw Exception("Không tìm thấy cấu hình")

                val isValid = cryptoManager.verifyPassword(password, storedHash)

                if (!isValid) {
                    sessionManager.recordWrongAttempt()
                    val attempts = sessionManager.wrongAttempts.value
                    val isNowLockedOut = sessionManager.isLockedOut()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = if (isNowLockedOut)
                            "Đã sai ${SessionManager.MAX_WRONG_ATTEMPTS} lần. Khóa 30 giây."
                        else
                            "Mật khẩu không đúng (${attempts}/${SessionManager.MAX_WRONG_ATTEMPTS})",
                        wrongAttempts = attempts,
                        isLockedOut = isNowLockedOut
                    )
                    return@launch
                }

                // Đúng password — derive key và unlock
                val saltBase64 = preferencesRepository.getEncryptionSalt()
                    ?: throw Exception("Không tìm thấy salt")
                val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
                val encryptionKey = cryptoManager.deriveKeyFromPassword(password, salt)

                sessionManager.unlock(encryptionKey)
                _uiState.value = _uiState.value.copy(isLoading = false, isSuccess = true)

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Lỗi: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}