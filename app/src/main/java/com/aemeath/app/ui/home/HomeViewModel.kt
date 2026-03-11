package com.aemeath.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aemeath.app.data.db.entity.WebAppEntity
import com.aemeath.app.data.repository.AccountRepository
import com.aemeath.app.data.repository.PreferencesRepository
import com.aemeath.app.security.CryptoManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortOrder { AZ, ZA, NEWEST, RECENTLY_EDITED }
enum class ViewMode { LIST, GRID }

data class WebAppWithCount(
    val webApp: WebAppEntity,
    val accountCount: Int
)

data class HomeUiState(
    val webApps: List<WebAppWithCount> = emptyList(),
    val totalWebApps: Int = 0,
    val totalAccounts: Int = 0,
    val securityScore: Int = 0,
    val isLoading: Boolean = true,
    val sortOrder: SortOrder = SortOrder.AZ,
    val viewMode: ViewMode = ViewMode.LIST,
    val selectedIds: Set<Long> = emptySet(),
    val isMultiSelectMode: Boolean = false
)

private data class FilterParams(
    val query: String,
    val sortOrder: SortOrder,
    val viewMode: ViewMode,
    val selectedIds: Set<Long>,
    val isMultiSelectMode: Boolean,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val preferencesRepository: PreferencesRepository,
    private val cryptoManager: CryptoManager
) : ViewModel() {

    // FIX: Tách query ra để UI update mượt mà
    private val _searchQuery = MutableStateFlow("")
    val searchText = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.AZ)
    private val _viewMode = MutableStateFlow(ViewMode.LIST)
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _isMultiSelectMode = MutableStateFlow(false)
    private val _showDeleteConfirm = MutableStateFlow(false)

    val showDeleteConfirm: StateFlow<Boolean> = _showDeleteConfirm.asStateFlow()

    private val filterParams: Flow<FilterParams> = combine(
        _searchQuery.debounce(300), // Giữ debounce để không query DB quá nhiều
        _sortOrder,
        _viewMode,
        _selectedIds,
        _isMultiSelectMode
    ) { query, sort, view, selected, multiSelect ->
        FilterParams(query, sort, view, selected, multiSelect)
    }

    val uiState: StateFlow<HomeUiState> = filterParams
        .flatMapLatest { params ->
            // Bước 1: Luôn lấy tất cả WebApps để có dữ liệu nền
            accountRepository.getAllWebAppsFlow().map { webApps ->
                params to webApps
            }
        }
        .mapLatest { (params, allWebApps) ->
            // Bước 2: Xử lý filter và mapping (chạy trong background thread an toàn)

            // Lấy tổng số liệu cho Dashboard
            val totalAllAccounts = accountRepository.getAllAccounts()
            val totalWebApps = allWebApps.size

            val resultList = mutableListOf<WebAppWithCount>()

            // Duyệt qua từng WebApp để kiểm tra Account bên trong
            for (webApp in allWebApps) {
                // Lấy danh sách account của WebApp này
                val accounts = accountRepository.getAccountsByWebAppSync(webApp.id)

                if (params.query.isBlank()) {
                    // Nếu không tìm kiếm -> Thêm hết
                    resultList.add(WebAppWithCount(webApp, accounts.size))
                } else {
                    // Logic tìm kiếm nâng cao:
                    // 1. Tên App có chứa từ khóa?
                    val matchAppName = webApp.name.contains(params.query, ignoreCase = true)

                    // 2. Có Account nào (username hoặc title) chứa từ khóa?
                    val matchAccount = accounts.any { acc ->
                        acc.username.contains(params.query, ignoreCase = true) ||
                                acc.title.contains(params.query, ignoreCase = true)
                    }

                    // Nếu thỏa mãn 1 trong 2 -> Thêm vào kết quả
                    if (matchAppName || matchAccount) {
                        resultList.add(WebAppWithCount(webApp, accounts.size))
                    }
                }
            }

            // Sắp xếp kết quả
            val sorted = when (params.sortOrder) {
                SortOrder.AZ -> resultList.sortedBy { it.webApp.name.lowercase() }
                SortOrder.ZA -> resultList.sortedByDescending { it.webApp.name.lowercase() }
                SortOrder.NEWEST -> resultList.sortedByDescending { it.webApp.createdAt }
                SortOrder.RECENTLY_EDITED -> resultList.sortedByDescending { it.webApp.updatedAt }
            }

            // Tính điểm bảo mật
            var strongPasswords = 0
            if (totalAllAccounts.isNotEmpty()) {
                strongPasswords = totalAllAccounts.count { acc ->
                    try {
                        val plain = accountRepository.decryptPassword(acc.encryptedPassword)
                        val strength = cryptoManager.checkPasswordStrength(plain)
                        strength == CryptoManager.PasswordStrength.GOOD || strength == CryptoManager.PasswordStrength.STRONG
                    } catch (e: Exception) { false }
                }
            }
            val score = if (totalAllAccounts.isNotEmpty()) (strongPasswords.toFloat() / totalAllAccounts.size * 100).toInt() else 100

            HomeUiState(
                webApps = sorted,
                totalWebApps = totalWebApps,
                totalAccounts = totalAllAccounts.size,
                securityScore = score,
                isLoading = false,
                sortOrder = params.sortOrder,
                viewMode = params.viewMode,
                selectedIds = params.selectedIds,
                isMultiSelectMode = params.isMultiSelectMode
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    // ─── Actions ──────────────────────────────────────────────────────────────

    fun onSearchQueryChange(query: String) { _searchQuery.value = query }
    fun setSortOrder(order: SortOrder) { _sortOrder.value = order }

    fun setViewMode(mode: ViewMode) {
        _viewMode.value = mode
        viewModelScope.launch { preferencesRepository.setListViewMode(mode.name.lowercase()) }
    }

    fun toggleMultiSelect() {
        _isMultiSelectMode.value = !_isMultiSelectMode.value
        if (!_isMultiSelectMode.value) _selectedIds.value = emptySet()
    }

    fun toggleSelectItem(id: Long) {
        val current = _selectedIds.value.toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        _selectedIds.value = current
    }

    fun selectAll() { _selectedIds.value = uiState.value.webApps.map { it.webApp.id }.toSet() }

    fun requestDeleteSelected() { if (_selectedIds.value.isNotEmpty()) _showDeleteConfirm.value = true }

    fun confirmDeleteSelected() {
        viewModelScope.launch {
            _selectedIds.value.forEach { id ->
                accountRepository.getWebAppById(id)?.let { accountRepository.deleteWebApp(it) }
            }
            _selectedIds.value = emptySet()
            _isMultiSelectMode.value = false
            _showDeleteConfirm.value = false
        }
    }

    fun cancelDelete() { _showDeleteConfirm.value = false }
    fun exitMultiSelect() { _isMultiSelectMode.value = false; _selectedIds.value = emptySet() }
    fun setTheme(theme: String) { viewModelScope.launch { preferencesRepository.setTheme(theme) } }
}