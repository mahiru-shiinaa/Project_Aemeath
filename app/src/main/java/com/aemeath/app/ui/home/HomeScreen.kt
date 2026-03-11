package com.aemeath.app.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aemeath.app.navigation.Screen
import com.aemeath.app.ui.theme.*
import androidx.compose.ui.res.painterResource // Nhớ thêm dòng này nếu báo đỏ
import androidx.compose.ui.semantics.Role.Companion.Image
import com.aemeath.app.R // Nhớ import R của project

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // FIX 1: Lấy searchText riêng để input mượt mà
    val searchText by viewModel.searchText.collectAsStateWithLifecycle()

    val showDeleteConfirm by viewModel.showDeleteConfirm.collectAsStateWithLifecycle()
    var showSortMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    // Confirm xóa hàng loạt
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text("Xóa ${uiState.selectedIds.size} ứng dụng?") },
            text = { Text("Tất cả tài khoản bên trong cũng sẽ bị xóa vĩnh viễn.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmDeleteSelected() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Xóa") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) { Text("Hủy") }
            }
        )
    }

    if (showThemeDialog) {
        ThemeDialog(
            onDismiss = { showThemeDialog = false },
            onThemeSelected = { theme -> viewModel.setTheme(theme); showThemeDialog = false }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.isMultiSelectMode) {
                        Text("${uiState.selectedIds.size} đã chọn", fontWeight = FontWeight.SemiBold)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.logo),
                                contentDescription = "App Logo",
                                contentScale = ContentScale.Crop, // Giúp ảnh lấp đầy khung
                                modifier = Modifier
                                    .size(32.dp) // Kích thước bằng cái khung tím cũ
                                    .clip(RoundedCornerShape(9.dp)) // Bo góc giống khung cũ
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Aemeath", fontWeight = FontWeight.Bold, fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                },
                actions = {
                    if (uiState.isMultiSelectMode) {
                        IconButton(onClick = { viewModel.selectAll() }) {
                            Icon(Icons.Default.SelectAll, "Chọn tất cả",
                                tint = MaterialTheme.colorScheme.onBackground)
                        }
                        IconButton(onClick = { viewModel.requestDeleteSelected() }) {
                            Icon(Icons.Default.Delete, "Xóa", tint = MaterialTheme.colorScheme.error)
                        }
                        IconButton(onClick = { viewModel.exitMultiSelect() }) {
                            Icon(Icons.Default.Close, "Thoát",
                                tint = MaterialTheme.colorScheme.onBackground)
                        }
                    } else {
                        // Sort button
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, "Sắp xếp",
                                tint = MaterialTheme.colorScheme.onBackground)
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            SortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = {
                                        Text(when (order) {
                                            SortOrder.AZ -> "A → Z"
                                            SortOrder.ZA -> "Z → A"
                                            SortOrder.NEWEST -> "Mới nhất"
                                            SortOrder.RECENTLY_EDITED -> "Sửa gần nhất"
                                        })
                                    },
                                    leadingIcon = {
                                        if (uiState.sortOrder == order)
                                            Icon(Icons.Default.Check, null, tint = Primary)
                                    },
                                    onClick = { viewModel.setSortOrder(order); showSortMenu = false }
                                )
                            }
                        }

                        // More menu
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, "Thêm",
                                tint = MaterialTheme.colorScheme.onBackground)
                        }
                        DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Chọn nhiều") },
                                leadingIcon = { Icon(Icons.Default.CheckBox, null) },
                                onClick = { viewModel.toggleMultiSelect(); showMoreMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text(if (uiState.viewMode == ViewMode.LIST) "Xem dạng lưới" else "Xem dạng danh sách") },
                                leadingIcon = { Icon(if (uiState.viewMode == ViewMode.LIST) Icons.Default.GridView else Icons.Default.ViewList, null) },
                                onClick = {
                                    viewModel.setViewMode(if (uiState.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST)
                                    showMoreMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Chủ đề") },
                                leadingIcon = { Icon(Icons.Default.Palette, null) },
                                onClick = { showThemeDialog = true; showMoreMenu = false }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Cài đặt") },
                                leadingIcon = { Icon(Icons.Default.Settings, null) },
                                onClick = { navController.navigate(Screen.Settings.route); showMoreMenu = false }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (!uiState.isMultiSelectMode) {
                FloatingActionButton(
                    onClick = { navController.navigate(Screen.AddAccount.createRoute()) },
                    containerColor = Primary,
                    contentColor = OnPrimary,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.size(60.dp)
                ) {
                    Icon(Icons.Rounded.Add, "Thêm tài khoản", modifier = Modifier.size(28.dp))
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                modifier = Modifier.border(
                    1.dp,
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    RoundedCornerShape(0.dp)
                )
            ) {
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = { Icon(Icons.Filled.Home, null) },
                    label = { Text("Trang chủ") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Primary,
                        selectedTextColor = Primary,
                        indicatorColor = Primary.copy(alpha = 0.15f),
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate(Screen.Settings.route) },
                    icon = { Icon(Icons.Outlined.Settings, null) },
                    label = { Text("Cài đặt") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Primary,
                        selectedTextColor = Primary,
                        indicatorColor = Primary.copy(alpha = 0.15f),
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 1. Search Bar
            HomeSearchBar(
                query = searchText, // FIX 1: Dùng searchText trực tiếp
                onQueryChange = viewModel::onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            )

            // 2. Stats
            DashboardStats(
                totalAccounts = uiState.totalAccounts,
                totalWebApps = uiState.totalWebApps,
                securityScore = uiState.securityScore,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // 3. Section header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TẤT CẢ ỨNG DỤNG",
                    fontSize = 14.sp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                if (!uiState.isMultiSelectMode) {
                    Text(
                        text = "Chọn nhiều",
                        fontSize = 14.sp,
                        style = MaterialTheme.typography.labelMedium,
                        color = Primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { viewModel.toggleMultiSelect() }
                    )
                }
            }

            // 4. Content List/Grid
            Box(modifier = Modifier.fillMaxSize()) {
                if (uiState.isLoading) {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Primary)
                    }
                } else if (uiState.webApps.isEmpty()) {
                    EmptyState(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 60.dp),
                        isSearching = searchText.isNotEmpty()
                    )
                } else {
                    // FIX 2: Phân chia ViewMode
                    if (uiState.viewMode == ViewMode.LIST) {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 90.dp),
                        ) {
                            items(uiState.webApps, key = { it.webApp.id }) { item ->
                                WebAppListItem(
                                    item = item,
                                    isSelected = uiState.selectedIds.contains(item.webApp.id),
                                    isMultiSelectMode = uiState.isMultiSelectMode,
                                    onClick = {
                                        if (uiState.isMultiSelectMode) viewModel.toggleSelectItem(item.webApp.id)
                                        else navController.navigate(Screen.AccountList.createRoute(item.webApp.id))
                                    },
                                    onLongClick = {
                                        if (!uiState.isMultiSelectMode) {
                                            viewModel.toggleMultiSelect()
                                            viewModel.toggleSelectItem(item.webApp.id)
                                        }
                                    },
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                        .animateItemPlacement()
                                )
                            }
                        }
                    } else {
                        // FIX 2: Implement Grid View
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(bottom = 90.dp)
                        ) {
                            items(uiState.webApps, key = { it.webApp.id }) { item ->
                                WebAppGridItem(
                                    item = item,
                                    isSelected = uiState.selectedIds.contains(item.webApp.id),
                                    isMultiSelectMode = uiState.isMultiSelectMode,
                                    onClick = {
                                        if (uiState.isMultiSelectMode) viewModel.toggleSelectItem(item.webApp.id)
                                        else navController.navigate(Screen.AccountList.createRoute(item.webApp.id))
                                    },
                                    onLongClick = {
                                        if (!uiState.isMultiSelectMode) {
                                            viewModel.toggleMultiSelect()
                                            viewModel.toggleSelectItem(item.webApp.id)
                                        }
                                    },
                                    modifier = Modifier.animateItemPlacement()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ... Giữ nguyên HomeSearchBar, DashboardStats, StatsCard, WebAppListItem, EmptyState, ThemeDialog ...

// FIX 2: Thêm Composable cho Grid Item
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WebAppGridItem(
    item: WebAppWithCount,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (isSelected)
            Primary.copy(alpha = 0.08f)
        else MaterialTheme.colorScheme.surface,
        label = "card_color"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .softCard()
            .then(
                if (isSelected)
                    Modifier.border(1.5.dp, Primary.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                else Modifier
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),

        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(0.dp),

    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected && isMultiSelectMode) {
                    Icon(Icons.Default.Check, null, tint = Primary, modifier = Modifier.size(24.dp))
                } else {
                    Text(item.webApp.iconEmoji, fontSize = 26.sp)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Name
            Text(
                text = item.webApp.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(4.dp))

            // Count
            Text(
                text = "${item.accountCount} tài khoản",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ... (Phần còn lại của file giữ nguyên)
@Composable
fun HomeSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) Primary else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(200),
        label = "border"
    )

    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.5.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = if (isFocused) Primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(Primary),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { isFocused = it.isFocused },
                decorationBox = { innerTextField ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                "Tìm tài khoản, website...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardStats(
    totalAccounts: Int,
    totalWebApps: Int,
    securityScore: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatsCard(
            title = totalAccounts.toString(),
            subtitle = "Tài khoản",
            modifier = Modifier.weight(1f)
        )
        StatsCard(
            title = totalWebApps.toString(),
            subtitle = "Web/App",
            modifier = Modifier.weight(1f)
        )
        StatsCard(
            title = "$securityScore%",
            subtitle = "An toàn",
            titleColor = if (securityScore > 80) StrengthGood else if (securityScore > 50) StrengthFair else StrengthWeak,
            containerColor = if (securityScore > 80)
                StrengthGood.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun StatsCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    containerColor: Color = MaterialTheme.colorScheme.surface
) {
    Card(
        modifier = modifier
            .height(76.dp)
            .softCard(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = titleColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun getBrandColor(name: String): Color {
    val presetColors = listOf(
        Color(0xFF1DB954), // Green
        Color(0xFF1877F2), // Blue
        Color(0xFFF44336), // Red
        Color(0xFFFF9800), // Orange
        Color(0xFF9C27B0), // Purple
        Color(0xFF00BCD4), // Cyan
        Color(0xFFE91E63), // Pink
        Color(0xFF673AB7)  // Deep Purple
    )
    // Dùng hashCode của tên để chọn index cố định trong danh sách màu
    val index = Math.abs(name.hashCode()) % presetColors.size
    return presetColors[index]
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WebAppListItem(
    item: WebAppWithCount,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
// Tự động gán màu dựa trên tên, không cần can thiệp data
    val brandColor = remember(item.webApp.name) { getBrandColor(item.webApp.name) }

    val containerColor by animateColorAsState(
        targetValue = if (isSelected)
            Primary.copy(alpha = 0.08f)
        else MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        label = "card_color"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
            .softCard()
            .then(
                if (isSelected)
                    Modifier.border(1.5.dp, Primary.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                else Modifier
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(0.dp),

    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),

            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Icon container
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(brandColor.copy(alpha = 0.1f))
                    .border(
                        width = 1.dp,
                        color = brandColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(14.dp)
                    ),

                contentAlignment = Alignment.Center
            ) {
                if (isSelected && isMultiSelectMode) {
                    Icon(Icons.Default.Check, null, tint = Primary, modifier = Modifier.size(22.dp))
                } else {
                    Text(item.webApp.iconEmoji, fontSize = 22.sp)
                }
            }

            // Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = item.webApp.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${item.accountCount} tài khoản",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Badge count
            if (item.accountCount > 0 && !isMultiSelectMode) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Primary.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.accountCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (!isMultiSelectMode) {
                Icon(
                    Icons.Default.ChevronRight,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyState(modifier: Modifier = Modifier, isSearching: Boolean = false) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(if (isSearching) "🔍" else "🔐", fontSize = 52.sp)
        Text(
            if (isSearching) "Không tìm thấy kết quả" else "Chưa có tài khoản nào",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            if (isSearching) "Thử tìm kiếm với từ khóa khác" else "Nhấn nút + để thêm tài khoản đầu tiên",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ThemeDialog(onDismiss: () -> Unit, onThemeSelected: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chủ đề", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                listOf("light" to "🌤️ Sáng", "dark" to "🌙 Tối", "system" to " 📱Theo hệ thống")
                    .forEach { (value, label) ->
                        TextButton(
                            onClick = { onThemeSelected(value) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(label, modifier = Modifier.fillMaxWidth())
                        }
                    }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}