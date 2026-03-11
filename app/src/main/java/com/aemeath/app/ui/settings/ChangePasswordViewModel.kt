package com.aemeath.app.ui.settings

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aemeath.app.data.repository.AccountRepository
import com.aemeath.app.data.repository.PreferencesRepository
import com.aemeath.app.security.CryptoManager
import com.aemeath.app.security.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChangePasswordUiState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
    val progress: Int = 0,       // 0-100 cho progress bar
    val progressText: String = "",
    val newPasswordStrength: CryptoManager.PasswordStrength? = null
)

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val cryptoManager: CryptoManager,
    private val sessionManager: SessionManager,
    private val preferencesRepository: PreferencesRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangePasswordUiState())
    val uiState: StateFlow<ChangePasswordUiState> = _uiState.asStateFlow()

    fun onNewPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(
            newPasswordStrength = if (password.isNotEmpty())
                cryptoManager.checkPasswordStrength(password) else null
        )
    }

    fun changePassword(oldPassword: String, newPassword: String, confirmPassword: String) {
        // Validate
        if (oldPassword.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Vui lòng nhập mật khẩu cũ"); return
        }
        if (newPassword.length < 6) {
            _uiState.value = _uiState.value.copy(error = "Mật khẩu mới phải ít nhất 6 ký tự"); return
        }
        if (newPassword != confirmPassword) {
            _uiState.value = _uiState.value.copy(error = "Mật khẩu xác nhận không khớp"); return
        }
        if (newPassword == oldPassword) {
            _uiState.value = _uiState.value.copy(error = "Mật khẩu mới phải khác mật khẩu cũ"); return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, progress = 0)

            try {
                // Bước 1: Xác minh mật khẩu cũ
                _uiState.value = _uiState.value.copy(progressText = "Xác minh mật khẩu cũ...", progress = 10)
                val storedHash = preferencesRepository.getPasswordHash()
                    ?: throw Exception("Không tìm thấy cấu hình")
                if (!cryptoManager.verifyPassword(oldPassword, storedHash)) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false, error = "Mật khẩu cũ không đúng", progress = 0
                    ); return@launch
                }

                // Bước 2: Derive key cũ (để decrypt)
                _uiState.value = _uiState.value.copy(progressText = "Tải dữ liệu...", progress = 20)
                val oldSaltBase64 = preferencesRepository.getEncryptionSalt()
                    ?: throw Exception("Không tìm thấy salt")
                val oldSalt = Base64.decode(oldSaltBase64, Base64.NO_WRAP)
                val oldKey = cryptoManager.deriveKeyFromPassword(oldPassword, oldSalt)

                // Bước 3: Lấy tất cả accounts
                _uiState.value = _uiState.value.copy(progressText = "Đang tải tài khoản...", progress = 30)
                val allAccounts = accountRepository.getAllAccounts()

                // Bước 4: Tạo key mới
                _uiState.value = _uiState.value.copy(progressText = "Tạo khoá mã hoá mới...", progress = 40)
                val newSalt = cryptoManager.generateSalt()
                val newKey = cryptoManager.deriveKeyFromPassword(newPassword, newSalt)

                // Bước 5: Re-encrypt từng account
                _uiState.value = _uiState.value.copy(progressText = "Mã hoá lại dữ liệu...", progress = 50)
                val total = allAccounts.size
                allAccounts.forEachIndexed { index, account ->
                    // Decrypt bằng key cũ
                    val plainPassword = cryptoManager.decryptString(account.encryptedPassword, oldKey)
                    // Encrypt bằng key mới
                    val newEncrypted = cryptoManager.encryptString(plainPassword, newKey)
                    // Update DB trực tiếp (không qua sessionManager key)
                    accountRepository.updateAccountDirect(
                        account.copy(encryptedPassword = newEncrypted)
                    )
                    // Cập nhật progress (50% → 85%)
                    val encProgress = 50 + ((index + 1) * 35 / (total.coerceAtLeast(1)))
                    _uiState.value = _uiState.value.copy(
                        progress = encProgress,
                        progressText = "Mã hoá ${index + 1}/$total tài khoản..."
                    )
                }

                // Bước 6: Lưu hash và salt mới
                _uiState.value = _uiState.value.copy(progressText = "Lưu cấu hình...", progress = 90)
                val newSaltBase64 = Base64.encodeToString(newSalt, Base64.NO_WRAP)
                val newHash = cryptoManager.hashPasswordForVerification(newPassword)
                preferencesRepository.saveEncryptionSalt(newSaltBase64)
                preferencesRepository.savePasswordHash(newHash)

                // Bước 7: Cập nhật session key trong memory
                sessionManager.unlock(newKey)

                _uiState.value = _uiState.value.copy(
                    isLoading = false, isSuccess = true, progress = 100,
                    progressText = "Hoàn tất!"
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Lỗi: ${e.message}",
                    progress = 0,
                    progressText = ""
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}