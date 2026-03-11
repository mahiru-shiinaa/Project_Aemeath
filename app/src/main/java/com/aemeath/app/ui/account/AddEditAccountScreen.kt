package com.aemeath.app.ui.account

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.aemeath.app.security.CryptoManager
import com.aemeath.app.ui.auth.PasswordStrengthBar
import com.aemeath.app.ui.theme.*

import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange

// Danh sách Emoji phong phú hơn, chia nhóm (giả lập icon social/tech)
val EMOJI_CATEGORIES = mapOf(

    "Phổ biến" to listOf(
        "🧿","🪪","🛰️","🧭","🗝️","🪄","📡","🧬","🧠","🔮"
    ),

    "Mạng xã hội" to listOf(
        "facebook","messenger","instagram","tiktok","twitter",
        "youtube","telegram","zalo","whatsapp","linkedin",
        "reddit","discord","threads","snapchat","pinterest"
    ),

    "Tài chính & Mua sắm" to listOf(
        "🏛️","🪙","💹","🧾","🛍️","📉","📈","🧮",
        "paypal","stripe","visa","mastercard",
        "lazada","ebay"
    ),

    "Công việc & Cloud" to listOf(
        "📦","🗂️","🧰","📡","📂","📌",
        "google","drive","notion","dropbox",
        "slack","zoom","teams","figma","jira"
    ),

    "Giải trí & Game" to listOf(
        "🎧","🕹️","🎲","🎮","🧩","🪩",
        "netflix","spotify","steam","epic","twitch"
    ),

    "Dev & Tech" to listOf(
        "github","gitlab","bitbucket",
        "firebase","vercel","supabase",
        "docker","linux","windows"
    ),

    "Bảo mật" to listOf(
        "🛡️","🔐","🧷","🪤","🔑","📛","🧱","🚨"
    ),

    "Khác" to listOf(
        "✈️","🚗","🏥","❤️","🍀","🐶","🦊","🌌"
    )
)


