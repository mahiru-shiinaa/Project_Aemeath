package com.aemeath.app.ui.lansync

import android.content.Context
import android.net.wifi.WifiManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aemeath.app.data.repository.AccountRepository
import com.aemeath.app.data.repository.PreferencesRepository
import com.aemeath.app.security.CryptoManager
import com.aemeath.app.security.SessionManager
import com.aemeath.app.ui.lansync.server.LanSyncCrypto
import com.aemeath.app.ui.lansync.server.LanSyncServer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.*
import javax.inject.Inject

enum class LanSyncStep {
    IDLE, STARTING, WAITING, CONNECTED, VERIFYING, CONFIRMED, SYNCING, SUCCESS, ERROR
}

data class LanSyncUiState(
    val step: LanSyncStep = LanSyncStep.IDLE,
    val serverUrl: String = "",
    val localIp: String = "",
    val verificationCode: String = "",
    val formattedCode: String = "",
    val sessionId: String = "",
    val syncProgress: Int = 0,
    val syncProgressText: String = "",
    val syncedAccountCount: Int = 0,
    val errorMessage: String = "",
    val timeoutSeconds: Int = 180,
    val showPasswordDialog: Boolean = false,
    val passwordError: String = ""
)

@HiltViewModel
class LanSyncViewModel @Inject constructor(
    private val lanSyncCrypto: LanSyncCrypto,
    private val accountRepository: AccountRepository,
    private val preferencesRepository: PreferencesRepository,
    private val cryptoManager: CryptoManager,
    private val sessionManager: SessionManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(LanSyncUiState())
    val uiState: StateFlow<LanSyncUiState> = _uiState.asStateFlow()

    private var server: LanSyncServer? = null
    private var timeoutJob: Job? = null
    private var timerJob: Job? = null
    private val port = 8080

    // ─── Start flow ───────────────────────────────────────────────────────────

    fun requestStart() {
        _uiState.value = _uiState.value.copy(
            showPasswordDialog = true,
            passwordError = ""
        )
    }

    fun confirmPasswordAndStart(password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val storedHash = preferencesRepository.getPasswordHash()
            if (storedHash == null || !cryptoManager.verifyPassword(password, storedHash)) {
                _uiState.value = _uiState.value.copy(passwordError = "Mật khẩu không đúng")
                return@launch
            }
            _uiState.value = _uiState.value.copy(showPasswordDialog = false, passwordError = "")
            doStartSession()
        }
    }

    fun dismissPasswordDialog() {
        _uiState.value = _uiState.value.copy(showPasswordDialog = false, passwordError = "")
    }

    // Xử lý QR scan từ laptop (phone quét QR)
    fun handleQrScanned(sessionId: String, laptopPublicKey: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Verify session ID khớp
                if (sessionId != _uiState.value.sessionId) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            step = LanSyncStep.ERROR,
                            errorMessage = "Mã QR không hợp lệ.\nSession ID không khớp với phiên hiện tại."
                        )
                    }
                    return@launch
                }

                // Compute shared secret
                val verCode = lanSyncCrypto.computeSharedSecret(laptopPublicKey)

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        step = LanSyncStep.VERIFYING,
                        verificationCode = verCode,
                        formattedCode = "${verCode.take(2)} ${verCode.drop(2).take(2)} ${verCode.drop(4)}"
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        step = LanSyncStep.ERROR,
                        errorMessage = "Lỗi xử lý QR: ${e.message}"
                    )
                }
            }
        }
    }

    private suspend fun doStartSession() {
        withContext(Dispatchers.Main) {
            _uiState.value = _uiState.value.copy(step = LanSyncStep.STARTING)
        }

        server?.stop()
        lanSyncCrypto.reset()

        val sessionId = UUID.randomUUID().toString().take(8).uppercase()
        val ip = getLocalIpAddress() ?: run {
            _uiState.value = _uiState.value.copy(
                step = LanSyncStep.ERROR,
                errorMessage = "Không tìm được địa chỉ IP Wi-Fi.\nĐảm bảo điện thoại đã kết nối Wi-Fi (không phải Mobile Data)."
            )
            return
        }

        lanSyncCrypto.generateKeyPair()

        server = LanSyncServer(
            port = port,
            crypto = lanSyncCrypto,
            sessionId = sessionId,
            onClientConnected = {
                viewModelScope.launch(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(step = LanSyncStep.CONNECTED)
                }
            },
            onVerificationReady = { code ->
                viewModelScope.launch(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        step = LanSyncStep.VERIFYING,
                        verificationCode = code,
                        formattedCode = "${code.take(2)} ${code.drop(2).take(2)} ${code.drop(4)}"
                    )
                }
            },
            onError = { err ->
                viewModelScope.launch(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(step = LanSyncStep.ERROR, errorMessage = err)
                }
            }
        )

        try {
            server!!.start()
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    step = LanSyncStep.WAITING,
                    serverUrl = "http://$ip:$port",
                    localIp = ip,
                    sessionId = sessionId
                )
            }
            startTimeout()
            startTimer()
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                step = LanSyncStep.ERROR,
                errorMessage = "Không khởi động được server: ${e.message}\nPort $port có thể đang bận."
            )
        }
    }

    // ─── User confirmations ───────────────────────────────────────────────────

    fun confirmVerification() {
        _uiState.value = _uiState.value.copy(step = LanSyncStep.CONFIRMED)
        // Chỉ còn 1 mode là Phone -> Laptop (SEND_ONLY)
        viewModelScope.launch { sendDataToLaptop() }
    }

    fun rejectVerification() {
        stopAndReset()
        _uiState.value = _uiState.value.copy(
            step = LanSyncStep.ERROR,
            errorMessage = "Mã xác nhận không khớp.\nPhiên đã bị hủy để bảo mật."
        )
    }

    fun refreshSession() {
        stopAndReset()
        viewModelScope.launch {
            delay(300)
            doStartSession()
        }
    }

    fun stopAndReset() {
        timeoutJob?.cancel()
        timerJob?.cancel()
        server?.stop()
        server = null
        lanSyncCrypto.reset()
        _uiState.value = LanSyncUiState()
    }

    // ─── Send data Phone → Laptop ─────────────────────────────────────────────

    private suspend fun sendDataToLaptop() {
        updateSync(10, "🔐 Mã hoá dữ liệu...")
        try {
            val payload = buildSyncPayload()
            val payloadBytes = payload.toByteArray(Charsets.UTF_8)
            updateSync(40, "📦 Chuẩn bị truyền...")
            val encrypted = lanSyncCrypto.encryptPayload(payloadBytes)
            updateSync(70, "🚀 Đang truyền dữ liệu...")
            server?.syncPayload?.set(encrypted)
            delay(800)
            updateSync(90, "✅ Xác minh toàn vẹn...")
            delay(500)

            val count = JSONObject(payload).getJSONArray("accounts").length()
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    step = LanSyncStep.SUCCESS, syncProgress = 100,
                    syncProgressText = "Hoàn tất!", syncedAccountCount = count
                )
            }
            stopTimer()
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                step = LanSyncStep.ERROR, errorMessage = "Lỗi gửi: ${e.message}"
            )
        }
    }

    private suspend fun buildSyncPayload(): String {
        val accounts = accountRepository.getAllAccounts()
        val webApps = accountRepository.getAllWebApps()
        val key = sessionManager.requireKey()
        return JSONObject().apply {
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            put("webApps", JSONArray().also { arr ->
                webApps.forEach { w -> arr.put(JSONObject().apply {
                    put("id", w.id); put("name", w.name); put("iconEmoji", w.iconEmoji)
                }) }
            })
            put("accounts", JSONArray().also { arr ->
                accounts.forEach { a ->
                    val pw = try { cryptoManager.decryptString(a.encryptedPassword, key) } catch (e: Exception) { "" }
                    arr.put(JSONObject().apply {
                        put("id", a.id); put("webAppId", a.webAppId)
                        put("title", a.title); put("username", a.username)
                        put("password", pw); put("notes", a.notes)
                    })
                }
            })
        }.toString()
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private suspend fun updateSync(progress: Int, text: String) {
        withContext(Dispatchers.Main) {
            _uiState.value = _uiState.value.copy(syncProgress = progress, syncProgressText = text)
        }
    }

    private fun startTimeout() {
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            delay(180_000L)
            if (_uiState.value.step !in listOf(LanSyncStep.SUCCESS, LanSyncStep.ERROR)) {
                _uiState.value = _uiState.value.copy(
                    step = LanSyncStep.ERROR,
                    errorMessage = "Phiên hết hạn sau 3 phút.\nNhấn 'Làm mới' để tạo phiên mới."
                )
                server?.stop()
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var remaining = 180
            while (remaining > 0 && _uiState.value.step !in listOf(LanSyncStep.SUCCESS, LanSyncStep.ERROR)) {
                delay(1000); remaining--
                _uiState.value = _uiState.value.copy(timeoutSeconds = remaining)
            }
        }
    }

    private fun stopTimer() { timerJob?.cancel(); timeoutJob?.cancel() }

    private fun getLocalIpAddress(): String? {
        try {
            @Suppress("DEPRECATION")
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val ipInt = wifiManager.connectionInfo.ipAddress
            if (ipInt != 0) return String.format(
                "%d.%d.%d.%d",
                ipInt and 0xff, ipInt shr 8 and 0xff,
                ipInt shr 16 and 0xff, ipInt shr 24 and 0xff
            )
        } catch (_: Exception) {}
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList()
                ?.flatMap { it.inetAddresses.toList() }
                ?.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                ?.hostAddress
        } catch (_: Exception) { null }
    }

    override fun onCleared() {
        super.onCleared()
        stopAndReset()
    }
}