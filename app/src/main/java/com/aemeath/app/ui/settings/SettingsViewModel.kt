package com.aemeath.app.ui.settings

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aemeath.app.data.repository.PreferencesRepository
import com.aemeath.app.security.CryptoManager
import com.aemeath.app.security.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val cryptoManager: CryptoManager,
    private val sessionManager: SessionManager
) : ViewModel() {

    // Theme (giữ nguyên)
    val theme = preferencesRepository.theme

    // Trạng thái Biometric (Flow để UI tự cập nhật)
    val isBiometricEnabled = preferencesRepository.isBiometricEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun setTheme(theme: String) {
        viewModelScope.launch { preferencesRepository.setTheme(theme) }
    }

    /**
     * Tắt vân tay: Chỉ cần xóa cờ trong DataStore
     */
    fun disableBiometric() {
        viewModelScope.launch {
            preferencesRepository.setBiometricEnabled(false)
        }
    }

    /**
     * Bật vân tay: Cần xác thực mật khẩu trước
     */
    fun enableBiometric(password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                // 1. Kiểm tra mật khẩu nhập vào có đúng không
                val storedHash = preferencesRepository.getPasswordHash()
                if (storedHash == null || !cryptoManager.verifyPassword(password, storedHash)) {
                    _error.value = "Mật khẩu không đúng"
                    return@launch
                }

                // 2. Tạo key biometric trong Keystore
                cryptoManager.generateBiometricKey()

                // 3. Lấy session key hiện tại (đang dùng để decrypt db)
                // Lưu ý: Key này đang nằm trong RAM (SessionManager)
                val currentKey = sessionManager.getKey()
                    ?: throw IllegalStateException("Session key not found")

                // 4. Mã hóa session key này và lưu xuống disk (để lần sau dùng vân tay unlock được)
                val keyBase64 = Base64.encodeToString(currentKey.encoded, Base64.NO_WRAP)
                preferencesRepository.saveBiometricEncryptedKey(keyBase64)

                // 5. Bật cờ
                preferencesRepository.setBiometricEnabled(true)

                _error.value = null
                onSuccess()
            } catch (e: Exception) {
                _error.value = "Lỗi kích hoạt: ${e.message}"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}