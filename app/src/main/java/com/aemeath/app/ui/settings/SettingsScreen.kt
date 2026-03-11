package com.aemeath.app.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aemeath.app.R
import com.aemeath.app.navigation.Screen
import com.aemeath.app.ui.account.SquareIconButton
import com.aemeath.app.ui.theme.Primary
import com.aemeath.app.ui.theme.PrimaryLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    // Collect states from ViewModel
    val theme by viewModel.theme.collectAsStateWithLifecycle(initialValue = "system")
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    // Local UI states
    var showThemeDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showBiometricAuthDialog by remember { mutableStateOf(false) }

    // ─── DIALOGS ─────────────────────────────────────────────────────────────

    // 1. Theme Dialog
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Chủ đề") },
            text = {
                Column {
                    listOf(
                        "light" to "☀️  Sáng",
                        "dark" to "🌙  Tối",
                        "system" to "📱  Theo hệ thống"
                    ).forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setTheme(value); showThemeDialog = false }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = theme == value,
                                onClick = { viewModel.setTheme(value); showThemeDialog = false }
                            )
                            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showThemeDialog = false }) { Text("Đóng") } }
        )
    }

    // 2. Biometric Password Confirmation Dialog
    if (showBiometricAuthDialog) {
        var password by remember { mutableStateOf("") }
        var showPassword by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = {
                showBiometricAuthDialog = false
                viewModel.clearError()
            },
            icon = { Icon(Icons.Default.Fingerprint, null, tint = Primary) },
            title = { Text("Xác nhận mật khẩu") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Vui lòng nhập mật khẩu chính để kích hoạt tính năng đăng nhập bằng vân tay.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            viewModel.clearError()
                        },
                        label = { Text("Mật khẩu chính") },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        isError = error != null,
                        supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.enableBiometric(password) {
                            showBiometricAuthDialog = false // Close on success
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Xác nhận") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBiometricAuthDialog = false
                    viewModel.clearError()
                }) { Text("Hủy") }
            }
        )
    }

    // 3. About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("Về Aemeath") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🔐 Aemeath Password Manager", fontWeight = FontWeight.Bold)
                    Text("Phiên bản: 1.0.0", style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                    Text("Công nghệ sử dụng:", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
                    listOf(
                        "Kotlin + Jetpack Compose (Material 3)",
                        "Room Database (SQLite)",
                        "AES-256-GCM Encryption",
                        "PBKDF2 Key Derivation",
                        "ECDH Key Exchange (LAN Sync)",
                        "Hilt Dependency Injection",
                        "NanoHTTPD (Local Server)"
                    ).forEach {
                        Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                    Text(
                        "Dữ liệu được lưu hoàn toàn offline trên thiết bị. Không có server trung gian.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // --- PHẦN THÊM MỚI ---
                    HorizontalDivider()
                    Text(
                        text = "Created by @hieuj2k4.",
                        style = MaterialTheme.typography.labelSmall, // Font nhỏ hơn một chút cho tinh tế
                        color = MaterialTheme.colorScheme.primary, // Đổi màu để làm nổi bật tác giả
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End // Căn phải cho chuyên nghiệp
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showAboutDialog = false }) { Text("Đóng") } }
        )
    }

    // ─── MAIN SCAFFOLD ───────────────────────────────────────────────────────

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(20.dp),
                        ambientColor = Color.Black.copy(0.4f),
                        spotColor = Color.Black
                    )
                    .clip(RoundedCornerShape(20.dp))
            ) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Cài đặt",
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        SquareIconButton(
                            icon = Icons.Default.ArrowBack,
                            onClick = { navController.popBackStack() }
                        )
                    },
                    actions = {
                        Spacer(modifier = Modifier.width(56.dp))
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    )
                )
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.popBackStack() },
                    icon = { Icon(Icons.Filled.Home, null) },
                    label = { Text("Trang chủ") }
                )
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = { Icon(Icons.Filled.Settings, null) },
                    label = { Text("Cài đặt") }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {

            // ── Profile Header ───────────────────────────────────────────────
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {


                        Image(
                            painter = painterResource(id = R.drawable.logo),
                            contentDescription = "App Logo",
                            contentScale = ContentScale.Crop, // Giúp ảnh lấp đầy khung
                            modifier = Modifier
                                .size(60.dp) // Kích thước bằng cái khung tím cũ
                                .clip(CircleShape) // Bo góc giống khung cũ
                        )
                        Column {
                            Text("Aemeath", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Password Manager v1.0.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // ── Giao diện ────────────────────────────────────────────────────
            item { SettingsSectionLabel("🎨  Giao diện") }

            item {
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Default.Palette,
                        title = "Chủ đề",
                        subtitle = when (theme) {
                            "light" -> "☀️ Sáng"
                            "dark" -> "🌙 Tối"
                            else -> "📱 Theo hệ thống"
                        },
                        onClick = { showThemeDialog = true }
                    )
                }
            }

            // ── Bảo mật ──────────────────────────────────────────────────────
            item { Spacer(Modifier.height(4.dp)); SettingsSectionLabel("🔐  Bảo mật") }

            item {
                SettingsCard {
                    // Item Đăng nhập bằng vân tay (MỚI)
                    SettingsRow(
                        icon = Icons.Default.Fingerprint,
                        title = "Đăng nhập bằng vân tay",
                        subtitle = if (isBiometricEnabled) "Đang bật" else "Đang tắt",
                        trailingContent = {
                            Switch(
                                checked = isBiometricEnabled,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) {
                                        showBiometricAuthDialog = true
                                    } else {
                                        viewModel.disableBiometric()
                                    }
                                }
                            )
                        },
                        onClick = {
                            if (!isBiometricEnabled) showBiometricAuthDialog = true
                            else viewModel.disableBiometric()
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    SettingsRow(
                        icon = Icons.Default.LockReset,
                        title = "Đổi mật khẩu chính",
                        subtitle = "Thay đổi Master Password và mã hoá lại dữ liệu",
                        onClick = { navController.navigate(Screen.ChangePassword.route) }
                    )
                }
            }

            // ── Đồng bộ ──────────────────────────────────────────────────────
            item { Spacer(Modifier.height(4.dp)); SettingsSectionLabel("📡  Đồng bộ") }

            item {
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Default.Wifi,
                        title = "LAN Sync",
                        subtitle = "Đồng bộ an toàn qua Wi-Fi nội bộ",
                        onClick = { navController.navigate(Screen.LanSync.route) }
                    )
                }
            }

            // ── Dữ liệu ──────────────────────────────────────────────────────
            item { Spacer(Modifier.height(4.dp)); SettingsSectionLabel("💾  Dữ liệu") }

            item {
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Default.CloudDownload,
                        title = "Backup & Restore",
                        subtitle = "Xuất / Nhập dữ liệu (.aem, CSV, Google CSV)",
                        onClick = { navController.navigate(Screen.Backup.route) }
                    )
                }
            }

            // ── Thông tin ─────────────────────────────────────────────────────
            item { Spacer(Modifier.height(4.dp)); SettingsSectionLabel("ℹ️  Thông tin") }

            item {
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Default.Info,
                        title = "Về ứng dụng",
                        subtitle = "Phiên bản, công nghệ, chính sách bảo mật",
                        onClick = { showAboutDialog = true },
                        showChevron = true
                    )
                }
            }

            // ── Security badge ────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.06f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("🔒", fontSize = 24.sp)
                        Text("Dữ liệu được bảo vệ", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "AES-256-GCM · Offline only · No cloud",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // --- DÒNG COPYRIGHT NẰM TRONG CARD ---
                        Spacer(Modifier.height(8.dp)) // Tạo khoảng cách nhỏ
                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth(0.5f), // Chỉ kẻ một đường ngắn ở giữa
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Copyright © 2026 hieuj2k4",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), // Nhỏ hơn một chút
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

// ─── HELPER COMPOSABLES ──────────────────────────────────────────────────────

@Composable
fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = Primary,
        modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
    )
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column { content() }
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String? = null,
    showChevron: Boolean = true,
    trailingContent: (@Composable () -> Unit)? = null, // Hỗ trợ Switch hoặc nội dung khác
    onClick: () -> Unit
) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Primary, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    badge?.let {
                        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(
                                it,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (trailingContent != null) {
                trailingContent()
            } else if (showChevron) {
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}