package com.aemeath.app.ui.auth

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aemeath.app.data.repository.PreferencesRepository
import com.aemeath.app.security.CryptoManager
import com.aemeath.app.security.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetupUiState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
    val passwordStrength: CryptoManager.PasswordStrength? = null
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val cryptoManager: CryptoManager,
    private val sessionManager: SessionManager,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    fun onPasswordChanged(password: String) {
        val strength = if (password.isNotEmpty())
            cryptoManager.checkPasswordStrength(password)
        else null
        _uiState.value = _uiState.value.copy(passwordStrength = strength)
    }

    fun createMasterPassword(password: String, confirmPassword: String, enableBiometric: Boolean) {
        if (password != confirmPassword) {
            _uiState.value = _uiState.value.copy(error = "Mật khẩu xác nhận không khớp")
            return
        }
        if (password.length < 6) {
            _uiState.value = _uiState.value.copy(error = "Mật khẩu phải ít nhất 6 ký tự")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                // 1. Tạo salt mới
                val salt = cryptoManager.generateSalt()
                val saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP)

                // 2. Derive encryption key
                val encryptionKey = cryptoManager.deriveKeyFromPassword(password, salt)

                // 3. Lưu hash để verify sau
                val passwordHash = cryptoManager.hashPasswordForVerification(password)

                // 4. Lưu vào DataStore
                preferencesRepository.saveEncryptionSalt(saltBase64)
                preferencesRepository.savePasswordHash(passwordHash)
                preferencesRepository.setSetupComplete(true)

                // 5. Setup biometric nếu bật
                if (enableBiometric) {
                    cryptoManager.generateBiometricKey()
                    preferencesRepository.setBiometricEnabled(true)
                }

                // 6. Unlock session
                sessionManager.unlock(encryptionKey)

                _uiState.value = _uiState.value.copy(isLoading = false, isSuccess = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Lỗi khởi tạo: ${e.message}"
                )
            }
        }
    }
}