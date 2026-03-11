package com.aemeath.app.ui.account

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aemeath.app.data.db.entity.AccountEntity
import com.aemeath.app.data.db.entity.WebAppEntity
import com.aemeath.app.data.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Collections
import javax.inject.Inject

data class AccountListUiState(
    val webApp: WebAppEntity? = null,
    val accounts: List<AccountEntity> = emptyList(),
    val isLoading: Boolean = true,
    val revealedPasswordIds: Set<Long> = emptySet(),
    val selectedIds: Set<Long> = emptySet(),
    val isMultiSelectMode: Boolean = false,
    val isReorderMode: Boolean = false, // Chế độ sắp xếp
    val showDeleteConfirmId: Long? = null,
    val showDeleteMultiConfirm: Boolean = false,
    val snackbarMessage: String? = null,
    val deletedAccount: AccountEntity? = null
)

@HiltViewModel
class AccountListViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val webAppId: Long = savedStateHandle.get<Long>("webAppId") ?: -1L
    private val _uiState = MutableStateFlow(AccountListUiState())
    val uiState: StateFlow<AccountListUiState> = _uiState.asStateFlow()
    private var clipboardClearJob: Job? = null

    // Dùng để giữ danh sách tạm thời khi đang kéo thả
    private var tempOrderedList: MutableList<AccountEntity> = mutableListOf()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val webApp = accountRepository.getWebAppById(webAppId)
            _uiState.value = _uiState.value.copy(webApp = webApp)

            accountRepository.getAccountsByWebApp(webAppId)
                .collect { accounts ->
                    // Chỉ update nếu không đang trong chế độ sắp xếp (để tránh giật khi kéo)
                    if (!_uiState.value.isReorderMode) {
                        _uiState.value = _uiState.value.copy(accounts = accounts, isLoading = false)
                        tempOrderedList = accounts.toMutableList()
                    }
                }
        }
    }

    // ─── Drag & Drop Logic ──────────────────────────────────────────────────

    fun toggleReorderMode() {
        val current = _uiState.value.isReorderMode
        if (current) {
            // Đang tắt chế độ sắp xếp -> Lưu vào DB
            saveOrder()
        } else {
            // Bắt đầu sắp xếp -> Reset multi select
            exitMultiSelect()
        }
        _uiState.value = _uiState.value.copy(isReorderMode = !current)
    }

    fun onItemMoved(fromIndex: Int, toIndex: Int) {
        // Update list tạm thời trong RAM để UI mượt
        val newList = _uiState.value.accounts.toMutableList()
        if (fromIndex in newList.indices && toIndex in newList.indices) {
            Collections.swap(newList, fromIndex, toIndex)
            _uiState.value = _uiState.value.copy(accounts = newList)
            tempOrderedList = newList
        }
    }

    private fun saveOrder() {
        viewModelScope.launch {
            // Cập nhật field position cho từng item
            val updates = tempOrderedList.mapIndexed { index, account ->
                account.copy(position = index)
            }
            // Gọi update batch trong DAO (bạn cần thêm hàm này trong DAO/Repo)
            // Ở đây tôi giả lập loop update, tốt nhất là thêm hàm updateAll trong DAO
            updates.forEach { accountRepository.updateAccountDirect(it) }

            showSnackbar("Đã lưu thứ tự")
        }
    }

    // ─── Existing Logic ─────────────────────────────────────────────────────

    fun togglePasswordVisibility(accountId: Long) {
        val current = _uiState.value.revealedPasswordIds.toMutableSet()
        if (current.contains(accountId)) current.remove(accountId) else current.add(accountId)
        _uiState.value = _uiState.value.copy(revealedPasswordIds = current)
    }

    fun getDecryptedPassword(encryptedPassword: String): String {
        return try { accountRepository.decryptPassword(encryptedPassword) } catch (e: Exception) { "Error" }
    }

    fun copyUsername(username: String) {
        copyToClipboard("Username", username)
        showSnackbar("Đã sao chép username")
    }

    fun copyPassword(encryptedPassword: String) {
        val decrypted = getDecryptedPassword(encryptedPassword)
        copyToClipboard("Password", decrypted)
        showSnackbar("Đã sao chép mật khẩu · Tự xóa sau 30s")
        scheduleClipboardClear()
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }

    private fun scheduleClipboardClear() {
        clipboardClearJob?.cancel()
        clipboardClearJob = viewModelScope.launch {
            delay(30_000L)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("", "")
            clipboard.setPrimaryClip(clip)
        }
    }

    fun requestDeleteSingle(accountId: Long) { _uiState.value = _uiState.value.copy(showDeleteConfirmId = accountId) }

    fun confirmDeleteSingle() {
        val id = _uiState.value.showDeleteConfirmId ?: return
        viewModelScope.launch {
            val account = accountRepository.getAccountById(id)
            if (account != null) {
                accountRepository.deleteAccount(account)
                _uiState.value = _uiState.value.copy(showDeleteConfirmId = null, deletedAccount = account, snackbarMessage = "Đã xóa tài khoản")
            }
        }
    }

    fun undoDelete() {
        val deleted = _uiState.value.deletedAccount ?: return
        viewModelScope.launch {
            accountRepository.insertAccount(deleted.webAppId, deleted.title, deleted.username, accountRepository.decryptPassword(deleted.encryptedPassword), deleted.notes)
            _uiState.value = _uiState.value.copy(deletedAccount = null, snackbarMessage = null)
        }
    }

    fun cancelDeleteSingle() { _uiState.value = _uiState.value.copy(showDeleteConfirmId = null) }

    // Multi-select
    fun toggleMultiSelect() {
        if (_uiState.value.isReorderMode) toggleReorderMode() // Tắt reorder nếu đang bật
        val current = !_uiState.value.isMultiSelectMode
        _uiState.value = _uiState.value.copy(isMultiSelectMode = current, selectedIds = emptySet())
    }

    fun toggleSelectItem(id: Long) {
        val current = _uiState.value.selectedIds.toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        _uiState.value = _uiState.value.copy(selectedIds = current)
    }

    fun requestDeleteMulti() {
        if (_uiState.value.selectedIds.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(showDeleteMultiConfirm = true)
        }
    }

    fun confirmDeleteMulti() {
        viewModelScope.launch {
            _uiState.value.selectedIds.forEach { id -> accountRepository.deleteAccountById(id) }
            _uiState.value = _uiState.value.copy(selectedIds = emptySet(), isMultiSelectMode = false, showDeleteMultiConfirm = false, snackbarMessage = "Đã xóa các tài khoản")
        }
    }

    fun cancelDeleteMulti() { _uiState.value = _uiState.value.copy(showDeleteMultiConfirm = false) }
    fun exitMultiSelect() { _uiState.value = _uiState.value.copy(isMultiSelectMode = false, selectedIds = emptySet()) }
    fun showSnackbar(msg: String) { _uiState.value = _uiState.value.copy(snackbarMessage = msg) }
    fun clearSnackbar() { _uiState.value = _uiState.value.copy(snackbarMessage = null) }

    override fun onCleared() { super.onCleared(); clipboardClearJob?.cancel() }
}