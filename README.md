# 🔐 AEMEATH – Master Prompt (Rebuild Reference)

> Dùng prompt này khi cần bắt đầu lại từ đầu trong một chat mới.
> Paste toàn bộ nội dung này vào đầu cuộc hội thoại mới với Claude.

---

## YÊU CẦU TỔNG QUAN

Tôi cần bạn giúp tôi vibe-coding một ứng dụng Android tên **Aemeath** — một **password manager offline** với tính năng **LAN Sync** bảo mật cao. Ứng dụng chạy hoàn toàn offline, không dùng server trung gian, mã hóa AES-256 end-to-end.

---

## 1. THÔNG TIN MÔI TRƯỜNG

- **IDE:** Android Studio Koala 2024.1.1 Patch 1 (AGP 8.5.1)
- **JDK:** 17
- **OS:** Windows 11
- **Target:** Chia sẻ APK cho vài người dùng (không lên Play Store)
- **Người dùng:** Mới học lập trình, cần hướng dẫn step-by-step, paste-and-run

---

## 2. CÔNG NGHỆ SỬ DỤNG

### Ngôn ngữ & Framework
- **Kotlin 1.9.24** (KHÔNG dùng Kotlin 2.0+ vì AGP 8.5.1 không tương thích)
- **Jetpack Compose** với `composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }`
- **KHÔNG** dùng plugin `kotlin.compose` (chỉ có từ Kotlin 2.0+)
- **Material Design 3**

### Build Config (QUAN TRỌNG – giữ đúng version)
```
agp = "8.5.1"
kotlin = "1.9.24"
ksp = "1.9.24-1.0.20"
compileSdk = 34
targetSdk = 34
minSdk = 26
composeBom = "2024.06.00"
kotlinCompilerExtensionVersion = "1.5.14"
activityCompose = "1.9.0"
lifecycleRuntimeKtx = "2.8.3"
```

### Thư viện chính
| Thư viện | Mục đích |
|----------|----------|
| Room 2.6.1 | Local database |
| Hilt 2.51.1 | Dependency Injection |
| KSP 1.9.24-1.0.20 | Annotation processing |
| DataStore Preferences 1.1.1 | Settings |
| androidx.security-crypto 1.0.0 | Secure storage |
| androidx.biometric 1.1.0 | Vân tay / FaceID |
| Navigation Compose 2.7.7 | Navigation |
| ZXing Android Embedded 4.3.0 | QR scanner |
| QRose 1.0.1 | QR code generator |
| NanoHTTPD 2.3.1 | Local HTTP server cho LAN Sync |
| Coroutines 1.8.1 | Async |
| Accompanist 0.34.0 | System UI |
| Splashscreen 1.0.1 | Splash screen |

### Package name
```
com.aemeath.app
```

---

## 3. KIẾN TRÚC CODE

### Pattern
- **MVVM** (Model – ViewModel – View)
- **Repository Pattern** cho data layer
- **Hilt** cho Dependency Injection
- **StateFlow + collectAsState** cho UI state
- **Coroutines** cho async operations

