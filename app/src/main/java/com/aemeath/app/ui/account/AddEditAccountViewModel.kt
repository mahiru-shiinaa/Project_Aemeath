package com.aemeath.app.ui.account

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aemeath.app.data.db.entity.AccountEntity
import com.aemeath.app.data.db.entity.WebAppEntity
import com.aemeath.app.data.repository.AccountRepository
import com.aemeath.app.security.CryptoManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddEditUiState(
    val isEditMode: Boolean = false,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null,

    // Form fields
    val selectedWebApp: WebAppEntity? = null,
    val webAppQuery: String = "",
    val webAppSuggestions: List<WebAppEntity> = emptyList(),
    val showWebAppDropdown: Boolean = false,

    val title: String = "",
    val username: String = "",
    val password: String = "",
    val notes: String = "",

    val passwordStrength: CryptoManager.PasswordStrength? = null,
    val showPasswordGenerator: Boolean = false,
    val generatedPassword: String = "",
    val genLength: Int = 16,
    val genUppercase: Boolean = true,
    val genLowercase: Boolean = true,
    val genDigits: Boolean = true,
    val genSymbols: Boolean = true,

    // Validation
    val titleError: String? = null,
    val usernameError: String? = null,
    val passwordError: String? = null,
    val webAppError: String? = null
)

