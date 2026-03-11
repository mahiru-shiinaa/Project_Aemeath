package com.aemeath.app.ui.backup

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aemeath.app.data.repository.AccountRepository
import com.aemeath.app.security.CryptoManager
import com.aemeath.app.security.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

enum class ImportMode { MERGE, OVERWRITE }
enum class ExportType { ENCRYPTED, CSV, GOOGLE_CSV }

data class BackupUiState(
    val isLoading: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null,

    // ── Export password dialog ──
    val showExportPasswordDialog: Boolean = false,
    val pendingEncryptedExportUri: Uri? = null,

    // ── CSV warning ──
    val showExportWarning: Boolean = false,
    val pendingExportType: ExportType? = null,
    val pendingExportUri: Uri? = null,

    // ── Import ──
    val showImportConfirm: Boolean = false,
    val pendingImportUri: Uri? = null,
    val pendingImportMode: ImportMode = ImportMode.MERGE,
    val pendingImportCount: Int = 0,

    // ── Import password dialog ──
    val showImportPasswordDialog: Boolean = false,
    val pendingImportFileContent: String? = null
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val cryptoManager: CryptoManager,
    private val sessionManager: SessionManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    // Field riêng của ViewModel — KHÔNG nằm trong UiState
    // Lưu tạm backup password giữa confirmImportPassword() → confirmImport()
    // Xoá ngay sau khi dùng
    private var pendingBackupPassword: String? = null

    // ─── Export entry point ────────────────────────────────────────────────────
    fun onExportUriReceived(uri: Uri, type: ExportType) {
        when (type) {
            ExportType.ENCRYPTED -> {
                _uiState.value = _uiState.value.copy(
                    showExportPasswordDialog = true,
                    pendingEncryptedExportUri = uri
                )
            }
            ExportType.CSV, ExportType.GOOGLE_CSV -> {
                _uiState.value = _uiState.value.copy(
                    showExportWarning = true,
                    pendingExportType = type,
                    pendingExportUri = uri
                )
            }
        }
    }

    fun confirmEncryptedExport(backupPassword: String) {
        val uri = _uiState.value.pendingEncryptedExportUri ?: return
        _uiState.value = _uiState.value.copy(
            showExportPasswordDialog = false,
            pendingEncryptedExportUri = null
        )
        exportEncrypted(uri, backupPassword)
    }

    fun dismissExportPasswordDialog() {
        _uiState.value = _uiState.value.copy(
            showExportPasswordDialog = false,
            pendingEncryptedExportUri = null
        )
    }

    fun confirmCsvExport() {
        val type = _uiState.value.pendingExportType ?: return
        val uri = _uiState.value.pendingExportUri ?: return
        _uiState.value = _uiState.value.copy(
            showExportWarning = false,
            pendingExportUri = null,
            pendingExportType = null
        )
        exportCsv(uri, googleFormat = type == ExportType.GOOGLE_CSV)
    }

    fun dismissExportWarning() {
        _uiState.value = _uiState.value.copy(
            showExportWarning = false,
            pendingExportUri = null,
            pendingExportType = null
        )
    }

    // ─── Encrypted export ──────────────────────────────────────────────────────
    private fun exportEncrypted(uri: Uri, backupPassword: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val sessionKey = sessionManager.requireKey()
                val accounts = accountRepository.getAllAccounts()
                val webApps = accountRepository.getAllWebApps()

                val root = JSONObject().apply {
                    put("version", 2)
                    put("exportedAt", System.currentTimeMillis())
                    put("webApps", JSONArray().also { arr ->
                        webApps.forEach { w ->
                            arr.put(JSONObject().apply {
                                put("id", w.id)
                                put("name", w.name)
                                put("iconEmoji", w.iconEmoji)
                                put("createdAt", w.createdAt)
                            })
                        }
                    })
                    put("accounts", JSONArray().also { arr ->
                        accounts.forEach { a ->
                            val plainPassword = try {
                                cryptoManager.decryptString(a.encryptedPassword, sessionKey)
                            } catch (e: Exception) { "" }

                            arr.put(JSONObject().apply {
                                put("id", a.id)
                                put("webAppId", a.webAppId)
                                put("title", a.title)
                                put("username", a.username)
                                put("password", plainPassword) // plaintext, mã hoá bởi backupKey bên dưới
                                put("notes", a.notes)
                                put("createdAt", a.createdAt)
                                put("updatedAt", a.updatedAt)
                            })
                        }
                    })
                }

                // Derive key từ backup password với salt riêng — độc lập hoàn toàn với session key
                val backupSalt = cryptoManager.generateSalt()
                val backupKey = cryptoManager.deriveKeyFromPassword(backupPassword, backupSalt)
                val jsonBytes = root.toString().toByteArray(Charsets.UTF_8)
                val encrypted = cryptoManager.encrypt(jsonBytes, backupKey)

                // Format: AEMEATH_BACKUP_V2\n{saltBase64}\n{encryptedBase64}
                val saltB64 = Base64.encodeToString(backupSalt, Base64.NO_WRAP)
                val encB64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
                writeText(uri, "AEMEATH_BACKUP_V2\n$saltB64\n$encB64")

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    successMessage = "✅ Đã xuất ${accounts.size} tài khoản (bảo vệ bằng mật khẩu backup)"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Lỗi xuất: ${e.message}"
                )
            }
        }
    }

    // ─── CSV export ────────────────────────────────────────────────────────────
    private fun exportCsv(uri: Uri, googleFormat: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val key = sessionManager.requireKey()
                val accounts = accountRepository.getAllAccounts()
                val webApps = accountRepository.getAllWebApps()
                val webAppMap = webApps.associateBy { it.id }

                val sb = StringBuilder()
                if (googleFormat) {
                    sb.append("name,url,username,password,note\r\n")
                    accounts.forEach { a ->
                        val name = webAppMap[a.webAppId]?.name ?: ""
                        val pw = cryptoManager.decryptString(a.encryptedPassword, key)
                        sb.append("${csvEscape(name)},,${csvEscape(a.username)},${csvEscape(pw)},${csvEscape(a.notes)}\r\n")
                    }
                } else {
                    sb.append("webapp,title,username,password,notes,created,updated\r\n")
                    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    accounts.forEach { a ->
                        val name = webAppMap[a.webAppId]?.name ?: ""
                        val pw = cryptoManager.decryptString(a.encryptedPassword, key)
                        sb.append(
                            "${csvEscape(name)},${csvEscape(a.title)},${csvEscape(a.username)}," +
                                    "${csvEscape(pw)},${csvEscape(a.notes)}," +
                                    "${fmt.format(Date(a.createdAt))},${fmt.format(Date(a.updatedAt))}\r\n"
                        )
                    }
                }

                val pfd = context.contentResolver.openFileDescriptor(uri, "wt")
                    ?: throw Exception("Không thể mở file")
                pfd.use { descriptor ->
                    FileOutputStream(descriptor.fileDescriptor).use { fos ->
                        OutputStreamWriter(fos, Charsets.UTF_8).use { it.write(sb.toString()); it.flush() }
                        fos.fd.sync()
                    }
                }

                val size = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
                if (size == 0L) throw Exception("File rỗng. Thử lưu vào bộ nhớ trong.")

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    successMessage = "✅ Đã xuất ${accounts.size} tài khoản CSV"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Lỗi xuất CSV: ${e.message}"
                )
            }
        }
    }

    // ─── Import entry point ────────────────────────────────────────────────────
    fun prepareImport(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val content = readFileContent(uri)
                when {
                    content.startsWith("AEMEATH_BACKUP_V2") -> {
                        // Cần mật khẩu backup để giải mã → hỏi trước
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            showImportPasswordDialog = true,
                            pendingImportFileContent = content,
                            pendingImportUri = uri
                        )
                    }
                    content.startsWith("AEMEATH_BACKUP_V1") -> {
                        // File cũ — dùng session key (backward compatible)
                        val count = estimateImportCountV1(content)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            showImportConfirm = true,
                            pendingImportUri = uri,
                            pendingImportCount = count,
                            pendingImportFileContent = content
                        )
                    }
                    else -> {
                        // CSV
                        val count = content.lines().count { it.isNotBlank() } - 1
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            showImportConfirm = true,
                            pendingImportUri = uri,
                            pendingImportCount = count,
                            pendingImportFileContent = content
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Không đọc được file: ${e.message}"
                )
            }
        }
    }

    // ── Validate mật khẩu backup → nếu đúng thì hiện confirm dialog ──
    fun confirmImportPassword(backupPassword: String) {
        val content = _uiState.value.pendingImportFileContent ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, showImportPasswordDialog = false)
            try {
                val lines = content.lines()
                val saltB64 = lines.getOrNull(1)?.trim()
                    ?: throw Exception("File backup bị hỏng (thiếu salt)")
                val encB64 = lines.getOrNull(2)?.trim()
                    ?: throw Exception("File backup bị hỏng (thiếu dữ liệu)")

                val backupSalt = Base64.decode(saltB64, Base64.NO_WRAP)
                val backupKey = cryptoManager.deriveKeyFromPassword(backupPassword, backupSalt)
                val encrypted = Base64.decode(encB64, Base64.NO_WRAP)

                // Thử giải mã — nếu sai mật khẩu sẽ throw AEADBadTagException
                val jsonBytes = try {
                    cryptoManager.decrypt(encrypted, backupKey)
                } catch (e: Exception) {
                    throw Exception("Mật khẩu backup không đúng")
                }

                val count = JSONObject(String(jsonBytes, Charsets.UTF_8))
                    .getJSONArray("accounts").length()

                // Mật khẩu đúng → lưu vào field riêng (KHÔNG trong UiState)
                pendingBackupPassword = backupPassword

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    showImportConfirm = true,
                    pendingImportCount = count,
                    pendingImportFileContent = content
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Lỗi xác thực"
                )
            }
        }
    }

    fun dismissImportPasswordDialog() {
        pendingBackupPassword = null
        _uiState.value = _uiState.value.copy(
            showImportPasswordDialog = false,
            pendingImportFileContent = null,
            pendingImportUri = null
        )
    }

    fun setImportMode(mode: ImportMode) {
        _uiState.value = _uiState.value.copy(pendingImportMode = mode)
    }

    fun confirmImport() {
        val content = _uiState.value.pendingImportFileContent ?: return
        val mode = _uiState.value.pendingImportMode
        _uiState.value = _uiState.value.copy(showImportConfirm = false)

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                when {
                    content.startsWith("AEMEATH_BACKUP_V2") -> {
                        val pw = pendingBackupPassword
                            ?: throw Exception("Mất thông tin xác thực, vui lòng thử lại")
                        pendingBackupPassword = null // Xoá ngay sau khi lấy
                        importEncryptedV2(content, mode, pw)
                    }
                    content.startsWith("AEMEATH_BACKUP_V1") -> {
                        importEncryptedV1(content, mode)
                    }
                    else -> importCsv(content, mode)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Lỗi nhập: ${e.message}"
                )
            }
        }
    }

    fun cancelImport() {
        pendingBackupPassword = null
        _uiState.value = _uiState.value.copy(
            showImportConfirm = false,
            pendingImportUri = null,
            pendingImportFileContent = null
        )
    }

    // ─── Import V2 (backup password riêng) ────────────────────────────────────
    private suspend fun importEncryptedV2(content: String, mode: ImportMode, backupPassword: String) {
        val lines = content.lines()
        val saltB64 = lines.getOrNull(1)?.trim() ?: throw Exception("File bị hỏng")
        val encB64 = lines.getOrNull(2)?.trim() ?: throw Exception("File bị hỏng")

        val backupSalt = Base64.decode(saltB64, Base64.NO_WRAP)
        val backupKey = cryptoManager.deriveKeyFromPassword(backupPassword, backupSalt)
        val encrypted = Base64.decode(encB64, Base64.NO_WRAP)
        val root = JSONObject(String(cryptoManager.decrypt(encrypted, backupKey), Charsets.UTF_8))

        if (mode == ImportMode.OVERWRITE) {
            accountRepository.getAllWebApps().forEach { accountRepository.deleteWebApp(it) }
        }

        val existingWebAppMap = accountRepository.getAllWebApps()
            .associate { it.name.lowercase().trim() to it.id }.toMutableMap()

        val webAppIdMap = mutableMapOf<Long, Long>()
        val webAppsArr = root.getJSONArray("webApps")
        for (i in 0 until webAppsArr.length()) {
            val w = webAppsArr.getJSONObject(i)
            val name = w.getString("name")
            val nameKey = name.lowercase().trim()
            val actualId = if (mode == ImportMode.MERGE && existingWebAppMap.containsKey(nameKey)) {
                existingWebAppMap[nameKey]!!
            } else {
                val id = accountRepository.insertWebApp(name, w.optString("iconEmoji", "🌐"))
                existingWebAppMap[nameKey] = id; id
            }
            webAppIdMap[w.getLong("id")] = actualId
        }

        val existingAccounts = if (mode == ImportMode.MERGE) {
            accountRepository.getAllAccounts()
                .map { "${it.webAppId}|${it.username.lowercase().trim()}" }.toHashSet()
        } else hashSetOf()

        var imported = 0
        val accountsArr = root.getJSONArray("accounts")
        for (i in 0 until accountsArr.length()) {
            val a = accountsArr.getJSONObject(i)
            val newWebAppId = webAppIdMap[a.getLong("webAppId")] ?: continue
            val username = a.getString("username")
            val accountKey = "${newWebAppId}|${username.lowercase().trim()}"
            if (mode == ImportMode.MERGE && existingAccounts.contains(accountKey)) continue

            accountRepository.insertAccount(
                webAppId = newWebAppId,
                title = a.optString("title", username),
                username = username,
                password = a.optString("password", ""),
                notes = a.optString("notes", "")
            )
            imported++
        }

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            successMessage = "✅ Đã nhập $imported tài khoản"
        )
    }

    // ─── Import V1 (backward compatible — dùng session key) ──────────────────
    private suspend fun importEncryptedV1(content: String, mode: ImportMode) {
        val key = sessionManager.requireKey()
        val encBase64 = content.removePrefix("AEMEATH_BACKUP_V1\n").trim()
        val encrypted = Base64.decode(encBase64, Base64.NO_WRAP)
        val jsonBytes = try {
            cryptoManager.decrypt(encrypted, key)
        } catch (e: Exception) {
            throw Exception("File V1 không thể giải mã bằng mật khẩu hiện tại. File này được tạo bằng mật khẩu cũ.")
        }
        val root = JSONObject(String(jsonBytes, Charsets.UTF_8))

        if (mode == ImportMode.OVERWRITE) {
            accountRepository.getAllWebApps().forEach { accountRepository.deleteWebApp(it) }
        }

        val existingWebAppMap = accountRepository.getAllWebApps()
            .associate { it.name.lowercase().trim() to it.id }.toMutableMap()

        val webAppIdMap = mutableMapOf<Long, Long>()
        val webAppsArr = root.getJSONArray("webApps")
        for (i in 0 until webAppsArr.length()) {
            val w = webAppsArr.getJSONObject(i)
            val name = w.getString("name")
            val nameKey = name.lowercase().trim()
            val actualId = if (mode == ImportMode.MERGE && existingWebAppMap.containsKey(nameKey)) {
                existingWebAppMap[nameKey]!!
            } else {
                val id = accountRepository.insertWebApp(name, w.optString("iconEmoji", "🌐"))
                existingWebAppMap[nameKey] = id; id
            }
            webAppIdMap[w.getLong("id")] = actualId
        }

        val existingAccounts = if (mode == ImportMode.MERGE) {
            accountRepository.getAllAccounts()
                .map { "${it.webAppId}|${it.username.lowercase().trim()}" }.toHashSet()
        } else hashSetOf()

        var imported = 0
        val accountsArr = root.getJSONArray("accounts")
        for (i in 0 until accountsArr.length()) {
            val a = accountsArr.getJSONObject(i)
            val newWebAppId = webAppIdMap[a.getLong("webAppId")] ?: continue
            val username = a.getString("username")
            val accountKey = "${newWebAppId}|${username.lowercase().trim()}"
            if (mode == ImportMode.MERGE && existingAccounts.contains(accountKey)) continue

            val plainPw = try {
                cryptoManager.decryptString(a.getString("encryptedPassword"), key)
            } catch (e: Exception) { "" }

            accountRepository.insertAccount(
                webAppId = newWebAppId,
                title = a.optString("title", username),
                username = username,
                password = plainPw,
                notes = a.optString("notes", "")
            )
            imported++
        }

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            successMessage = "✅ Đã nhập $imported tài khoản (từ file V1)"
        )
    }

    // ─── Import CSV ────────────────────────────────────────────────────────────
    private suspend fun importCsv(content: String, mode: ImportMode) {
        if (mode == ImportMode.OVERWRITE) {
            accountRepository.getAllWebApps().forEach { accountRepository.deleteWebApp(it) }
        }

        val lines = content.lines().filter { it.isNotBlank() }
        if (lines.size < 2) throw Exception("File CSV không có dữ liệu")

        val header = lines[0].lowercase().trim()
        val isGoogleFormat = header.startsWith("name,url")

        val webAppCache = accountRepository.getAllWebApps()
            .associate { it.name.lowercase().trim() to it.id }.toMutableMap()

        val existingAccounts = if (mode == ImportMode.MERGE) {
            accountRepository.getAllAccounts()
                .map { "${it.webAppId}|${it.username.lowercase().trim()}" }.toHashSet()
        } else hashSetOf()

        var imported = 0; var skipped = 0

        lines.drop(1).forEach { line ->
            if (line.isBlank()) return@forEach
            val cols = parseCsvLine(line)
            if (cols.size < 3) return@forEach
            try {
                val webAppName: String; val username: String; val password: String
                val title: String; val notes: String
                if (isGoogleFormat) {
                    webAppName = cols.getOrElse(0) { "" }.trim()
                    username = cols.getOrElse(2) { "" }.trim()
                    password = cols.getOrElse(3) { "" }.trim()
                    title = username; notes = cols.getOrElse(4) { "" }.trim()
                } else {
                    webAppName = cols.getOrElse(0) { "" }.trim()
                    title = cols.getOrElse(1) { "" }.trim()
                    username = cols.getOrElse(2) { "" }.trim()
                    password = cols.getOrElse(3) { "" }.trim()
                    notes = cols.getOrElse(4) { "" }.trim()
                }
                if (webAppName.isBlank() || username.isBlank()) return@forEach

                val webAppId = webAppCache[webAppName.lowercase()] ?: run {
                    val id = accountRepository.insertWebApp(webAppName)
                    webAppCache[webAppName.lowercase()] = id; id
                }
                val accountKey = "${webAppId}|${username.lowercase()}"
                if (mode == ImportMode.MERGE && existingAccounts.contains(accountKey)) {
                    skipped++; return@forEach
                }
                accountRepository.insertAccount(
                    webAppId = webAppId,
                    title = title.ifBlank { username },
                    username = username,
                    password = password,
                    notes = notes
                )
                existingAccounts.add(accountKey); imported++
            } catch (e: Exception) { /* skip malformed row */ }
        }

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            successMessage = if (skipped > 0)
                "✅ Đã nhập $imported tài khoản (bỏ qua $skipped trùng)"
            else "✅ Đã nhập $imported tài khoản từ CSV"
        )
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────
    private fun writeText(uri: Uri, content: String) {
        val pfd = context.contentResolver.openFileDescriptor(uri, "wt")
            ?: throw Exception("Không thể mở file")
        pfd.use { descriptor ->
            FileOutputStream(descriptor.fileDescriptor).use { fos ->
                OutputStreamWriter(fos, Charsets.UTF_8).use { it.write(content); it.flush() }
                fos.fd.sync()
            }
        }
    }

    private fun readFileContent(uri: Uri): String {
        val sb = StringBuilder()
        context.contentResolver.openInputStream(uri)?.use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).forEachLine {
                sb.append(it).append('\n')
            }
        } ?: throw Exception("Không thể mở file")
        return sb.toString()
    }

    private fun estimateImportCountV1(content: String): Int {
        return try {
            val key = sessionManager.requireKey()
            val enc = Base64.decode(content.removePrefix("AEMEATH_BACKUP_V1\n").trim(), Base64.NO_WRAP)
            JSONObject(String(cryptoManager.decrypt(enc, key), Charsets.UTF_8))
                .getJSONArray("accounts").length()
        } catch (e: Exception) { 0 }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false; var i = 0
        while (i < line.length) {
            when {
                line[i] == '"' && !inQuotes -> inQuotes = true
                line[i] == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"'); i++
                }
                line[i] == '"' && inQuotes -> inQuotes = false
                line[i] == ',' && !inQuotes -> { result.add(current.toString()); current = StringBuilder() }
                else -> current.append(line[i])
            }
            i++
        }
        result.add(current.toString())
        return result
    }

    private fun csvEscape(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n"))
            "\"${value.replace("\"", "\"\"")}\""
        else value
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(successMessage = null, errorMessage = null)
    }
}