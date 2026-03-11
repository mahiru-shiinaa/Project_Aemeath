package com.aemeath.app.ui.account

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aemeath.app.data.db.entity.AccountEntity
import com.aemeath.app.navigation.Screen
import com.aemeath.app.ui.theme.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

@RequiresApi(Build.VERSION_CODES.Q)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AccountListScreen(
    navController: NavController,
    viewModel: AccountListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val listState = rememberLazyListState()
    val dragDropState = rememberDragDropState(listState) { from, to ->
        viewModel.onItemMoved(from, to)
    }

    BackHandler(enabled = uiState.isMultiSelectMode || uiState.isReorderMode) {
        if (uiState.isMultiSelectMode) viewModel.exitMultiSelect()
        if (uiState.isReorderMode) viewModel.toggleReorderMode()
    }

    // QR & Snackbar logic
    var qrSheetAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var qrBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var qrCountdown by remember { mutableStateOf(60) }
    var qrDecryptedPassword by remember { mutableStateOf("") }

    LaunchedEffect(qrSheetAccount) {
        val acc = qrSheetAccount ?: return@LaunchedEffect
        qrDecryptedPassword = viewModel.getDecryptedPassword(acc.encryptedPassword)
        val appName = uiState.webApp?.name ?: "Unknown App"
        val content = "App: $appName\nUsername: ${acc.username}\nPassword: ${qrDecryptedPassword}"
        qrBitmap = generateQrBitmap(content)
        qrCountdown = 60
        while (qrCountdown > 0) { delay(1000); qrCountdown-- }
        qrSheetAccount = null
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            val result = snackbarHostState.showSnackbar(msg, actionLabel = "Hoàn tác")
            if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
            viewModel.clearSnackbar()
        }
    }

    // Dialogs ...
    if (uiState.showDeleteConfirmId != null) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDeleteSingle() },
            title = { Text("Xóa tài khoản?") },
            text = { Text("Tài khoản này sẽ bị xóa vĩnh viễn.") },
            confirmButton = { TextButton(onClick = { viewModel.confirmDeleteSingle() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Xóa") } },
            dismissButton = { TextButton(onClick = { viewModel.cancelDeleteSingle() }) { Text("Hủy") } }
        )
    }

    if (uiState.showDeleteMultiConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDeleteMulti() },
            title = { Text("Xóa ${uiState.selectedIds.size} tài khoản?") },
            text = { Text("Các tài khoản đã chọn sẽ bị xóa vĩnh viễn.") },
            confirmButton = { TextButton(onClick = { viewModel.confirmDeleteMulti() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Xóa") } },
            dismissButton = { TextButton(onClick = { viewModel.cancelDeleteMulti() }) { Text("Hủy") } }
        )
    }

    // QR Bottom Sheet ...
    if (qrSheetAccount != null) {
        ModalBottomSheet(onDismissRequest = { qrSheetAccount = null }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp).navigationBarsPadding().padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(StrengthWeak.copy(alpha = 0.12f)).padding(12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = StrengthWeak, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text("Chỉ dùng ở nơi an toàn. QR tự hủy sau $qrCountdown giây.", style = MaterialTheme.typography.bodySmall, color = StrengthWeak, textAlign = TextAlign.Center)
                }
                Text("Chia sẻ tài khoản qua QR", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(qrSheetAccount?.title?.ifBlank { qrSheetAccount?.username } ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                qrBitmap?.let { bmp ->
                    Box(modifier = Modifier.size(220.dp).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(12.dp)) { androidx.compose.foundation.Image(bitmap = bmp, contentDescription = "QR Code", modifier = Modifier.fillMaxSize()) }

                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(progress = qrCountdown / 60f, color = if (qrCountdown > 20) Primary else StrengthWeak, trackColor = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(48.dp), strokeWidth = 4.dp)
                        Text("$qrCountdown", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (qrCountdown > 20) Primary else StrengthWeak)
                    }

                    // ─── ACTION BUTTONS ───
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Chia sẻ qua app khác
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        val file = File(context.cacheDir, "qr_share.png")
                                        val androidBitmap = bmp.asAndroidBitmap()
                                        FileOutputStream(file).use {
                                            androidBitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                                        }
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.provider",
                                            file
                                        )
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "image/png"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(
                                            Intent.createChooser(shareIntent, "Chia sẻ QR qua...")
                                        )
                                    } catch (e: Exception) {
                                        // Dùng Toast vì Snackbar bị BottomSheet che
                                        Toast.makeText(context, "Lỗi chia sẻ: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Chia sẻ", fontWeight = FontWeight.Medium)
                        }

                        // Tải về (FIXED: Dùng MediaStore & Toast)
                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        val androidBitmap = bmp.asAndroidBitmap()
                                        val timestamp = System.currentTimeMillis()
                                        val fileName = "Aemeath_QR_$timestamp.png"

                                        val resolver = context.contentResolver
                                        val contentValues = ContentValues().apply {
                                            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                                            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                                            }
                                        }

                                        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                                        if (uri != null) {
                                            val outputStream: OutputStream? = resolver.openOutputStream(uri)
                                            outputStream?.use { stream ->
                                                androidBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                                            }
                                            // Dùng Toast để hiện thông báo lên TRÊN BottomSheet
                                            Toast.makeText(context, "✅ Đã lưu vào thư mục Downloads", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "❌ Không thể tạo file", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "❌ Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary)
                        ) {
                            Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Tải về", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            when {
                uiState.isMultiSelectMode -> {
                    TopAppBar(
                        title = { Text("${uiState.selectedIds.size} đã chọn", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                        navigationIcon = { IconButton(onClick = { viewModel.exitMultiSelect() }) { Icon(Icons.Default.Close, "Đóng") } },
                        actions = { IconButton(onClick = { viewModel.requestDeleteMulti() }) { Icon(Icons.Default.Delete, "Xóa", tint = MaterialTheme.colorScheme.error) } },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                }
                uiState.isReorderMode -> {
                    TopAppBar(
                        title = { Text("Sắp xếp lại", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                        navigationIcon = { IconButton(onClick = { viewModel.toggleReorderMode() }) { Icon(Icons.Default.Close, "Hủy") } },
                        actions = {
                            TextButton(onClick = { viewModel.toggleReorderMode() }) {
                                Text("Xong", fontWeight = FontWeight.Bold, color = Primary)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                }
                else -> {
                    TopAppBar(
                        title = {},
                        navigationIcon = { SquareIconButton(icon = Icons.Default.ArrowBack, onClick = { navController.popBackStack() }) },
                        actions = {
                            var showMenu by remember { mutableStateOf(false) }
                            SquareIconButton(icon = Icons.Default.MoreHoriz, onClick = { showMenu = true })
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Chọn nhiều") },
                                    leadingIcon = { Icon(Icons.Default.CheckCircle, null) },
                                    onClick = { viewModel.toggleMultiSelect(); showMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sắp xếp") },
                                    leadingIcon = { Icon(Icons.Default.SwapVert, null) },
                                    onClick = { viewModel.toggleReorderMode(); showMenu = false }
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }
            }
        },
        floatingActionButton = {
            if (!uiState.isMultiSelectMode && !uiState.isReorderMode) {
                FloatingActionButton(onClick = { navController.navigate(Screen.AddAccount.createRoute(uiState.webApp?.id)) }, containerColor = Primary, contentColor = Color.White, shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Default.Add, "Thêm")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            if (!uiState.isMultiSelectMode && !uiState.isReorderMode) {
                uiState.webApp?.let { webApp ->
                    Box(
                        modifier = Modifier.fillMaxWidth(0.88f).align(Alignment.CenterHorizontally).padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 20.dp)
                            .shadow(12.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black.copy(0.5f), spotColor = Color.Black)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF0F172A), Color(0xFF1E293B))))
                            .border(1.dp, Color.White.copy(0.06f), RoundedCornerShape(24.dp)).padding(20.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(modifier = Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(Brush.verticalGradient(listOf(Color(0xFF1DB954).copy(0.25f), Color(0xFF1DB954).copy(0.15f)))), contentAlignment = Alignment.Center) {
                                Text(webApp.iconEmoji, fontSize = 30.sp)
                            }
                            Column {
                                Text(webApp.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                                Spacer(Modifier.height(4.dp))
                                Text("${uiState.accounts.size} tài khoản", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(0.65f))
                            }
                        }
                    }
                }
            }

            when {
                uiState.isLoading -> { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Primary) } }
                uiState.accounts.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🔑", fontSize = 48.sp); Text("Chưa có tài khoản nào", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().dragContainer(dragDropState),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(uiState.accounts, key = { _, it -> it.id }) { index, account ->
                            DraggableItem(dragDropState = dragDropState, index = index) { isDragging ->
                                val elevation by animateFloatAsState(if (isDragging) 8f else 0f, label = "elevation")
                                AccountCard(
                                    account = account,
                                    isPasswordRevealed = uiState.revealedPasswordIds.contains(account.id),
                                    isSelected = uiState.selectedIds.contains(account.id),
                                    isMultiSelectMode = uiState.isMultiSelectMode,
                                    isReorderMode = uiState.isReorderMode,
                                    getDecryptedPassword = { viewModel.getDecryptedPassword(it) },
                                    onTogglePassword = { viewModel.togglePasswordVisibility(account.id) },
                                    onCopyUsername = { viewModel.copyUsername(account.username) },
                                    onCopyPassword = { viewModel.copyPassword(account.encryptedPassword) },
                                    onEdit = { navController.navigate(Screen.EditAccount.createRoute(account.id)) },
                                    onDelete = { viewModel.requestDeleteSingle(account.id) },
                                    onToggleSelect = { viewModel.toggleSelectItem(account.id) },
                                    onLongPress = {
                                        if (!uiState.isMultiSelectMode && !uiState.isReorderMode) {
                                            viewModel.toggleMultiSelect()
                                            viewModel.toggleSelectItem(account.id)
                                        }
                                    },
                                    onShowQr = { qrSheetAccount = account },
                                    modifier = Modifier
                                        .shadow(elevation.dp, RoundedCornerShape(18.dp))
                                        .zIndex(if (isDragging) 1f else 0f)
                                )
                            }
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}

// ... AccountCard & Utils ...
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountCard(account: AccountEntity, isPasswordRevealed: Boolean, isSelected: Boolean, isMultiSelectMode: Boolean, isReorderMode: Boolean, getDecryptedPassword: (String) -> String, onTogglePassword: () -> Unit, onCopyUsername: () -> Unit, onCopyPassword: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onToggleSelect: () -> Unit, onLongPress: () -> Unit, onShowQr: () -> Unit, modifier: Modifier = Modifier) {
    val decryptedPassword = if (isPasswordRevealed) getDecryptedPassword(account.encryptedPassword) else null
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    Card(modifier = modifier.fillMaxWidth().then(if (isSelected) Modifier.border(1.5.dp, Primary, RoundedCornerShape(18.dp)) else Modifier).combinedClickable(onClick = { if (isMultiSelectMode) onToggleSelect() }, onLongClick = onLongPress, enabled = !isReorderMode), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = if (isSelected) Primary.copy(0.10f) else MaterialTheme.colorScheme.surfaceVariant.copy(0.35f)), border = if (!isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant.copy(0.6f)) else null) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSelected && isMultiSelectMode) { Icon(Icons.Default.CheckCircle, null, tint = Primary, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
                if (isReorderMode) { Icon(Icons.Default.DragHandle, null, tint = Primary, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)) }
                Text(text = account.title.ifBlank { account.username }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                if (!isMultiSelectMode && !isReorderMode) { Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { SmallActionButton(Icons.Default.QrCode, onShowQr); SmallActionButton(Icons.Default.Edit, onEdit); SmallActionButton(Icons.Default.Delete, onDelete, MaterialTheme.colorScheme.error) } }
            }
            Spacer(Modifier.height(10.dp)); HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(0.6f), thickness = 0.8.dp)
            AccountField("USER", account.username, copyButton = if (!isMultiSelectMode && !isReorderMode) ({ FieldCopyBtn(onCopyUsername) }) else null)
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(0.6f), thickness = 0.8.dp)
            AccountField("PASS", if (isPasswordRevealed && decryptedPassword != null) decryptedPassword else "••••••••", isMono = true, isMasked = !isPasswordRevealed, copyButton = if (!isMultiSelectMode && !isReorderMode) ({ Row { IconButton(onClick = onTogglePassword, modifier = Modifier.size(30.dp)) { Icon(if (isPasswordRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Toggle", modifier = Modifier.size(14.dp)) }; IconButton(onClick = onCopyPassword, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.ContentCopy, "Copy", modifier = Modifier.size(14.dp), tint = Primary) } } }) else null)
        }
    }
}

@Composable fun SquareIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface // Thêm tham số tùy chọn
) {
    Box(modifier = Modifier.padding(horizontal = 12.dp).size(45.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(0.50f)), contentAlignment = Alignment.Center) {
        IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
            Icon(icon, null, tint = tint) // Đã đổi từ màu mặc định sang biến tint
        }
    }
}

@Composable fun AccountField(label: String, value: String, isMono: Boolean = false, isMasked: Boolean = false, maxLines: Int = 1, copyButton: (@Composable () -> Unit)? = null) { Row(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f), fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, modifier = Modifier.width(72.dp)); Text(text = value, style = MaterialTheme.typography.bodySmall.copy(fontFamily = if (isMono) FontFamily.Monospace else FontFamily.Default, letterSpacing = if (isMasked) 3.sp else 0.sp), color = if (isMasked) MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f) else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f), maxLines = maxLines, overflow = TextOverflow.Ellipsis); copyButton?.invoke() } }
@Composable fun FieldCopyBtn(onClick: () -> Unit) { IconButton(onClick = onClick, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.ContentCopy, "Copy", modifier = Modifier.size(14.dp), tint = Primary) } }
@Composable fun SmallActionButton(icon: ImageVector, onClick: () -> Unit, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant, hoverTint: Color = Primary) { Box(modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)), contentAlignment = Alignment.Center) { IconButton(onClick = onClick, modifier = Modifier.size(30.dp)) { Icon(icon, null, modifier = Modifier.size(15.dp), tint = tint) } } }
fun generateQrBitmap(content: String): ImageBitmap? { return try { val writer = QRCodeWriter(); val size = 512; val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size); val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565); for (x in 0 until size) { for (y in 0 until size) { bmp.setPixel(x, y, if (bitMatrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE) } }; bmp.asImageBitmap() } catch (e: Exception) { null } }

// ─── DRAG AND DROP HELPERS (Robust Manual Implementation) ─────────────────────

@Composable
fun rememberDragDropState(
    lazyListState: LazyListState,
    onMove: (Int, Int) -> Unit
): DragDropState {
    val scope = rememberCoroutineScope()
    val state = remember(lazyListState) {
        DragDropState(state = lazyListState, onMove = onMove, scope = scope)
    }
    return state
}

class DragDropState(
    private val state: LazyListState,
    private val onMove: (Int, Int) -> Unit,
    private val scope: CoroutineScope
) {
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    internal var draggingItemOffset by mutableStateOf(0f)
        private set

    fun onDragStart(offset: Offset) {
        state.layoutInfo.visibleItemsInfo
            .firstOrNull { item -> offset.y.toInt() in item.offset..(item.offset + item.size) }
            ?.let { item ->
                draggingItemIndex = item.index
                draggingItemOffset = 0f
            }
    }

    fun onDragInterrupted() {
        draggingItemIndex = null
        draggingItemOffset = 0f
    }

    fun onDrag(offset: Float) {
        draggingItemOffset += offset
        val currentIndex = draggingItemIndex ?: return
        val currentInfo = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentIndex } ?: return
        val startOffset = currentInfo.offset + draggingItemOffset
        val endOffset = startOffset + currentInfo.size

        // Move Up
        val movedUp = state.layoutInfo.visibleItemsInfo
            .filter { it.index < currentIndex }
            .lastOrNull { startOffset < it.offset + it.size / 2 }

        if (movedUp != null) {
            onMove(currentIndex, movedUp.index)
            draggingItemIndex = movedUp.index
            draggingItemOffset += currentInfo.size
            return
        }

        // Move Down
        val movedDown = state.layoutInfo.visibleItemsInfo
            .filter { it.index > currentIndex }
            .firstOrNull { endOffset > it.offset + it.size / 2 }

        if (movedDown != null) {
            onMove(currentIndex, movedDown.index)
            draggingItemIndex = movedDown.index
            draggingItemOffset -= currentInfo.size
            return
        }

        // Auto Scroll
        val viewportStart = 0f
        val viewportEnd = state.layoutInfo.viewportEndOffset.toFloat()
        val scrollZone = 100f // 100px scroll zone

        if (currentInfo.offset + draggingItemOffset < viewportStart + scrollZone) {
            scope.launch { state.scrollBy(-15f) }
        } else if (currentInfo.offset + currentInfo.size + draggingItemOffset > viewportEnd - scrollZone) {
            scope.launch { state.scrollBy(15f) }
        }
    }
}

fun Modifier.dragContainer(dragDropState: DragDropState): Modifier {
    return this.pointerInput(dragDropState) {
        detectDragGesturesAfterLongPress(
            onDragStart = { dragDropState.onDragStart(it) },
            onDrag = { change, dragAmount ->
                change.consume()
                dragDropState.onDrag(dragAmount.y)
            },
            onDragEnd = { dragDropState.onDragInterrupted() },
            onDragCancel = { dragDropState.onDragInterrupted() }
        )
    }
}

@Composable
fun DraggableItem(
    dragDropState: DragDropState,
    index: Int,
    content: @Composable (isDragging: Boolean) -> Unit
) {
    val isDragging = index == dragDropState.draggingItemIndex
    val draggingOffset = if (isDragging) dragDropState.draggingItemOffset else 0f

    Box(
        modifier = Modifier
            .graphicsLayer { translationY = draggingOffset }
            .zIndex(if (isDragging) 1f else 0f)
    ) {
        content(isDragging)
    }
}
