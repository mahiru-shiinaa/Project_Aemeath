package com.aemeath.app.ui.lansync.server

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class LanSyncServer(
    port: Int,
    private val crypto: LanSyncCrypto,
    private val sessionId: String,
    private val onClientConnected: () -> Unit,
    private val onVerificationReady: (code: String) -> Unit,
    private val onError: (String) -> Unit
) : NanoHTTPD(port) {

    val isClientConnected = AtomicBoolean(false)
    val partnerPublicKey = AtomicReference<String?>(null)
    val syncPayload = AtomicReference<String?>(null)

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        return try {
            when {
                uri == "/" && method == Method.GET -> serveWebUI()
                uri == "/api/info" && method == Method.GET -> serveInfo()
                uri == "/api/status" && method == Method.GET -> serveStatus()
                uri == "/api/handshake" && method == Method.POST -> handleHandshake(session)
                uri == "/api/passwords" && method == Method.GET -> serveDecryptedPasswords()
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
            }
        } catch (e: Exception) {
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR, "application/json",
                """{"error":"${e.message}"}"""
            )
        }
    }

    private fun generateQrBase64(content: String): String {
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 400, 400)
            val width = bitMatrix.width; val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) for (y in 0 until height)
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) { "" }
    }

    private fun serveInfo(): Response {
        val json = JSONObject().apply {
            put("sessionId", sessionId)
            put("publicKey", crypto.getLocalPublicKeyBase64())
            put("version", 1)
        }.toString()
        return jsonResponse(json)
    }

    private fun serveStatus(): Response {
        val json = JSONObject().apply {
            put("connected", isClientConnected.get())
            put("verificationCode", crypto.getVerificationCode() ?: JSONObject.NULL)
            put("synced", syncPayload.get() != null)
        }.toString()
        return jsonResponse(json)
    }

    private fun handleHandshake(session: IHTTPSession): Response {
        val body = mutableMapOf<String, String>()
        session.parseBody(body)
        val bodyJson = body["postData"] ?: return errorResponse("Missing body")
        val json = JSONObject(bodyJson)
        val clientPublicKey = json.getString("publicKey")

        isClientConnected.set(true)
        partnerPublicKey.set(clientPublicKey)
        onClientConnected()

        val verCode = crypto.computeSharedSecret(clientPublicKey)
        onVerificationReady(verCode)

        val responseJson = JSONObject().apply {
            put("ok", true)
            put("verificationCode", verCode)
        }.toString()
        return jsonResponse(responseJson)
    }

    private fun serveDecryptedPasswords(): Response {
        val encryptedBase64 = syncPayload.get() ?: return errorResponse("No data available")
        return try {
            val decryptedBytes = crypto.decryptPayload(encryptedBase64)
            jsonResponse(String(decryptedBytes, Charsets.UTF_8))
        } catch (e: Exception) {
            errorResponse("Decryption failed: ${e.message}")
        }
    }

    private fun serveWebUI(): Response {
        val serverPublicKey = crypto.getLocalPublicKeyBase64()
        val qrContent = """{"sessionId":"$sessionId","publicKey":"$serverPublicKey"}"""
        val qrBase64Image = generateQrBase64(qrContent)
        val html = buildWebUiHtml(qrBase64Image)
        val response = newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
        response.addHeader("Access-Control-Allow-Origin", "*")
        return response
    }

    private fun buildWebUiHtml(qrBase64Image: String): String = """<!DOCTYPE html>
<html lang="vi">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Aemeath · Secure Vault</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link href="https://fonts.googleapis.com/css2?family=DM+Mono:wght@400;500&family=Sora:wght@300;400;600;700&display=swap" rel="stylesheet">
  <style>
    :root {
      --bg: #080810; --surface: rgba(255,255,255,0.075); --surface-hover: rgba(255,255,255,0.11);
      --border: rgba(255,255,255,0.14); --border-bright: rgba(255,255,255,0.15);
      --accent: #5b7fff; --accent-glow: rgba(91,127,255,0.25); --accent-dim: rgba(91,127,255,0.12);
      --green: #34d399; --green-dim: rgba(52,211,153,0.12);
      --text: #f0f0f8; --text-sec: rgba(240,240,248,0.45); --text-dim: rgba(240,240,248,0.25);
      --mono: 'DM Mono', monospace; --sans: 'Sora', sans-serif;
      --radius: 16px; --radius-sm: 10px;
      --text-cred: rgba(240,240,248,0.82);
    }
    *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
    body { font-family: var(--sans); background: var(--bg); color: var(--text); min-height: 100vh; overflow-x: hidden; }
    body::before {
      content: ''; position: fixed; inset: 0; pointer-events: none; z-index: 0;
      background: radial-gradient(ellipse 80% 50% at 10% 0%, rgba(91,127,255,0.08) 0%, transparent 60%),
                  radial-gradient(ellipse 60% 40% at 90% 100%, rgba(52,211,153,0.06) 0%, transparent 60%);
    }

    /* ── Connect Screen ── */
    #connect-screen { display: flex; align-items: center; justify-content: center; min-height: 100vh; padding: 24px; position: relative; z-index: 1; }
    .connect-card {
      background: rgba(255,255,255,0.03); border: 1px solid var(--border); border-radius: 28px;
      padding: 40px 36px; width: 100%; max-width: 400px; text-align: center;
      backdrop-filter: blur(20px); box-shadow: 0 40px 80px rgba(0,0,0,0.6), inset 0 1px 0 rgba(255,255,255,0.06);
      animation: cardIn 0.6s cubic-bezier(0.22,1,0.36,1);
    }
    @keyframes cardIn { from { opacity:0; transform: translateY(24px) scale(0.97); } to { opacity:1; transform: translateY(0) scale(1); } }
    .brand { display:flex; align-items:center; justify-content:center; gap:10px; margin-bottom:6px; }
    .brand-icon {
      width:36px; height:36px; background:var(--accent-dim); border:1px solid rgba(91,127,255,0.3);
      border-radius:10px; display:flex; align-items:center; justify-content:center; font-size:18px;
    }
    .brand-name { font-size:22px; font-weight:700; letter-spacing:-0.5px; }
    .connect-subtitle { font-size:13px; color:var(--text-sec); margin-bottom:28px; font-weight:300; }
    .qr-wrapper { display:flex; justify-content:center; margin-bottom:24px; }
    .qr-frame { padding:12px; background:white; border-radius:16px; box-shadow:0 0 0 1px rgba(255,255,255,0.1), 0 8px 32px rgba(0,0,0,0.4); }
    #qr-img { width:160px; height:160px; display:block; border-radius:4px; }
    .steps { display:flex; flex-direction:column; gap:8px; margin-bottom:20px; text-align:left; }
    .step { display:flex; align-items:flex-start; gap:10px; font-size:13px; color:var(--text-sec); line-height:1.4; }
    .step-num {
      flex-shrink:0; width:20px; height:20px; background:var(--accent-dim);
      border:1px solid rgba(91,127,255,0.25); border-radius:50%;
      font-size:11px; font-weight:600; color:var(--accent);
      display:flex; align-items:center; justify-content:center; margin-top:1px;
    }
    #status {
      display:flex; align-items:center; justify-content:center; gap:8px;
      padding:12px 16px; border-radius:var(--radius-sm); background:var(--surface);
      border:1px solid var(--border); font-size:13px; font-weight:500; color:var(--text-sec); transition:all 0.4s;
    }
    #status.connected { background:var(--green-dim); border-color:rgba(52,211,153,0.25); color:var(--green); }
    #verify-box {
      display:none; margin-top:16px; padding:16px; border-radius:var(--radius-sm);
      background:rgba(91,127,255,0.06); border:1px solid rgba(91,127,255,0.2);
      animation:slideDown 0.3s ease;
    }
    @keyframes slideDown { from { opacity:0; transform:translateY(-8px); } to { opacity:1; transform:translateY(0); } }
    .verify-label { font-size:11px; font-weight:600; letter-spacing:1px; text-transform:uppercase; color:var(--accent); margin-bottom:8px; }
    #code { font-family:var(--mono); font-size:28px; font-weight:500; letter-spacing:8px; color:var(--text); }
    .verify-hint { font-size:11px; color:var(--text-dim); margin-top:6px; }

    /* ── Dashboard ── */
    #dashboard { display:none; min-height:100vh; position:relative; z-index:1; }
    .topbar {
      position:sticky; top:0; z-index:100; background:rgba(8,8,16,0.85); backdrop-filter:blur(20px);
      border-bottom:1px solid var(--border); padding:0 32px; height:64px;
      display:flex; align-items:center; justify-content:space-between;
    }
    .topbar-brand { display:flex; align-items:center; gap:10px; }
    .topbar-brand .brand-icon { width:30px; height:30px; font-size:14px; }
    .topbar-brand .brand-name { font-size:17px; }
    .pill {
      padding:4px 10px; border-radius:20px; background:var(--green-dim);
      border:1px solid rgba(52,211,153,0.2); color:var(--green);
      font-size:11px; font-weight:600; display:flex; align-items:center; gap:4px;
    }
    .pill::before { content:''; width:6px; height:6px; border-radius:50%; background:var(--green); animation:pulse 2s infinite; }
    @keyframes pulse { 0%,100% { opacity:1; } 50% { opacity:0.4; } }
    .content { max-width:1100px; margin:0 auto; padding:32px 24px 80px; }
    .content-title { font-size:26px; font-weight:700; letter-spacing:-0.5px; margin-bottom:4px; }
    .content-sub { font-size:14px; color:var(--text-sec); margin-bottom:24px; }
    .search-wrap { position:relative; margin-bottom:20px; }
    .search-icon { position:absolute; left:16px; top:50%; transform:translateY(-50%); color:var(--text-dim); font-size:16px; pointer-events:none; }
    #search {
      width:100%; background:var(--surface); border:1px solid var(--border); border-radius:var(--radius);
      padding:14px 16px 14px 44px; color:var(--text); font-size:15px; font-family:var(--sans); outline:none; transition:all 0.2s;
    }
    #search::placeholder { color:var(--text-dim); }
    #search:focus { border-color:rgba(91,127,255,0.4); background:rgba(255,255,255,0.05); box-shadow:0 0 0 3px var(--accent-glow); }
    .stats-row { display:flex; gap:10px; margin-bottom:24px; flex-wrap:wrap; }
    .stat-chip {
      padding:7px 14px; background:var(--surface); border:1px solid var(--border);
      border-radius:20px; font-size:13px; color:var(--text-sec); display:flex; align-items:center; gap:6px;
    }
    .stat-chip strong { color:var(--text); font-weight:600; }
    .grid { display:grid; grid-template-columns:repeat(auto-fill, minmax(300px, 1fr)); gap:14px; }
    .account-card {
      background:var(--surface); border:1px solid var(--border); border-radius:var(--radius);
      padding:18px; transition:all 0.2s cubic-bezier(0.22,1,0.36,1); animation:cardAppear 0.4s ease both;
      box-shadow:
        inset 0 1px 0 rgba(255,255,255,0.05),
        0 10px 30px rgba(0,0,0,0.35);
    }
    @keyframes cardAppear { from { opacity:0; transform:translateY(12px); } to { opacity:1; transform:translateY(0); } }
    .account-card:hover { background:var(--surface-hover); border-color:var(--border-bright); transform:translateY(-2px); box-shadow:0 12px 32px rgba(0,0,0,0.3); }
    .card-header { display:flex; align-items:center; gap:12px; margin-bottom:14px; }
    .app-icon {
      width:42px; height:42px; background:rgba(255,255,255,0.06); border:1px solid var(--border);
      border-radius:12px; display:flex; align-items:center; justify-content:center; font-size:22px; flex-shrink:0;
    }
    .card-title-wrap { min-width:0; }
    .card-app-name { font-size:11px; font-weight:600; color:var(--accent); text-transform:uppercase; letter-spacing:0.5px; margin-bottom:2px; }
    .card-account-title { font-size:15px; font-weight:600; color:var(--text); white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
    .card-divider { height:1px; background:var(--border); margin-bottom:14px; }
    .field { margin-bottom:10px; }
    .field:last-child { margin-bottom:0; }
    .field-label { font-size:10px; font-weight:600; letter-spacing:1px; text-transform:uppercase; color:var(--text-dim); margin-bottom:5px; }
    .field-row {
      display:flex; align-items:center; gap:6px; background:rgba(255,255,255,0.04);
      border:1px solid rgba(255,255,255,0.05); border-radius:var(--radius-sm); padding:8px 10px; min-width:0;
    }
    .field-value {
    overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
  font-family: var(--mono);
  font-size: 13px;
  color: var(--text-cred);
  font-weight: 500;
}


    .field-value.masked {
        letter-spacing: 3px;
        color: rgba(240,240,248,0.9);
    }
    
    .icon-btn {
      flex-shrink:0; width:28px; height:28px; background:transparent; border:none; border-radius:7px;
      cursor:pointer; color:var(--text-sec); display:flex; align-items:center; justify-content:center;
      transition:all 0.15s; font-size:14px; line-height:1;
    }
    .icon-btn:hover { background:rgba(255,255,255,0.08); color:var(--text); }
    .icon-btn:active { transform:scale(0.9); }
    .icon-btn.copied { color:var(--green); }
    .empty { grid-column:1/-1; text-align:center; padding:60px 20px; color:var(--text-dim); }
    .empty-icon { font-size:48px; margin-bottom:12px; }
    #toast {
      position:fixed; bottom:32px; left:50%; transform:translateX(-50%) translateY(20px);
      background:rgba(52,211,153,0.15); border:1px solid rgba(52,211,153,0.3); color:var(--green);
      padding:10px 20px; border-radius:30px; font-size:13px; font-weight:600;
      opacity:0; transition:all 0.3s cubic-bezier(0.22,1,0.36,1); pointer-events:none;
      backdrop-filter:blur(10px); white-space:nowrap; z-index:999;
    }
    #toast.show { opacity:1; transform:translateX(-50%) translateY(0); }
    .spinner { width:14px; height:14px; border:2px solid rgba(255,255,255,0.1); border-top-color:var(--accent); border-radius:50%; animation:spin 0.8s linear infinite; flex-shrink:0; }
    @keyframes spin { to { transform:rotate(360deg); } }
    @media (max-width:600px) { .topbar { padding:0 16px; } .content { padding:20px 16px 60px; } .grid { grid-template-columns:1fr; } }
  </style>
</head>
<body>

<div id="connect-screen">
  <div class="connect-card">
    <div class="brand">
      <div class="brand-icon">🔐</div>
      <div class="brand-name">Aemeath</div>
    </div>
    <p class="connect-subtitle">Quét mã QR để xem kho mật khẩu trên máy tính</p>
    <div class="qr-wrapper">
      <div class="qr-frame">
        <img id="qr-img" src="data:image/png;base64,$qrBase64Image" alt="QR Code" />
      </div>
    </div>
    <div class="steps">
      <div class="step"><div class="step-num">1</div><span>Mở app Aemeath → chọn <strong>LAN Sync</strong></span></div>
      <div class="step"><div class="step-num">2</div><span>Nhấn <strong>Quét QR</strong> và quét mã bên trên</span></div>
      <div class="step"><div class="step-num">3</div><span>So sánh mã xác nhận xuất hiện ở cả hai thiết bị</span></div>
    </div>
    <div id="status"><div class="spinner"></div><span>Đang chờ kết nối...</span></div>
    <div id="verify-box">
      <div class="verify-label">🔑 Mã xác nhận</div>
      <div id="code">------</div>
      <div class="verify-hint">Xác nhận trên điện thoại để tiếp tục</div>
    </div>
  </div>
</div>

<div id="dashboard">
  <div class="topbar">
    <div class="topbar-brand">
      <div class="brand-icon">🔐</div>
      <div class="brand-name">Aemeath</div>
    </div>
    <div class="pill">Đã kết nối</div>
  </div>
  <div class="content">
    <div class="content-title">Kho Mật Khẩu</div>
    <div class="content-sub">Phiên truy cập tạm thời · Dữ liệu không được lưu trên máy này</div>
    <div class="search-wrap">
      <div class="search-icon">🔍</div>
      <input type="text" id="search" placeholder="Tìm theo tên app, username, tiêu đề..." autocomplete="off" />
    </div>
    <div class="stats-row" id="stats-row"></div>
    <div class="grid" id="grid"></div>
  </div>
</div>

<div id="toast">✓ Đã sao chép!</div>

<script>
  let allAccounts = [];
  let webApps = {};

  (async () => {
    while (true) {
      try {
        const r = await fetch('/api/status');
        const d = await r.json();
        if (d.connected) {
          const s = document.getElementById('status');
          s.className = 'connected';
          s.innerHTML = '<span>✅ Đã kết nối · Đang chờ đồng bộ...</span>';
        }
        if (d.verificationCode) {
          document.getElementById('verify-box').style.display = 'block';
          const c = d.verificationCode;
          document.getElementById('code').textContent = c.slice(0,2)+' '+c.slice(2,4)+' '+c.slice(4,6);
        }
        if (d.synced) { await loadData(); return; }
      } catch(e) {}
      await new Promise(r => setTimeout(r, 1500));
    }
  })();

  async function loadData() {
    try {
      const r = await fetch('/api/passwords');
      const data = await r.json();
      data.webApps.forEach(w => webApps[w.id] = w);
      allAccounts = data.accounts;
      document.getElementById('connect-screen').style.display = 'none';
      document.getElementById('dashboard').style.display = 'block';
      const apps = new Set(allAccounts.map(a => a.webAppId)).size;
      document.getElementById('stats-row').innerHTML =
        '<div class="stat-chip"><strong>' + allAccounts.length + '</strong>&nbsp;tài khoản</div>' +
        '<div class="stat-chip"><strong>' + apps + '</strong>&nbsp;ứng dụng</div>';
      render(allAccounts);
    } catch(e) { alert('Lỗi tải dữ liệu: ' + e.message); }
  }

  function render(accounts) {
    const grid = document.getElementById('grid');
    grid.innerHTML = '';
    if (accounts.length === 0) {
      grid.innerHTML = '<div class="empty"><div class="empty-icon">🔍</div><div>Không tìm thấy tài khoản nào</div></div>';
      return;
    }
    accounts.forEach((acc, i) => {
      const w = webApps[acc.webAppId] || { name: 'Unknown', iconEmoji: '❓' };
      const card = document.createElement('div');
      card.className = 'account-card';
      card.style.animationDelay = (i * 0.04) + 's';
      card.innerHTML =
        '<div class="card-header">' +
          '<div class="app-icon">' + w.iconEmoji + '</div>' +
          '<div class="card-title-wrap">' +
            '<div class="card-app-name">' + esc(w.name) + '</div>' +
            '<div class="card-account-title">' + esc(acc.title || acc.username) + '</div>' +
          '</div>' +
        '</div>' +
        '<div class="card-divider"></div>' +
        '<div class="field">' +
          '<div class="field-label">Username</div>' +
          '<div class="field-row">' +
            '<div class="field-value">' + esc(acc.username) + '</div>' +
            '<button class="icon-btn" title="Sao chép" data-copy="' + escAttr(acc.username) + '">📋</button>' +
          '</div>' +
        '</div>' +
        '<div class="field">' +
          '<div class="field-label">Password</div>' +
          '<div class="field-row">' +
            '<div class="field-value masked" id="pw-' + i + '">••••••••</div>' +
            '<button class="icon-btn" id="eye-' + i + '" data-pw="' + escAttr(acc.password || '') + '" data-idx="' + i + '" title="Hiện/ẩn">👁</button>' +
            '<button class="icon-btn" title="Sao chép" data-copy="' + escAttr(acc.password || '') + '">📋</button>' +
          '</div>' +
        '</div>';
      grid.appendChild(card);
    });

    // Delegated events
    grid.onclick = function(e) {
      const btn = e.target.closest('.icon-btn');
      if (!btn) return;
      if (btn.dataset.copy !== undefined) { copyText(btn.dataset.copy, btn); return; }
      if (btn.dataset.idx !== undefined) {
        const idx = btn.dataset.idx;
        const pw = btn.dataset.pw;
        const el = document.getElementById('pw-' + idx);
        if (el.classList.contains('masked')) {
          el.textContent = pw || '(trống)';
          el.classList.remove('masked');
          btn.textContent = '🙈';
        } else {
          el.textContent = '••••••••';
          el.classList.add('masked');
          btn.textContent = '👁';
        }
      }
    };
  }

  document.getElementById('search').addEventListener('input', function(e) {
    const q = e.target.value.toLowerCase().trim();
    if (!q) { render(allAccounts); return; }
    render(allAccounts.filter(acc => {
      const w = webApps[acc.webAppId] || {};
      return (acc.username||'').toLowerCase().includes(q) ||
             (acc.title||'').toLowerCase().includes(q) ||
             (w.name||'').toLowerCase().includes(q);
    }));
  });

  function copyText(text, btn) {
    if (!text) return;
    const done = () => {
      const orig = btn.textContent;
      btn.textContent = '✓'; btn.classList.add('copied');
      setTimeout(() => { btn.textContent = orig; btn.classList.remove('copied'); }, 1500);
      const t = document.getElementById('toast');
      t.classList.add('show'); setTimeout(() => t.classList.remove('show'), 2000);
    };
    if (navigator.clipboard && window.isSecureContext) {
      navigator.clipboard.writeText(text).then(done).catch(() => fallbackCopy(text, done));
    } else { fallbackCopy(text, done); }
  }

  function fallbackCopy(text, cb) {
    const ta = document.createElement('textarea');
    ta.value = text;
    ta.style.cssText = 'position:fixed;top:-9999px;left:-9999px;opacity:0';
    document.body.appendChild(ta);
    ta.focus(); ta.select();
    try { document.execCommand('copy'); cb(); } catch(e) {}
    document.body.removeChild(ta);
  }

  function esc(s) {
    return String(s||'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
  }
  function escAttr(s) {
    return String(s||'').replace(/&/g,'&amp;').replace(/"/g,'&quot;').replace(/'/g,'&#39;');
  }
</script>
</body>
</html>"""

    private fun jsonResponse(json: String): Response {
        val r = newFixedLengthResponse(Response.Status.OK, "application/json", json)
        r.addHeader("Access-Control-Allow-Origin", "*")
        r.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        r.addHeader("Access-Control-Allow-Headers", "Content-Type")
        return r
    }

    private fun errorResponse(msg: String): Response {
        return newFixedLengthResponse(
            Response.Status.BAD_REQUEST, "application/json",
            """{"error":"$msg"}"""
        )
    }
}