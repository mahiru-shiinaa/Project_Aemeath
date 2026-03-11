package com.aemeath.app.ui.lansync

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.aemeath.app.navigation.Screen
import com.aemeath.app.ui.account.SquareIconButton
import com.aemeath.app.ui.theme.Primary
import com.aemeath.app.ui.theme.PrimaryLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanSyncScreen(
    navController: NavController,
    viewModel: LanSyncViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current

    // Password confirm dialog
    if (uiState.showPasswordDialog) {
        PasswordConfirmDialog(
            error = uiState.passwordError,
            onConfirm = { viewModel.confirmPasswordAndStart(it) },
            onDismiss = { viewModel.dismissPasswordDialog() }
        )
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .shadow(
                        12.dp,
                        RoundedCornerShape(20.dp),
                        ambientColor = Color.Black.copy(0.4f),
                        spotColor = Color.Black
                    )
                    .clip(RoundedCornerShape(20.dp))
            ) {
                CenterAlignedTopAppBar(
                    title = { Text("LAN Sync", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        SquareIconButton(
                            icon = Icons.Default.ArrowBack,
                            onClick = {
                                viewModel.stopAndReset()
                                navController.popBackStack()
                            }
                        )
                    },
                    actions = {
                        if (uiState.step !in listOf(LanSyncStep.IDLE, LanSyncStep.ERROR, LanSyncStep.SUCCESS)) {
                            // Dùng SquareIconButton nhưng truyền thêm màu đỏ
                            SquareIconButton(
                                icon = Icons.Default.Close,
                                onClick = { viewModel.stopAndReset() },
                                tint = MaterialTheme.colorScheme.error // Dấu X sẽ có màu đỏ
                            )
                        } else {
                            Spacer(modifier = Modifier.width(56.dp))
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    )
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (uiState.step) {
                LanSyncStep.IDLE -> IdleContent(onStart = { viewModel.requestStart() })
                LanSyncStep.STARTING -> LoadingContent("Đang khởi động server...")
                LanSyncStep.WAITING -> WaitingContent(
                    uiState = uiState,
                    onCopyUrl = { clipboard.setText(AnnotatedString(uiState.serverUrl)) },
                    onRefresh = { viewModel.refreshSession() },
                    onScanQr = { navController.navigate(Screen.QRScanner.route) }
                )
                LanSyncStep.CONNECTED -> ConnectedContent()
                LanSyncStep.VERIFYING -> VerifyContent(
                    code = uiState.formattedCode,
                    onConfirm = { viewModel.confirmVerification() },
                    onReject = { viewModel.rejectVerification() }
                )
                LanSyncStep.CONFIRMED -> LoadingContent("Đang gửi dữ liệu sang laptop...")
                LanSyncStep.SYNCING -> SyncingContent(uiState = uiState)
                LanSyncStep.SUCCESS -> SuccessContent(
                    count = uiState.syncedAccountCount,
                    onDone = { viewModel.stopAndReset(); navController.popBackStack() }
                )
                LanSyncStep.ERROR -> ErrorContent(
                    message = uiState.errorMessage,
                    onRetry = { viewModel.requestStart() },
                    onBack = { viewModel.stopAndReset(); navController.popBackStack() }
                )
            }

            if (uiState.step == LanSyncStep.WAITING || uiState.step == LanSyncStep.VERIFYING) {
                SecurityBadge()
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ─── IDLE ─────────────────────────────────────────────────────────────────────

@Composable
fun IdleContent(onStart: () -> Unit) {
    Text("📡", fontSize = 64.sp)
    Text("Kết nối máy tính", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

    Spacer(Modifier.height(8.dp))

    // Card giải thích tính năng thay cho RadioButton
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Info, null, tint = Primary, modifier = Modifier.size(20.dp).padding(top = 2.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    "Tính năng này biến điện thoại của bạn thành một máy chủ cục bộ tạm thời.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                "Bạn có thể xem và sao chép mật khẩu trực tiếp trên trình duyệt máy tính mà không cần cài đặt thêm phần mềm.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Wifi, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Hoạt động qua Wi-Fi nội bộ (LAN)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    Button(
        onClick = onStart,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Primary)
    ) {
        Icon(Icons.Default.LaptopMac, null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Bắt đầu phiên kết nối", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

// ─── WAITING ──────────────────────────────────────────────────────────────────

@Composable
fun WaitingContent(
    uiState: LanSyncUiState,
    onCopyUrl: () -> Unit,
    onRefresh: () -> Unit,
    onScanQr: () -> Unit
) {
    RadarAnimation()

    Text("Đang chờ kết nối", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Mở trình duyệt trên laptop, nhập:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    uiState.serverUrl,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Primary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onCopyUrl) {
                    Icon(Icons.Default.ContentCopy, "Sao chép", tint = Primary)
                }
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Default.Timer, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Text(
            "Hết hạn sau ${uiState.timeoutSeconds / 60}:${(uiState.timeoutSeconds % 60).toString().padStart(2, '0')}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onRefresh, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Làm mới")
        }
        Button(
            onClick = onScanQr,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary)
        ) {
            Icon(Icons.Default.QrCodeScanner, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Quét QR", fontWeight = FontWeight.SemiBold)
        }
    }

    StepTimeline(currentStep = 0)
}

// ─── CONNECTED ────────────────────────────────────────────────────────────────

@Composable
fun ConnectedContent() {
    Box(
        modifier = Modifier.size(80.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF1A2E1A)),
        contentAlignment = Alignment.Center
    ) { Text("✅", fontSize = 40.sp) }
    Text("Laptop đã kết nối!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text("Đang tạo khoá xác nhận...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    CircularProgressIndicator(color = Primary)
}

// ─── VERIFYING ────────────────────────────────────────────────────────────────

@Composable
fun VerifyContent(code: String, onConfirm: () -> Unit, onReject: () -> Unit) {
    Icon(Icons.Default.Security, null, tint = Primary, modifier = Modifier.size(48.dp))
    Text("Xác nhận kết nối", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text(
        "So sánh mã 6 số bên dưới với mã hiện trên laptop.\nNếu trùng khớp → nhấn Xác nhận.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Mã xác nhận", style = MaterialTheme.typography.labelMedium, color = Primary)
            Spacer(Modifier.height(8.dp))
            Text(
                text = code,
                fontSize = 48.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 6.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text("Thay đổi mỗi phiên · Không chia sẻ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = onReject,
            modifier = Modifier.weight(1f).height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Không khớp")
        }
        Button(
            onClick = onConfirm,
            modifier = Modifier.weight(1f).height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
        ) {
            Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Xác nhận", fontWeight = FontWeight.SemiBold)
        }
    }

    StepTimeline(currentStep = 2)
}

// ─── SYNCING ──────────────────────────────────────────────────────────────────

@Composable
fun SyncingContent(uiState: LanSyncUiState) {
    Icon(Icons.Default.Sync, null, tint = Primary, modifier = Modifier.size(48.dp))
    Text("Đang đồng bộ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(progress = uiState.syncProgress / 100f, modifier = Modifier.fillMaxWidth(), color = Primary)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(uiState.syncProgressText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${uiState.syncProgress}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Primary)
        }
    }
}

// ─── SUCCESS ──────────────────────────────────────────────────────────────────

@Composable
fun SuccessContent(count: Int, onDone: () -> Unit) {
    Box(
        modifier = Modifier.size(88.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xFF1A3A1A)),
        contentAlignment = Alignment.Center
    ) { Text("✅", fontSize = 44.sp) }

    Text("Thành công!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text("Dữ liệu đã sẵn sàng trên trình duyệt", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (count > 0) {
        Text("($count tài khoản)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Button(
        onClick = onDone,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Primary)
    ) { Text("Hoàn tất", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
}

// ─── ERROR ────────────────────────────────────────────────────────────────────

@Composable
fun ErrorContent(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    Box(
        modifier = Modifier.size(80.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.errorContainer),
        contentAlignment = Alignment.Center
    ) { Text("❌", fontSize = 36.sp) }

    Text("Đã xảy ra lỗi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Text(message, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Quay lại") }
        Button(onClick = onRetry, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Primary)) { Text("Thử lại") }
    }
}

// ─── LOADING ──────────────────────────────────────────────────────────────────

@Composable
fun LoadingContent(text: String) {
    CircularProgressIndicator(color = Primary, modifier = Modifier.size(48.dp))
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ─── RADAR ANIMATION ──────────────────────────────────────────────────────────

@Composable
fun RadarAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val scale1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f, label = "r1",
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart)
    )
    val scale2 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f, label = "r2",
        animationSpec = infiniteRepeatable(tween(2000, 600, LinearEasing), RepeatMode.Restart)
    )
    val scale3 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f, label = "r3",
        animationSpec = infiniteRepeatable(tween(2000, 1200, LinearEasing), RepeatMode.Restart)
    )

    Box(modifier = Modifier.size(140.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val maxRadius = size.width / 2
            listOf(scale1, scale2, scale3).forEach { scale ->
                drawCircle(
                    color = Color(0xFF4C6EF5).copy(alpha = (1f - scale) * 0.4f),
                    radius = maxRadius * scale,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
        Box(
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(Primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Wifi, null, tint = Primary, modifier = Modifier.size(28.dp))
        }
    }
}

// ─── STEP TIMELINE ────────────────────────────────────────────────────────────

@Composable
fun StepTimeline(currentStep: Int) {
    val steps = listOf("Tạo phiên", "Kết nối", "Xác thực", "Gửi dữ liệu")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, label ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(50.dp))
                        .background(if (index <= currentStep) Primary else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (index < currentStep) {
                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    } else {
                        Text("${index + 1}", fontSize = 11.sp, color = if (index <= currentStep) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = if (index <= currentStep) Primary else MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            if (index < steps.size - 1) {
                HorizontalDivider(modifier = Modifier.width(24.dp).padding(bottom = 16.dp), color = if (index < currentStep) Primary else MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

// ─── SECURITY BADGE ───────────────────────────────────────────────────────────

@Composable
fun SecurityBadge() {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            "🔒  AES-256-GCM · ECDH Key Exchange · Không server trung gian · Tự hủy sau 3 phút",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ─── PASSWORD CONFIRM DIALOG ──────────────────────────────────────────────────

@Composable
fun PasswordConfirmDialog(error: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var showPw by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Security, null, tint = Primary) },
        title = { Text("Xác nhận danh tính") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Nhập Master Password để bắt đầu phiên LAN Sync.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Master Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    trailingIcon = {
                        IconButton(onClick = { showPw = !showPw }) {
                            Icon(if (showPw) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                        }
                    },
                    visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    isError = error.isNotEmpty(),
                    supportingText = if (error.isNotEmpty()) ({ Text(error, color = MaterialTheme.colorScheme.error) }) else null,
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(password) },
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) { Text("Xác nhận") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    )
}