### Cấu trúc thư mục
```
com.aemeath.app/
├── AemeathApp.kt              (@HiltAndroidApp)
├── MainActivity.kt            (@AndroidEntryPoint, NavHost)
├── data/
│   ├── db/
│   │   ├── AemeathDatabase.kt (Room @Database)
│   │   ├── dao/
│   │   │   └── Daos.kt        (WebAppDao, AccountDao, AppSettingDao)
│   │   └── entity/
│   │       └── Entities.kt    (WebAppEntity, AccountEntity, AppSettingEntity)
│   └── repository/
│       └── PreferencesRepository.kt (DataStore)
├── di/
│   └── AppModule.kt           (Hilt @Module)
├── navigation/
│   └── Screen.kt              (sealed class routes)
├── security/
│   ├── CryptoManager.kt       (AES-256-GCM, PBKDF2, password tools)
│   └── SessionManager.kt      (in-memory key, auto-lock, wrong attempts)
└── ui/
    ├── auth/
    │   ├── SetupScreen.kt + SetupViewModel.kt
    │   └── UnlockScreen.kt + UnlockViewModel.kt
    ├── home/
    │   ├── HomeScreen.kt + HomeViewModel.kt
    │   └── components/
    │       ├── WebAppCard.kt
    │       └── SearchBar.kt
    ├── account/
    │   ├── AccountListScreen.kt + AccountListViewModel.kt
    │   ├── AddEditAccountScreen.kt + AddEditViewModel.kt
    │   └── components/
    │       ├── AccountItem.kt
    │       ├── PasswordField.kt
    │       └── PasswordGenerator.kt
    ├── settings/
    │   ├── SettingsScreen.kt + SettingsViewModel.kt
    │   └── ChangePasswordScreen.kt + ChangePasswordViewModel.kt
    ├── backup/
    │   └── BackupScreen.kt + BackupViewModel.kt
    ├── lansync/
    │   ├── LanSyncScreen.kt
    │   ├── LanSyncHostScreen.kt + LanSyncViewModel.kt
    │   ├── LanSyncLogScreen.kt
    │   └── server/
    │       ├── LanSyncServer.kt   (NanoHTTPD)
    │       └── LanSyncCrypto.kt   (ECDH, verification code)
    ├── main/
    │   └── MainViewModel.kt
    └── theme/
        ├── Color.kt
        ├── Theme.kt
        └── Type.kt
```

---

## 4. DATABASE SCHEMA

### Bảng `web_apps`
```
id          Long (PK autoGenerate)
name        String
iconEmoji   String (default "🌐")
iconBase64  String? (ảnh custom)
createdAt   Long (timestamp)
updatedAt   Long (timestamp)
```

### Bảng `accounts`
```
id                Long (PK autoGenerate)
webAppId          Long (FK → web_apps.id, CASCADE DELETE)
title             String
username          String
encryptedPassword String (AES-256-GCM encrypted, Base64)
notes             String
createdAt         Long
updatedAt         Long
```

### Bảng `app_settings`
```
key   String (PK)
value String
```

---

## 5. BẢO MẬT – QUAN TRỌNG NHẤT

### Encryption
- **AES-256-GCM** cho mọi password lưu xuống db
- **PBKDF2WithHmacSHA256** để derive key từ Master Password
  - Iterations: **310,000** (OWASP 2023 standard)
  - Key length: 256-bit
  - Salt: 32 bytes SecureRandom
- **IV**: 12 bytes SecureRandom, prepend vào ciphertext
- Format lưu: `Base64(IV + ciphertext + GCM_tag)`

### Session
- Encryption key **chỉ sống trong memory** (SessionManager), KHÔNG lưu disk
- Auto-lock khi app vào background
- Sai 5 lần → lockout 30 giây
- Constant-time comparison để chống timing attack

### Android Keystore (Biometric)
- Key biometric lưu trong Android Keystore
- `setUserAuthenticationRequired(true)`
- `setInvalidatedByBiometricEnrollment(true)`

### LAN Sync Security
- **ECDH** key exchange (Elliptic Curve Diffie-Hellman)
- Shared secret → **verification code 6 số** (first 6 digits of SHA-256 hash)
- **AES session key** cho mỗi phiên sync
- Không dùng server trung gian
- Phiên tự hủy sau 3 phút

---

## 6. TOÀN BỘ CHỨC NĂNG CẦN IMPLEMENT

### A. Authentication Layer

#### Setup (lần đầu)
- Tạo Master Password (min 6 ký tự)
- Xác nhận lại password
- Hiển thị password strength bar real-time (Weak/Fair/Good/Strong)
- Toggle bật/tắt sinh trắc học
- Sinh PBKDF2 key, lưu salt + hash xuống DataStore
- Tự động unlock session sau khi setup xong

