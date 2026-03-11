<div align="center">

<img src="app/src/main/res/drawable/logo.png" width="120" height="120" style="border-radius: 28px;" alt="Aemeath Logo"/>

# 🔐 Aemeath

### Trình quản lý mật khẩu offline, bảo mật cao cho Android

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.06-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![AES-256](https://img.shields.io/badge/Encryption-AES--256--GCM-FF6B6B)](https://en.wikipedia.org/wiki/Galois/Counter_Mode)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)
[![API](https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-orange)](https://apilevels.com)

**Aemeath** là một ứng dụng quản lý mật khẩu **hoàn toàn offline**, **mã hóa AES-256-GCM end-to-end**, không có server trung gian, với tính năng **LAN Sync** bảo mật cho phép xem mật khẩu trực tiếp trên trình duyệt máy tính trong cùng mạng nội bộ.

[📦 Tải APK](#-cài-đặt) · [🚀 Tính năng](#-tính-năng) · [🔒 Bảo mật](#-nguyên-lý-bảo-mật) · [🏗️ Kiến trúc](#️-kiến-trúc) · [📡 LAN Sync](#-lan-sync)

</div>

---

## 📸 Giao diện

| Màn hình Unlock | Trang Chủ | Danh sách Tài khoản | LAN Sync |
|:-:|:-:|:-:|:-:|
| <img src="docs/screenshots/unlock.png" width="180"/> | <img src="docs/screenshots/home.png" width="180"/> | <img src="docs/screenshots/accounts.png" width="180"/> | <img src="docs/screenshots/lansync.png" width="180"/> |

> *Hỗ trợ giao diện Sáng / Tối / Theo hệ thống với Material Design 3.*

---

## ✨ Tính năng

### 🔑 Quản lý mật khẩu
- ✅ Lưu trữ mật khẩu theo nhóm **Web / Ứng dụng** với emoji icon tùy chỉnh
- ✅ Tìm kiếm **real-time** (debounce 300ms) theo tên app, username, tiêu đề
- ✅ **Sắp xếp** A→Z, Z→A, Mới nhất, Sửa gần nhất
- ✅ Chế độ xem **Danh sách** & **Lưới**
- ✅ **Kéo thả** để sắp xếp thứ tự tài khoản
- ✅ **Chọn nhiều** → Xóa hàng loạt
- ✅ Swipe-to-delete với **Undo** snackbar
- ✅ **Copy password** → Tự động xóa clipboard sau 30 giây
- ✅ **QR Share** tài khoản (tự hủy sau 60 giây)

### 🛡️ Xác thực & Bảo mật
- ✅ **Master Password** với PBKDF2 (310.000 iterations)
- ✅ **Mở khóa vân tay** (BiometricPrompt API + Android Keystore)
- ✅ Khóa **5 lần sai** → lockout 30 giây (chống brute force)
- ✅ Auto-lock khi vào background
- ✅ **Đổi Master Password** + tự động re-encrypt toàn bộ dữ liệu

### 💾 Backup & Restore
- ✅ Xuất file **`.aem`** — mã hóa AES-256 với mật khẩu backup riêng
- ✅ Xuất / Nhập **CSV** và **Google CSV** (tương thích Chrome Password Manager)
- ✅ Chế độ **Merge** (gộp) hoặc **Overwrite** (ghi đè) khi import
- ✅ Tương thích ngược với định dạng backup V1/V2

### 📡 LAN Sync
- ✅ Xem mật khẩu trực tiếp trên **trình duyệt máy tính** qua Wi-Fi nội bộ
- ✅ Mã hóa **ECDH key exchange** + xác nhận mã 6 số
- ✅ **Không cần cài phần mềm** trên máy tính
- ✅ Phiên tự hủy sau 3 phút

### 🎨 UI/UX
- ✅ **Material Design 3** với động lực mượt mà
- ✅ Radar animation, Step timeline, Password strength bar
- ✅ **Haptic feedback** khi copy, confirm, error
- ✅ Empty states và loading states đầy đủ

---

## 🔒 Nguyên lý Bảo mật

### 1. Mã hóa dữ liệu lưu trữ

Mọi mật khẩu được mã hóa bằng **AES-256-GCM** trước khi lưu vào database.

```
Master Password + Salt (32 bytes)
        │
        ▼ PBKDF2WithHmacSHA256
        │  310.000 iterations (OWASP 2023)
        │  Key length: 256-bit
        ▼
   Encryption Key (chỉ tồn tại trong RAM)
        │
        ├─► encrypt(password) ──► IV(12B) + Ciphertext + GCM Tag(16B)
        │                                 └──────────────────────────┘
        │                                     Base64 → lưu vào DB
        │
        └─► Khi app lock → Key bị xóa khỏi RAM
```

**Định dạng lưu:** `Base64(IV[12 bytes] + Ciphertext + GCM_Tag[16 bytes])`

### 2. Quản lý Session (SessionManager)

```kotlin
// Key CHỈ tồn tại trong memory, KHÔNG bao giờ ghi xuống disk
private var _encryptionKey: SecretKey? = null

// Auto-lock khi onStop()
fun lock() {
    _encryptionKey = null   // GC sẽ thu hồi
    _isUnlocked.value = false
}
```

| Sự kiện | Hành động |
|---------|-----------|
| App vào background | Tự động lock, xóa key khỏi RAM |
| Sai mật khẩu 5 lần | Lockout 30 giây |
| Copy password | Tự động xóa clipboard sau 30 giây |
| Đổi Master Password | Re-encrypt toàn bộ DB với key mới |

### 3. Xác thực bằng Vân tay (Android Keystore)

```
Biometric Key ──────────────────────── Android Keystore (TEE/Secure Enclave)
     │                                        │
     │  setUserAuthenticationRequired(true)   │
     │  setInvalidatedByBiometricEnrollment   │
     ▼                                        │
BiometricPrompt ◄────────── Xác thực ─────────┘
     │
     ▼ Thành công
SessionManager.unlock(sessionKey)
```

### 4. LAN Sync — ECDH Key Exchange

```
PHONE (Server)                    LAPTOP (Browser)
      │                                  │
      ├─ Sinh ECDH key pair (secp256r1) ─┤
      │                                  │
      │◄──── GET /api/info ──────────────┤
      │  {sessionId, phonePublicKey}     │
      │                                  │
      │                          Sinh ECDH key pair
      │                                  │
      │◄──── POST /api/handshake ────────┤
      │    {laptopPublicKey}             │
      │                                  │
      ├─ ECDH compute sharedSecret ──────┤
      │                                  │
      │  verificationCode = SHA256(sharedSecret)[0:6 digits]
      │                                  │
    [User so sánh 6 số trên 2 màn hình và xác nhận]
      │                                  │
      │  sessionKey = SHA256(sharedSecret + "AEMEATH_SESSION_KEY")
      │                                  │
      ├─ AES-GCM encrypt payload ────────►
      │                                  │
```

**Tại sao an toàn?**
- Khóa phiên được tạo mới mỗi lần, không bao giờ tái sử dụng
- Mã 6 số xác nhận đảm bảo không có MITM attack
- Payload mã hóa AES-256-GCM trước khi truyền
- Phiên tự hủy sau 3 phút, không lưu lại gì trên máy tính

### 5. Điều không bao giờ làm

```
❌ Không lưu plaintext password xuống disk
❌ Không lưu encryption key xuống disk  
❌ Không upload data lên bất kỳ server nào
❌ Không dùng allowBackup=true trong Manifest
❌ Không log password hoặc key ra Logcat
❌ Không dùng AES-ECB mode (chỉ dùng GCM)
❌ Không dùng MD5, SHA1 cho mật khẩu
```

---

## 🏗️ Kiến trúc

### Pattern

```
View (Jetpack Compose)
    │  collectAsState / StateFlow
    ▼
ViewModel (Hilt @HiltViewModel)
    │  coroutines / Flow
    ▼
Repository
    │
    ├──► Room Database (AccountDao, WebAppDao)
    ├──► DataStore (PreferencesRepository)
    └──► CryptoManager / SessionManager
```

**MVVM + Repository Pattern + Dependency Injection (Hilt)**

### Cấu trúc thư mục

```
com.aemeath.app/
├── AemeathApp.kt                  # @HiltAndroidApp
├── MainActivity.kt                # NavHost, Theme
│
├── data/
│   ├── db/
│   │   ├── AemeathDatabase.kt     # Room @Database (v2)
│   │   ├── dao/
│   │   │   └── Daos.kt            # WebAppDao, AccountDao, AppSettingDao
│   │   └── entity/
│   │       └── Entities.kt        # WebAppEntity, AccountEntity
│   └── repository/
│       ├── AccountRepository.kt   # CRUD + encrypt/decrypt
│       └── PreferencesRepository.kt # DataStore settings
│
├── di/
│   └── AppModule.kt               # Hilt @Module — Room, DAOs
│
├── navigation/
│   └── Screen.kt                  # Sealed class routes
│
├── security/
│   ├── CryptoManager.kt           # AES-256-GCM, PBKDF2, Biometric
│   └── SessionManager.kt          # In-memory key, lockout logic
│
└── ui/
    ├── auth/
    │   ├── SetupScreen.kt + SetupViewModel.kt
    │   └── UnlockScreen.kt + UnlockViewModel.kt
    ├── home/
    │   ├── HomeScreen.kt + HomeViewModel.kt
    │   └── (WebAppListItem, GridItem, SearchBar, StatsCard)
    ├── account/
    │   ├── AccountListScreen.kt + AccountListViewModel.kt
    │   └── AddEditAccountScreen.kt + AddEditAccountViewModel.kt
    ├── settings/
    │   ├── SettingsScreen.kt + SettingsViewModel.kt
    │   └── ChangePasswordScreen.kt + ChangePasswordViewModel.kt
    ├── backup/
    │   └── BackupScreen.kt + BackupViewModel.kt
    ├── lansync/
    │   ├── LanSyncScreen.kt + LanSyncViewModel.kt
    │   ├── QRScannerScreen.kt
    │   └── server/
    │       ├── LanSyncServer.kt   # NanoHTTPD
    │       └── LanSyncCrypto.kt   # ECDH, AES session
    ├── main/
    │   └── MainViewModel.kt
    └── theme/
        ├── Color.kt · Theme.kt · Type.kt
        └── SoftCardModifier.kt
```

---

## 🗄️ Cấu trúc Database

### Bảng `web_apps`

| Cột | Kiểu | Mô tả |
|-----|------|-------|
| `id` | `Long` (PK autoGenerate) | ID tự tăng |
| `name` | `String` | Tên ứng dụng (VD: "Facebook") |
| `iconEmoji` | `String` | Emoji icon (mặc định `"🌐"`) |
| `iconBase64` | `String?` | Ảnh icon tùy chỉnh (nullable) |
| `createdAt` | `Long` | Timestamp tạo |
| `updatedAt` | `Long` | Timestamp sửa |

### Bảng `accounts`

| Cột | Kiểu | Mô tả |
|-----|------|-------|
| `id` | `Long` (PK autoGenerate) | ID tự tăng |
| `webAppId` | `Long` (FK) | Liên kết tới `web_apps.id` (CASCADE DELETE) |
| `title` | `String` | Tiêu đề tài khoản |
| `username` | `String` | Tên đăng nhập |
| `encryptedPassword` | `String` | Mật khẩu đã mã hóa AES-256-GCM (Base64) |
| `notes` | `String` | Ghi chú |
| `position` | `Int` | Thứ tự hiển thị (kéo thả) |
| `createdAt` | `Long` | Timestamp tạo |
| `updatedAt` | `Long` | Timestamp sửa |

### Bảng `app_settings`

| Cột | Kiểu | Mô tả |
|-----|------|-------|
| `key` | `String` (PK) | Khóa cài đặt |
| `value` | `String` | Giá trị |

### DataStore (Preferences)

| Key | Kiểu | Mô tả |
|-----|------|-------|
| `is_setup_complete` | `Boolean` | Đã thiết lập lần đầu chưa |
| `password_hash` | `String` | Hash để xác minh Master Password |
| `encryption_salt` | `String` | Salt PBKDF2 (Base64) |
| `biometric_enabled` | `Boolean` | Vân tay có bật không |
| `biometric_encrypted_key` | `String` | Session key backup cho vân tay |
| `theme` | `String` | `"light"` / `"dark"` / `"system"` |
| `auto_lock_minutes` | `Int` | Thời gian tự khóa |
| `list_view_mode` | `String` | `"list"` / `"grid"` |

---

## ⚙️ Công nghệ sử dụng

| Thư viện | Phiên bản | Mục đích |
|----------|-----------|---------|
| **Kotlin** | 1.9.24 | Ngôn ngữ lập trình |
| **Jetpack Compose** | BOM 2024.06.00 | UI framework declarative |
| **Material Design 3** | — | Design system |
| **Room** | 2.6.1 | SQLite database ORM |
| **Hilt** | 2.51.1 | Dependency Injection |
| **KSP** | 1.9.24-1.0.20 | Annotation processing |
| **DataStore Preferences** | 1.1.1 | Key-value persistent storage |
| **androidx.biometric** | 1.1.0 | BiometricPrompt API |
| **Navigation Compose** | 2.7.7 | In-app navigation |
| **ML Kit Barcode** | — | QR code scanning |
| **ZXing** | 4.3.0 | QR code generation |
| **NanoHTTPD** | 2.3.1 | Local HTTP server (LAN Sync) |
| **Coroutines** | 1.8.1 | Async/concurrent programming |
| **Accompanist** | 0.34.0 | System UI utilities |
| **Splashscreen** | 1.0.1 | Splash screen API |

### Build Config

```kotlin
agp                          = "8.5.1"
kotlin                       = "1.9.24"
ksp                          = "1.9.24-1.0.20"
compileSdk                   = 34
targetSdk                    = 34
minSdk                       = 26  // Android 8.0+
kotlinCompilerExtensionVersion = "1.5.14"
```

---

## 🚀 Cài đặt & Chạy

### Yêu cầu

| Công cụ | Phiên bản |
|---------|-----------|
| Android Studio | Koala 2024.1.1 Patch 1 trở lên |
| JDK | 17 |
| Android SDK | API 26+ |
| OS | Windows / macOS / Linux |

### Clone & Build

```bash
# 1. Clone repository
git clone https://github.com/your-username/aemeath.git
cd aemeath

# 2. Mở Android Studio → File → Open → chọn thư mục vừa clone

# 3. Sync Gradle (Android Studio tự làm khi mở)
./gradlew assembleDebug

# 4. Cài lên thiết bị (kết nối USB + bật USB Debugging)
./gradlew installDebug
```

### Build APK Release

```bash
# Build APK release (unsigned)
./gradlew assembleRelease

# APK sẽ nằm tại:
# app/build/outputs/apk/release/app-release-unsigned.apk
```

> **Lưu ý:** Để chia sẻ APK cho người khác, bạn cần ký APK bằng keystore. Xem hướng dẫn [ký APK](#-ký-apk-release).

### Ký APK Release

```bash
# 1. Tạo keystore (chỉ làm 1 lần)
keytool -genkey -v -keystore aemeath-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias aemeath

# 2. Ký APK
jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 \
  -keystore aemeath-release.jks \
  app/build/outputs/apk/release/app-release-unsigned.apk \
  aemeath

# 3. Zipalign (tối ưu)
zipalign -v 4 \
  app/build/outputs/apk/release/app-release-unsigned.apk \
  aemeath-release.apk
```

---

## 📦 Đưa APK lên GitHub Releases

### Cách 1: Qua GitHub Website (Đơn giản nhất)

**Bước 1:** Build APK release (xem phần trên)

**Bước 2:** Truy cập repository trên GitHub → Tab **"Releases"** → **"Create a new release"**

**Bước 3:** Điền thông tin:
```
Tag version : v1.0.0
Release title: Aemeath v1.0.0
Description  : (Mô tả các thay đổi)
```

**Bước 4:** Kéo thả file `aemeath-release.apk` vào ô **"Attach binaries"**

**Bước 5:** Nhấn **"Publish release"** ✅

---

### Cách 2: Qua GitHub CLI (Nhanh hơn)

```bash
# Cài GitHub CLI (nếu chưa có)
# Windows: winget install GitHub.cli
# macOS:   brew install gh

# Đăng nhập
gh auth login

# Tạo release và upload APK
gh release create v1.0.0 \
  ./aemeath-release.apk \
  --title "Aemeath v1.0.0" \
  --notes "## 🎉 Aemeath v1.0.0

### Tính năng
- Quản lý mật khẩu offline với AES-256-GCM
- LAN Sync qua ECDH key exchange  
- Backup/Restore (.aem, CSV, Google CSV)
- Mở khóa vân tay

### Cài đặt
1. Tải file APK bên dưới
2. Trên điện thoại: Cài đặt → Bảo mật → Cho phép cài từ nguồn không rõ
3. Mở file APK và cài đặt"
```

---

### Cách 3: GitHub Actions (Tự động hóa)

Tạo file `.github/workflows/release.yml`:

```yaml
name: Build & Release APK

on:
  push:
    tags:
      - 'v*'   # Kích hoạt khi push tag dạng v1.0.0

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Setup JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Android SDK
        uses: android-actions/setup-android@v3

      - name: Build Release APK
        run: ./gradlew assembleRelease

      - name: Sign APK
        uses: r0adkll/sign-android-release@v1
        with:
          releaseDirectory: app/build/outputs/apk/release
          signingKeyBase64: ${{ secrets.SIGNING_KEY }}
          alias: ${{ secrets.KEY_ALIAS }}
          keyStorePassword: ${{ secrets.KEY_STORE_PASSWORD }}
          keyPassword: ${{ secrets.KEY_PASSWORD }}

      - name: Upload to GitHub Releases
        uses: softprops/action-gh-release@v1
        with:
          files: app/build/outputs/apk/release/app-release-signed.apk
          name: "Aemeath ${{ github.ref_name }}"
          generate_release_notes: true
```

> **Cách lưu secrets:** GitHub repo → Settings → Secrets and variables → Actions → New repository secret

**Cách tạo tag để kích hoạt:**
```bash
git tag v1.0.0
git push origin v1.0.0
```

---

## 📱 Cách cài APK từ Releases

Hướng dẫn cho người dùng cuối:

1. Vào trang **Releases** → tải file `.apk` mới nhất
2. Trên điện thoại Android: **Cài đặt → Bảo mật → Nguồn không rõ → Bật**
   - Hoặc: **Cài đặt → Ứng dụng → Cài đặt đặc biệt → Cài từ nguồn khác**
3. Mở file APK vừa tải và nhấn **Cài đặt**
4. Mở ứng dụng → Tạo Master Password → Xong!

---

## 🗺️ Roadmap

- [x] Phase 1 — Auth (Setup + Unlock) + Database + Navigation
- [x] Phase 2 — Home Screen + Add/Edit Account + Password Generator
- [x] Phase 3 — Backup/Restore + Settings + Change Password
- [x] Phase 4 — LAN Sync (HTTP Server + QR + ECDH + Sync)
- [ ] Phase 5 — Polish (Animations + Micro-interactions + Edge cases)
- [ ] Auto-backup theo lịch
- [ ] Widget màn hình khóa
- [ ] Import từ Bitwarden, 1Password
- [ ] Tìm kiếm mật khẩu trùng / yếu

---

## 🤝 Đóng góp

Pull requests luôn được chào đón! Vui lòng:

1. Fork repository
2. Tạo branch mới: `git checkout -b feature/ten-tinh-nang`
3. Commit: `git commit -m "feat: thêm tính năng X"`
4. Push: `git push origin feature/ten-tinh-nang`
5. Mở Pull Request

---

## 📄 Chính sách bảo mật

- **Không có server:** Toàn bộ dữ liệu lưu trên thiết bị của bạn
- **Không có analytics:** Không theo dõi hành vi người dùng
- **Không backup tự động lên cloud:** `android:allowBackup="false"`
- **Mã nguồn mở:** Bạn có thể kiểm tra toàn bộ code

---

## 📜 License

```
MIT License — Copyright (c) 2026 hieuj2k4

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction...
```

---

<div align="center">

**Được xây dựng với ❤️ bởi [@hieuj2k4](https://github.com/hieuj2k4)**

*AES-256-GCM · PBKDF2 · ECDH · Offline-first · No cloud · Open source*

</div>