// Helper map để mapping tên app sang emoji gần đúng (hoặc emoji ký tự)
fun getIconForType(type: String): String {
    return when(type.lowercase()) {
        "facebook" -> "📘"
        "messenger" -> "💬"
        "instagram" -> "📸"
        "tiktok" -> "🎵"
        "twitter" -> "🐦"
        "youtube" -> "▶️"
        "telegram" -> "✈️"
        "zalo" -> "📱"
        "whatsapp" -> "📞"
        "linkedin" -> "🤝"
        "reddit" -> "👽"
        "discord" -> "🎙️"
        "snapchat" -> "👻"
        "threads" -> "🧵"
        "pinterest" -> "📌"
        "paypal" -> "💰"
        "stripe" -> "💳"
        "visa" -> "💠"
        "mastercard" -> "🔴"
        "lazada" -> "📦"
        "ebay" -> "🛒"
        "google" -> "🔍"
        "drive" -> "🗂️"
        "notion" -> "📓"
        "dropbox" -> "📦"
        "slack" -> "📢"
        "zoom" -> "📹"
        "teams" -> "👥"
        "figma" -> "🎨"
        "jira" -> "📋"
        "github" -> "🐙"
        "gitlab" -> "🦊"
        "bitbucket" -> "🪣"
        "firebase" -> "🔥"
        "vercel" -> "▲"
        "supabase" -> "🟩"
        "docker" -> "🐳"
        "linux" -> "🐧"
        "windows" -> "🪟"
        "netflix" -> "🍿"
        "spotify" -> "🎧"
        "steam" -> "👾"
        "epic" -> "🕹️"
        "twitch" -> "📺"
        else -> type
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditAccountScreen(
    navController: NavController,
    viewModel: AddEditAccountViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) navController.popBackStack()
    }

    var showNewWebAppSheet by remember { mutableStateOf(false) }

    // Password Generator Sheet
    if (uiState.showPasswordGenerator) {
        PasswordGeneratorSheet(
            uiState = uiState,
            onLengthChange = viewModel::onGenLengthChange,
            onOptionChange = viewModel::onGenOptionChange,
            onRegenerate = viewModel::regeneratePassword,
            onApply = viewModel::applyGeneratedPassword,
            onDismiss = viewModel::closePasswordGenerator
        )
    }

    // New WebApp Sheet (Thay thế Dialog)
    if (showNewWebAppSheet) {
        NewWebAppSheet(
            initialName = uiState.webAppQuery,
            onCreate = { name, emoji ->
                viewModel.createNewWebApp(name, emoji)
                showNewWebAppSheet = false
            },
            onDismiss = { showNewWebAppSheet = false }
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
                    title = {
                        Text(
                            if (uiState.isEditMode) "Sửa tài khoản" else "Thêm tài khoản",
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        SquareIconButton(
                            icon = Icons.Default.Close,
                            onClick = { navController.popBackStack() }
                        )
                    },
                    actions = {
                        if (uiState.isLoading) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .size(45.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(0.2f),
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        } else {
                            SquareIconButton(
                                icon = Icons.Default.Check,
                                onClick = { viewModel.save() }
                            )
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // ─── 1. Web/App Selector ──────────────────────────────────────────
            SectionLabel("Web / Ứng dụng *")
            // Thay thế ExposedDropdownMenuBox bằng cách dùng Box thủ công
            Box(modifier = Modifier.fillMaxWidth()) {
                val density = LocalDensity.current

                // FIX: Dùng TextFieldValue để giữ vị trí con trỏ không bị nhảy
                var textFieldValue by remember {
                    mutableStateOf(TextFieldValue(text = uiState.webAppQuery, selection = TextRange(uiState.webAppQuery.length)))
                }

                // Đồng bộ: Khi uiState thay đổi từ bên ngoài (vd: chọn từ dropdown), cập nhật lại textFieldValue
                // Dùng side-effect để chỉ cập nhật khi text thực sự khác biệt (tránh xung đột khi đang gõ)
                LaunchedEffect(uiState.webAppQuery) {
                    if (uiState.webAppQuery != textFieldValue.text) {
                        textFieldValue = textFieldValue.copy(
                            text = uiState.webAppQuery,
                            selection = TextRange(uiState.webAppQuery.length) // Đưa con trỏ về cuối khi chọn xong
                        )
                    }
                }

                val textFieldShape = if (uiState.showWebAppDropdown)
                    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                else
                    RoundedCornerShape(16.dp)

                OutlinedTextField(
                    value = textFieldValue, // Dùng biến local TextFieldValue
                    onValueChange = { newValue ->
                        textFieldValue = newValue // Cập nhật UI ngay lập tức (giữ con trỏ)
                        viewModel.onWebAppQueryChange(newValue.text) // Gửi dữ liệu về ViewModel
                    },

                    label = { Text("Chọn hoặc nhập tên...") },
                    placeholder = { Text("Ví dụ: Facebook, Google...") },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = uiState.selectedWebApp?.iconEmoji ?: "🌐", fontSize = 18.sp)
                        }
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (textFieldValue.text.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        // Xóa text thì reset cả TextFieldValue và VM
                                        val empty = TextFieldValue("", TextRange.Zero)
                                        textFieldValue = empty
                                        viewModel.onWebAppQueryChange("")
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Xóa",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    if (!uiState.showWebAppDropdown) viewModel.onWebAppQueryChange(textFieldValue.text)
                                    else viewModel.dismissWebAppDropdown()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (uiState.showWebAppDropdown)
                                        Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = textFieldShape,
                    isError = uiState.webAppError != null,
                    supportingText = uiState.webAppError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                // ── Dropdown tự vẽ, thay thế DropdownMenu ──
                // ── Dropdown tự vẽ, thay thế DropdownMenu ──
                if (uiState.showWebAppDropdown) {
                    val dropdownShape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopStart)
                            .padding(top = 56.dp) // Đẩy xuống dưới TextField
                            .clip(dropdownShape)
                            .border(2.dp, Primary, dropdownShape)
                            // --- THAY ĐỔI Ở ĐÂY: Giới hạn chiều cao max khoảng 5 item (tầm 280dp) ---
                            .heightIn(max = 280.dp),
                        shape = dropdownShape,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp,
                        shadowElevation = 4.dp
                    ) {
                        // --- THAY ĐỔI Ở ĐÂY: Dùng LazyColumn thay vì Column để cuộn được ---
                        androidx.compose.foundation.lazy.LazyColumn {

                            // Trường hợp 1: Không có gợi ý -> Hiển thị nút "Tạo mới"
                            if (uiState.webAppSuggestions.isEmpty() && uiState.webAppQuery.isNotBlank()) {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.dismissWebAppDropdown()
                                                showNewWebAppSheet = true
                                            }
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.AddCircleOutline, null, tint = Primary)
                                        Text(
                                            "Tạo mới: \"${uiState.webAppQuery}\"",
                                            color = Primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            // Trường hợp 2: Có danh sách gợi ý
                            else {
                                items(
                                    count = uiState.webAppSuggestions.size,
                                    key = { index -> uiState.webAppSuggestions[index].name } // (Tuỳ chọn) Key để tối ưu
                                ) { index ->
                                    val webApp = uiState.webAppSuggestions[index]

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.onWebAppSelected(webApp) }
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(webApp.iconEmoji, fontSize = 22.sp)
                                        Text(webApp.name, style = MaterialTheme.typography.bodyLarge)
                                    }

                                    // Divider giữa các item (trừ item cuối)
                                    if (index < uiState.webAppSuggestions.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            thickness = 0.5.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ─── 2. Title ─────────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Tiêu đề tài khoản")
                OutlinedTextField(
                    value = uiState.title,
                    onValueChange = viewModel::onTitleChange,
                    label = { Text("Mô tả ngắn gọn") },
                    placeholder = { Text("VD: Acc Chính, Acc Game...") },
                    leadingIcon = { Icon(Icons.Default.Label, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
            }

            // ─── 3. Username ──────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Username / Email *")
                OutlinedTextField(
                    value = uiState.username,
                    onValueChange = viewModel::onUsernameChange,
                    label = { Text("Tên đăng nhập") },
                    leadingIcon = { Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    isError = uiState.usernameError != null,
                    supportingText = uiState.usernameError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
                )
            }

            // ─── 4. Password ──────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Mật khẩu *")
                var showPassword by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = { Text("Nhập mật khẩu") },
                    leadingIcon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingIcon = {
                        Row {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                            }
                            IconButton(onClick = { viewModel.openPasswordGenerator() }) {
                                Icon(Icons.Default.AutoAwesome, null, tint = Primary)
                            }
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    isError = uiState.passwordError != null,
                    supportingText = uiState.passwordError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    singleLine = true
                )

                AnimatedVisibility(visible = uiState.password.isNotEmpty()) {
                    PasswordStrengthBar(
                        strength = uiState.passwordStrength,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ─── 5. Notes ─────────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Ghi chú")
                OutlinedTextField(
                    value = uiState.notes,
                    onValueChange = viewModel::onNotesChange,
                    label = { Text("Thông tin thêm (câu hỏi bảo mật, pin...)") },
                    leadingIcon = { Icon(Icons.Default.Notes, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 120.dp),
                    shape = RoundedCornerShape(16.dp),
                    maxLines = 8,
                    keyboardOptions = KeyboardOptions.Default
                )
            }

            AnimatedVisibility(visible = uiState.error != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = uiState.error ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Button(
                onClick = { viewModel.save() },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = !uiState.isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text(
                    if (uiState.isEditMode) "Cập nhật thay đổi" else "Lưu tài khoản mới",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp)
    )
}

// ─── NEW WEBAPP SHEET (Fixed scrolling) ──────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewWebAppSheet(
    initialName: String,
    onCreate: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedEmoji by remember { mutableStateOf("🌐") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        // FIX: Thêm verticalScroll để cuộn được khi màn hình nhỏ
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()) // <--- ADDED
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Text("Tạo Web/App mới", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            // Input Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Tên Web/App") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                leadingIcon = {
                    Text(selectedEmoji, fontSize = 24.sp)
                }
            )

            Spacer(Modifier.height(24.dp))
            Text("Chọn biểu tượng:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))

            // Scrollable Grid Icons
            // Lưu ý: Box có chiều cao cố định (300.dp) nên không xung đột với verticalScroll của Column
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 52.dp),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EMOJI_CATEGORIES.forEach { (category, icons) ->
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = category,
                                style = MaterialTheme.typography.labelMedium,
                                color = Primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                        items(icons) { iconKey ->
                            val displayIcon = getIconForType(iconKey)
                            val isSelected = selectedEmoji == displayIcon

                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) Primary else MaterialTheme.colorScheme.surface)
                                    .clickable { selectedEmoji = displayIcon }
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(displayIcon, fontSize = 26.sp)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Hủy")
                }
                Button(
                    onClick = { if (name.isNotBlank()) onCreate(name.trim(), selectedEmoji) },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    enabled = name.isNotBlank()
                ) {
                    Text("Tạo mới", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─── PASSWORD GENERATOR SHEET (Fixed scrolling) ──────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordGeneratorSheet(
    uiState: AddEditUiState,
    onLengthChange: (Int) -> Unit,
    onOptionChange: (Boolean?, Boolean?, Boolean?, Boolean?) -> Unit,
    onRegenerate: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        // FIX: Thêm verticalScroll và padding bottom
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()) // <--- ADDED
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp), // <--- ADDED Extra padding
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Tạo mật khẩu mạnh", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            // Generated password preview
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.generatedPassword,
                        modifier = Modifier.weight(1f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp
                    )
                    IconButton(onClick = onRegenerate) {
                        Icon(Icons.Default.Refresh, "Tạo lại", tint = Primary)
                    }
                }
            }

            PasswordStrengthBar(
                strength = if (uiState.generatedPassword.isNotEmpty())
                    com.aemeath.app.security.CryptoManager.PasswordStrength.STRONG else null,
                modifier = Modifier.fillMaxWidth()
            )

            // Length slider
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Độ dài: ${uiState.genLength}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = uiState.genLength.toFloat(),
                    onValueChange = { onLengthChange(it.toInt()) },
                    valueRange = 8f..32f,
                    steps = 23,
                    colors = SliderDefaults.colors(thumbColor = Primary, activeTrackColor = Primary)
                )
            }

            // Options
// Options
            Text("Ký tự bao gồm:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)

            // Tính số lượng option đang được chọn
            val activeOptionsCount = listOf(
                uiState.genUppercase,
                uiState.genLowercase,
                uiState.genDigits,
                uiState.genSymbols
            ).count { it }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Helper để xử lý click: Chỉ cho phép tắt nếu còn nhiều hơn 1 option đang bật
                fun canToggle(currentValue: Boolean): Boolean {
                    return !currentValue || activeOptionsCount > 1
                }

                FilterChip(
                    selected = uiState.genUppercase,
                    onClick = {
                        if (canToggle(uiState.genUppercase))
                            onOptionChange(!uiState.genUppercase, null, null, null)
                    },
                    label = { Text("ABC") },
                    modifier = Modifier.weight(1f)
                )

                FilterChip(
                    selected = uiState.genLowercase,
                    onClick = {
                        if (canToggle(uiState.genLowercase))
                            onOptionChange(null, !uiState.genLowercase, null, null)
                    },
                    label = { Text("abc") },
                    modifier = Modifier.weight(1f)
                )

                FilterChip(
                    selected = uiState.genDigits,
                    onClick = {
                        if (canToggle(uiState.genDigits))
                            onOptionChange(null, null, !uiState.genDigits, null)
                    },
                    label = { Text("123") },
                    modifier = Modifier.weight(1f)
                )

                FilterChip(
                    selected = uiState.genSymbols,
                    onClick = {
                        if (canToggle(uiState.genSymbols))
                            onOptionChange(null, null, null, !uiState.genSymbols)
                    },
                    label = { Text("@#$") },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(14.dp)) { Text("Hủy") }
                Button(
                    onClick = onApply,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) { Text("Sữ dụng", fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}