#### Unlock
- Nhập Master Password với auto-focus
- Icon toggle ẩn/hiện password
- Đếm sai: hiển thị `X/5 lần`
- Lockout 30s với countdown timer
- Nút mở bằng vân tay (nếu đã bật)
- BiometricPrompt API đầy đủ

#### Đổi Master Password
- Yêu cầu nhập password cũ, kiểm tra hợp lệ
- Nhập password mới + xác nhận
- Re-encrypt **toàn bộ** accounts trong db với key mới
- Tạo salt mới, xóa key cũ khỏi memory

#### Session Security
- Auto-lock khi `onStop()` (trừ khi đang LAN Sync)
- Clear clipboard sau 30 giây khi copy password
- Yêu cầu nhập lại Master Password trước khi tạo phiên LAN Sync

---

### B. Core Password Manager

#### Trang Chủ
- **AppBar**: Logo Aemeath bên trái, tiêu đề giữa, icon Settings bên phải
- **Stats row**: Tổng số Web/App và tổng số tài khoản
- **Search bar**: Tìm kiếm real-time (debounce 300ms), search cả tên web/app lẫn username
- **RecyclerView** (LazyColumn / LazyVerticalGrid):
  - Item: icon emoji, tên Web/App, số tài khoản
  - Click → màn hình danh sách tài khoản
- **Toolbar menu** (3 chấm):
  - Sắp xếp: A-Z, Z-A, Mới nhất, Sửa gần nhất
  - Chủ đề: Sáng / Tối / Hệ thống
  - Chọn nhiều → Xóa hàng loạt
  - Dạng xem: Danh sách / Lưới
- **FAB**: Thêm tài khoản mới
- **BottomNavigation**: Trang Chủ ↔ Cài Đặt

#### Thêm/Sửa Tài Khoản
- Dropdown chọn Web/App (có thể gõ tìm kiếm)
  - Nếu chưa tồn tại → nút "Tạo mới" + chọn emoji icon
- Field: Tiêu đề tài khoản
- Field: Username (với nút copy)
- Field: Password
  - Toggle ẩn/hiện
  - Password strength bar
  - Nút **Generate** → mở bottom sheet tùy chọn:
    - Độ dài: slider 8-32
    - Toggle: Chữ hoa / Chữ thường / Số / Ký tự đặc biệt
    - Preview password sinh ra
    - Nút "Dùng password này"
- Field: Ghi chú (multiline)
- Validate: không để trống Web/App, Username, Password
- Lưu: mã hóa password → AccountEntity → Room

#### Danh sách Tài Khoản theo Web/App
- Header: icon lớn, tên Web/App, số lượng account, ảnh nền mờ
- Mỗi account item:
  - Tiêu đề + username
  - Password (ẩn `••••••`) + icon toggle hiện
  - Nút copy username (toast "Đã sao chép")
  - Nút copy password (toast + auto-clear clipboard 30s)
  - Ngày sửa đổi
  - 3 nút: QR Share | Sửa | Xóa (confirm dialog)
- Menu 3 chấm: chọn nhiều → xóa hàng loạt
- Swipe-to-delete với Undo snackbar

#### QR Share Account
- Tạo QR chứa thông tin account (username + password đã mã hóa AES với temp key)
- Hiển thị trong bottom sheet
- QR tự hủy sau 60 giây
- Cảnh báo "Chỉ dùng ở nơi an toàn"

---

### C. Backup & Restore

#### Xuất
- Xuất database encrypted (file .aem) → chọn thư mục lưu
- Xuất CSV (plaintext) → warning trước khi xuất
- Xuất Google CSV format (tương thích import vào Chrome)
- Hiển thị kích thước file, thời gian xuất

#### Nhập
- Nhập file .aem (encrypted backup)
- Nhập CSV thông thường
- Nhập Google CSV (passwords.csv từ Chrome)
- Preview số record trước khi import
- Chọn: Merge (giữ cả hai) hoặc Overwrite (xóa hết rồi import)
- Confirm dialog trước khi overwrite