@HiltViewModel
class AddEditAccountViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val cryptoManager: CryptoManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val editAccountId: Long? = savedStateHandle.get<Long>("accountId")?.takeIf { it != -1L }
    private val preselectedWebAppId: Long? = savedStateHandle.get<Long>("webAppId")?.takeIf { it != -1L }

    private val _uiState = MutableStateFlow(AddEditUiState())
    val uiState: StateFlow<AddEditUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Load all webapps for suggestions
            val allWebApps = accountRepository.getAllWebApps()

            if (editAccountId != null) {
                // Edit mode
                val account = accountRepository.getAccountById(editAccountId)
                val webApp = account?.let { accountRepository.getWebAppById(it.webAppId) }
                val decryptedPassword = account?.let {
                    try { accountRepository.decryptPassword(it.encryptedPassword) } catch (e: Exception) { "" }
                } ?: ""

                _uiState.value = _uiState.value.copy(
                    isEditMode = true,
                    isLoading = false,
                    selectedWebApp = webApp,
                    webAppQuery = webApp?.name ?: "",
                    title = account?.title ?: "",
                    username = account?.username ?: "",
                    password = decryptedPassword,
                    notes = account?.notes ?: "",
                    passwordStrength = if (decryptedPassword.isNotEmpty())
                        cryptoManager.checkPasswordStrength(decryptedPassword) else null,
                    webAppSuggestions = allWebApps
                )
            } else {
                // Add mode — preselect webapp if coming from AccountListScreen
                val preselectedWebApp = preselectedWebAppId?.let { accountRepository.getWebAppById(it) }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    selectedWebApp = preselectedWebApp,
                    webAppQuery = preselectedWebApp?.name ?: "",
                    webAppSuggestions = allWebApps
                )
            }
        }
    }

    // ─── WebApp Field ─────────────────────────────────────────────────────────

    fun onWebAppQueryChange(query: String) {
        viewModelScope.launch {
            val allWebApps = accountRepository.getAllWebApps()
            val filtered = if (query.isBlank()) allWebApps
            else allWebApps.filter { it.name.contains(query, ignoreCase = true) }

            _uiState.value = _uiState.value.copy(
                webAppQuery = query,
                webAppSuggestions = filtered,
                showWebAppDropdown = true,
                selectedWebApp = if (query.isBlank()) null else _uiState.value.selectedWebApp,
                webAppError = null
            )
        }
    }

    fun onWebAppSelected(webApp: WebAppEntity) {
        _uiState.value = _uiState.value.copy(
            selectedWebApp = webApp,
            webAppQuery = webApp.name,
            showWebAppDropdown = false,
            webAppError = null
        )
    }

    fun createNewWebApp(name: String, emoji: String) {
        viewModelScope.launch {
            val id = accountRepository.insertWebApp(name, emoji)
            val newWebApp = accountRepository.getWebAppById(id)
            newWebApp?.let { onWebAppSelected(it) }
        }
    }

    fun dismissWebAppDropdown() {
        _uiState.value = _uiState.value.copy(showWebAppDropdown = false)
    }

    // ─── Form Fields ──────────────────────────────────────────────────────────

    fun onTitleChange(v: String) { _uiState.value = _uiState.value.copy(title = v, titleError = null) }
    fun onUsernameChange(v: String) { _uiState.value = _uiState.value.copy(username = v, usernameError = null) }
    fun onNotesChange(v: String) { _uiState.value = _uiState.value.copy(notes = v) }

    fun onPasswordChange(v: String) {
        _uiState.value = _uiState.value.copy(
            password = v,
            passwordError = null,
            passwordStrength = if (v.isNotEmpty()) cryptoManager.checkPasswordStrength(v) else null
        )
    }

    // ─── Password Generator ───────────────────────────────────────────────────

    fun openPasswordGenerator() {
        val generated = cryptoManager.generateStrongPassword(
            length = _uiState.value.genLength,
            includeUppercase = _uiState.value.genUppercase,
            includeLowercase = _uiState.value.genLowercase,
            includeDigits = _uiState.value.genDigits,
            includeSymbols = _uiState.value.genSymbols
        )
        _uiState.value = _uiState.value.copy(showPasswordGenerator = true, generatedPassword = generated)
    }

    fun closePasswordGenerator() {
        _uiState.value = _uiState.value.copy(showPasswordGenerator = false)
    }

    fun onGenLengthChange(length: Int) {
        val generated = cryptoManager.generateStrongPassword(
            length = length,
            includeUppercase = _uiState.value.genUppercase,
            includeLowercase = _uiState.value.genLowercase,
            includeDigits = _uiState.value.genDigits,
            includeSymbols = _uiState.value.genSymbols
        )
        _uiState.value = _uiState.value.copy(genLength = length, generatedPassword = generated)
    }

    fun onGenOptionChange(uppercase: Boolean? = null, lowercase: Boolean? = null, digits: Boolean? = null, symbols: Boolean? = null) {
        val state = _uiState.value
        val newState = state.copy(
            genUppercase = uppercase ?: state.genUppercase,
            genLowercase = lowercase ?: state.genLowercase,
            genDigits = digits ?: state.genDigits,
            genSymbols = symbols ?: state.genSymbols
        )
        val generated = cryptoManager.generateStrongPassword(
            length = newState.genLength,
            includeUppercase = newState.genUppercase,
            includeLowercase = newState.genLowercase,
            includeDigits = newState.genDigits,
            includeSymbols = newState.genSymbols
        )
        _uiState.value = newState.copy(generatedPassword = generated)
    }

    fun regeneratePassword() {
        val state = _uiState.value
        val generated = cryptoManager.generateStrongPassword(
            length = state.genLength,
            includeUppercase = state.genUppercase,
            includeLowercase = state.genLowercase,
            includeDigits = state.genDigits,
            includeSymbols = state.genSymbols
        )
        _uiState.value = _uiState.value.copy(generatedPassword = generated)
    }

    fun applyGeneratedPassword() {
        val generated = _uiState.value.generatedPassword
        _uiState.value = _uiState.value.copy(
            password = generated,
            passwordStrength = cryptoManager.checkPasswordStrength(generated),
            showPasswordGenerator = false
        )
    }

    // ─── Save ─────────────────────────────────────────────────────────────────

    fun save() {
        val state = _uiState.value
        var hasError = false

        if (state.selectedWebApp == null) {
            _uiState.value = _uiState.value.copy(webAppError = "Vui lòng chọn Web/App")
            hasError = true
        }
        if (state.username.isBlank()) {
            _uiState.value = _uiState.value.copy(usernameError = "Username không được để trống")
            hasError = true
        }
        if (state.password.isBlank()) {
            _uiState.value = _uiState.value.copy(passwordError = "Password không được để trống")
            hasError = true
        }
        if (hasError) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                if (editAccountId != null) {
                    val existing = accountRepository.getAccountById(editAccountId)!!
                    accountRepository.updateAccount(
                        account = existing.copy(
                            webAppId = state.selectedWebApp!!.id,
                            title = state.title.ifBlank { state.username },
                            username = state.username,
                            notes = state.notes
                        ),
                        newPassword = state.password
                    )
                } else {
                    accountRepository.insertAccount(
                        webAppId = state.selectedWebApp!!.id,
                        title = state.title.ifBlank { state.username },
                        username = state.username,
                        password = state.password,
                        notes = state.notes
                    )
                }
                _uiState.value = _uiState.value.copy(isLoading = false, isSaved = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Lỗi lưu: ${e.message}"
                )
            }
        }
    }
}