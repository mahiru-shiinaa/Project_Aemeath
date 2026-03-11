package com.aemeath.app.ui.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.aemeath.app.ui.theme.Primary
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    navController: NavController,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessages() }
    }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessages() }
    }

    // ── File pickers ──────────────────────────────────────────────────────────
    val exportEncryptedLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? -> uri?.let { viewModel.onExportUriReceived(it, ExportType.ENCRYPTED) } }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? -> uri?.let { viewModel.onExportUriReceived(it, ExportType.CSV) } }

    val exportGoogleCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? -> uri?.let { viewModel.onExportUriReceived(it, ExportType.GOOGLE_CSV) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.prepareImport(it) } }

    // ── Dialog: Nhập mật khẩu backup khi EXPORT .aem ─────────────────────────
    if (uiState.showExportPasswordDialog) {
        BackupPasswordDialog(
            title = "Đặt mật khẩu backup",
            subtitle = "Mật khẩu này dùng để bảo vệ file .aem.\nKhác với mật khẩu chính — hãy lưu lại cẩn thận!",
            confirmLabel = "Xuất file",
            isExport = true,
            onConfirm = { password -> viewModel.confirmEncryptedExport(password) },
            onDismiss = { viewModel.dismissExportPasswordDialog() }
        )
    }

    // ── Dialog: Nhập mật khẩu backup khi IMPORT .aem V2 ──────────────────────
    if (uiState.showImportPasswordDialog) {
        BackupPasswordDialog(
            title = "Nhập mật khẩu backup",
            subtitle = "Nhập mật khẩu backup khi xuất file .aem này.",
            confirmLabel = "Xác nhận",
            isExport = false,
            onConfirm = { password -> viewModel.confirmImportPassword(password) },
            onDismiss = { viewModel.dismissImportPasswordDialog() }
        )
    }

    // ── CSV Warning dialog ────────────────────────────────────────────────────
    if (uiState.showExportWarning) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissExportWarning() },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Cảnh báo bảo mật") },
            text = {
                Text(
                    "File CSV chứa mật khẩu dạng văn bản thuần (plaintext).\n\n" +
                            "Chỉ dùng để chuyển sang trình quản lý mật khẩu khác. " +
                            "Không chia sẻ file này với bất kỳ ai.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmCsvExport() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Tôi hiểu, tiếp tục") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissExportWarning() }) { Text("Hủy") }
            }
        )
    }

    // ── Import confirm dialog ─────────────────────────────────────────────────
    if (uiState.showImportConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelImport() },
            title = { Text("Xác nhận nhập dữ liệu") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Tìm thấy ${uiState.pendingImportCount} tài khoản sẽ được nhập.")
                    Text("Chọn cách nhập:", fontWeight = FontWeight.Medium)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = uiState.pendingImportMode == ImportMode.MERGE,
                            onClick = { viewModel.setImportMode(ImportMode.MERGE) }
                        )
                        Column(modifier = Modifier.padding(start = 4.dp)) {
                            Text("Gộp (Merge)", fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.bodyMedium)
                            Text("Giữ dữ liệu hiện tại, thêm mới (bỏ qua trùng lặp)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = uiState.pendingImportMode == ImportMode.OVERWRITE,
                            onClick = { viewModel.setImportMode(ImportMode.OVERWRITE) }
                        )
                        Column(modifier = Modifier.padding(start = 4.dp)) {
                            Text("Ghi đè (Overwrite)",
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error)
                            Text("Xóa hết dữ liệu cũ rồi nhập",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmImport() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.pendingImportMode == ImportMode.OVERWRITE)
                            MaterialTheme.colorScheme.error else Primary
                    )
                ) { Text("Nhập") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelImport() }) { Text("Hủy") }
            }
        )
    }

    // ── Main UI ───────────────────────────────────────────────────────────────
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Backup & Restore", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Quay lại")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = Primary)
                    Text("Đang xử lý...", style = MaterialTheme.typography.bodyMedium)
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ─── Xuất ─────────────────────────────────────────────────────────
            SectionHeader(icon = Icons.Default.Upload, title = "Xuất dữ liệu")

            BackupOptionCard(
                icon = "🔐",
                title = "Xuất bản sao mã hoá (.aem)",
                subtitle = "Bảo vệ bằng mật khẩu riêng — dùng được dù đổi mật khẩu chính",
                badge = "Khuyến nghị",
                badgeColor = Primary,
                onClick = {
                    exportEncryptedLauncher.launch("aemeath_backup_$timestamp.aem")
                }
            )

            BackupOptionCard(
                icon = "📄",
                title = "Xuất CSV",
                subtitle = "Dạng bảng tính — mở được bằng Excel, Google Sheets",
                badge = "Plaintext",
                badgeColor = MaterialTheme.colorScheme.error,
                onClick = { exportCsvLauncher.launch("aemeath_export_$timestamp.csv") }
            )

            BackupOptionCard(
                icon = "🌐",
                title = "Xuất Google CSV",
                subtitle = "Tương thích nhập vào Chrome / Google Password Manager",
                badge = "Plaintext",
                badgeColor = MaterialTheme.colorScheme.error,
                onClick = { exportGoogleCsvLauncher.launch("aemeath_google_$timestamp.csv") }
            )

            Spacer(Modifier.height(4.dp))

            // ─── Nhập ─────────────────────────────────────────────────────────
            SectionHeader(icon = Icons.Default.Download, title = "Nhập dữ liệu")

            BackupOptionCard(
                icon = "📥",
                title = "Nhập từ file",
                subtitle = "Hỗ trợ: .aem (Aemeath), .csv (Standard & Google)",
                onClick = { importLauncher.launch("*/*") }
            )

            // Info card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("📋 Định dạng hỗ trợ:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold)
                    Text("• .aem  — Mã hoá AES-256, mật khẩu backup riêng",
                        style = MaterialTheme.typography.bodySmall)
                    Text("• CSV   — webapp, title, username, password, notes",
                        style = MaterialTheme.typography.bodySmall)
                    Text("• Google CSV — name, url, username, password",
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    Text("File .aem dùng mật khẩu backup riêng → không bị ảnh hưởng khi đổi mật khẩu chính.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Primary)
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

// ─── Dialog nhập mật khẩu backup (dùng chung cho export & import) ────────────
@Composable
fun BackupPasswordDialog(
    title: String,
    subtitle: String,
    confirmLabel: String,
    isExport: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                if (isExport) Icons.Default.Lock else Icons.Default.LockOpen,
                null,
                tint = Primary
            )
        },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                // Password input
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = { Text(if (isExport) "Mật khẩu backup" else "Mật khẩu backup của file") },
                    visualTransformation = if (showPassword) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                null
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = if (isExport) ImeAction.Next else ImeAction.Done
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    isError = error != null
                )

                // Confirm field chỉ hiện khi export
                if (isExport) {
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; error = null },
                        label = { Text("Nhập lại mật khẩu") },
                        visualTransformation = if (showPassword) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        isError = error != null
                    )
                }

                // Error
                AnimatedVisibility(visible = error != null) {
                    Text(
                        text = error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Cảnh báo khi export
                if (isExport) {
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(Icons.Default.Warning, null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp))
                            Text(
                                "Không thể khôi phục nếu quên mật khẩu này!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        password.isBlank() -> error = "Vui lòng nhập mật khẩu"
                        isExport && password.length < 6 -> error = "Mật khẩu tối thiểu 6 ký tự"
                        isExport && password != confirmPassword -> error = "Mật khẩu không khớp"
                        else -> onConfirm(password)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                enabled = password.isNotBlank()
            ) {
                Text(confirmLabel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}

// ─── Helper composables ───────────────────────────────────────────────────────

@Composable
fun SectionHeader(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Icon(icon, null, tint = Primary, modifier = Modifier.size(20.dp))
        Text(title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Primary)
    }
}

@Composable
fun BackupOptionCard(
    icon: String,
    title: String,
    subtitle: String,
    badge: String? = null,
    badgeColor: Color = Primary,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(icon, fontSize = 28.sp)

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    badge?.let {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = badgeColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = it,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeColor,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}