#### Sao lưu tự động
- Toggle bật/tắt
- Chọn thư mục lưu (SAF DocumentFile)
- Chu kỳ: Mỗi ngày / Mỗi tuần
- Chỉ backup khi có WiFi (toggle)
- Log lần backup cuối

---

### D. LAN Sync (Secure Device Link)

#### Flow hoàn chỉnh

**Android (Host):**
1. Yêu cầu nhập Master Password
2. Mở NanoHTTPD server trên port 8080
3. Sinh `sessionId`, ECDH key pair, timeout 3 phút
4. Hiển thị IP nội bộ + link `http://IP:8080`
5. Animation "radar" đang chờ kết nối
6. Nút: Sao chép link | Làm mới phiên | Hủy

**Laptop (Browser):**
7. Người dùng mở trình duyệt, nhập link
8. Web interface hiển thị logo + QR code lớn
9. QR encode: `sessionId + publicKey của server`

**Android quét QR:**
10. Nút "Quét mã từ laptop" → mở ZXing camera
11. Decode QR → lấy sessionId + public key laptop
12. Thực hiện ECDH: gửi public key của phone lên server
13. Cả hai tính `sharedSecret`
14. `verificationCode = first6digits(SHA256(sharedSecret))`

**Xác nhận:**
15. Android hiển thị mã 6 số (font monospace lớn, chia nhóm `XX XX XX`)
16. Laptop hiển thị mã 6 số tương tự
17. User so sánh → nhấn Xác nhận / Từ chối trên Android
18. Session AES key được thiết lập

**Sync:**
19. Chọn kiểu sync: Full / Một chiều (Phone→Laptop) / Chỉ mục chọn
20. Checkbox: Bao gồm Web/App | Bao gồm Accounts
21. Mã hóa payload AES → gửi qua HTTP
22. Kiểm tra hash toàn vẹn
23. Progress: icon đổi theo từng bước (🔐 Mã hóa → 📦 Chuẩn bị → 🚀 Truyền → ✅ Xác minh)
24. Hoàn tất: số record, thời gian, auto-close sau 10s

**Tự hủy phiên khi:**
- Không có kết nối trong 3 phút
- Sau khi sync xong
- User nhấn Hủy
- IP mạng thay đổi

#### Nhật ký LAN Sync
- Thời gian, Thiết bị, IP, Số record, Trạng thái
- Nút xóa log

---

### E. Cài Đặt

- Chủ đề: Sáng / Tối / Hệ thống (lưu DataStore, apply ngay)
- LAN Sync → màn hình LAN Sync
- Đổi Master Password
- Backup / Restore
- Sao lưu tự động
- Thông tin ứng dụng:
  - Phiên bản, tên tác giả
  - Danh sách công nghệ sử dụng
  - Chính sách bảo mật (dialog đơn giản)

---

## 7. UI/UX YÊU CẦU

### Design System
- **Material 3** components xuyên suốt
- **Corner radius**: 16-24dp cho cards, 16dp cho fields
- **Color brand**: Primary `#4C6EF5`, gradient Primary → PrimaryLight `#7B96FF`
- **Dark/Light** theme hoàn chỉnh (không hard-code màu)
- Background gradient nhẹ (không flat cứng)

### Màn hình Auth
- Logo centered với gradient box 80-88dp, corner 24-28dp
- Setup/Unlock: vertically centered layout

### Trang chủ
- Stats row với 2 chip nho nhỏ hiển thị số lượng
- Search bar Material 3 style
- WebApp cards có icon emoji lớn, tên, số account dạng badge

### LAN Sync (premium look)
- Radar animation khi chờ kết nối (vẽ bằng Canvas hoặc animated rings)
- Timeline step indicator (Tạo phiên ✓ → Kết nối ✓ → Xác thực… → Đồng bộ)
- Mã 6 số: font monospace, rất to, chia 3 nhóm 2 số
- Micro-interactions: fade-in số, haptic feedback khi confirm
- Text bảo mật nhỏ ở dưới: "Mã hóa AES-256 · Không dùng server trung gian · Phiên tự hủy sau 3 phút"

### Chung
- Haptic feedback khi copy, confirm, error
- Toast message cho các hành động copy
- Empty state có illustration (emoji + text mô tả)
- Loading state với CircularProgressIndicator
- Error state với retry button
- Confirm dialog trước khi xóa (không xóa thẳng)
- Snackbar với Undo cho swipe-to-delete

---

## 8. SECURITY NON-NEGOTIABLES

Những điều KHÔNG BAO GIỜ được làm:
- KHÔNG lưu plaintext password xuống disk
- KHÔNG lưu encryption key xuống disk (chỉ trong memory)
- KHÔNG upload data lên bất kỳ server nào
- KHÔNG dùng `allowBackup=true` (đã set false trong Manifest)
- KHÔNG log password hoặc key ra Logcat
- KHÔNG dùng `ECB` mode cho AES (chỉ dùng GCM)

---

## 9. CÁCH LÀM VIỆC VỚI USER

User mới học lập trình, cần:
- Code **paste-and-run**: mỗi file phải hoàn chỉnh, đúng package, không thiếu import
- Chỉ rõ **đường dẫn file** cho từng code snippet
- Giải thích **ngắn gọn** mỗi file làm gì (1-2 dòng)
- Khi gặp lỗi build: phân tích ngay, fix cụ thể từng dòng/file
- Chia thành **phases** có thể build và chạy được:
  - ✅ **Phase 1**: Auth (Setup + Unlock) + DB + Navigation cơ bản *(đã xong)*
  - 🔄 **Phase 2**: Home Screen + Add/Edit Account + Password Generator
  - ⏳ **Phase 3**: Backup/Restore + Settings + Change Password
  - ⏳ **Phase 4**: LAN Sync (HTTP Server + QR + ECDH + Sync)
  - ⏳ **Phase 5**: Polish (Animations + Micro-interactions + Edge cases)

---

## 10. TRẠNG THÁI HIỆN TẠI

### ✅ Phase 1 đã hoàn tất và chạy được:
- Project setup với Kotlin 1.9.24 + AGP 8.5.1
- CryptoManager: AES-256-GCM + PBKDF2 + password strength + password generator
- SessionManager: in-memory key + lockout logic
- Room Database: WebAppEntity + AccountEntity + tất cả DAOs
- DataStore: PreferencesRepository (theme, biometric, settings)
- Hilt DI: AppModule
- Navigation: Screen sealed class
- SetupScreen + SetupViewModel: tạo Master Password lần đầu
- UnlockScreen + UnlockViewModel: unlock + lockout countdown
- Material 3 Theme: Color, Type, Theme (dark/light)
- HomeScreen: placeholder, chờ Phase 2

### ⚠️ Lưu ý đã phát sinh khi build Phase 1:
- AGP 8.5.1 không tương thích với Kotlin 2.0+ và composeBom 2024.08+
- Phải dùng Kotlin 1.9.24 + composeBom 2024.06.00 + composeOptions thay vì kotlin.compose plugin
- compileSdk/targetSdk phải là **34** (không phải 35)

---

## 11. YÊU CẦU KHI BẮT ĐẦU LẠI

Nếu đây là chat mới:
1. Đọc toàn bộ prompt này
2. Xác nhận đã hiểu kiến trúc và trạng thái
3. Hỏi user muốn bắt đầu từ Phase nào (thường là Phase 2)
4. Generate code theo đúng conventions đã mô tả
5. Đảm bảo version dependencies khớp với bảng ở Mục 2

---

*Prompt này được tạo tự động từ quá trình phát triển Aemeath Phase 1.*
*Cập nhật lần cuối: Phase 1 hoàn tất.*