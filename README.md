<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%20TV-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Platform Android TV" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Architecture-MVVM-0A84FF?style=for-the-badge" alt="MVVM" />
</p>

---
# VietLauncher – Android TV Launcher (Kotlin & Jetpack Compose)

VietLauncher là launcher cho Android TV lấy cảm hứng từ Apple TV & macOS, hỗ trợ song ngữ **Tiếng Việt / English**, tối ưu điều khiển bằng remote D-pad lẫn cảm ứng/chuột.

## Kiến trúc & Công nghệ
- **Kotlin + Jetpack Compose + Material 3** (`VietLauncherTheme` với 3 giao diện: `macOS`, `Tulip xanh / Liquid Glass`, `Tulip tím / Liquid Glass`).
- **MVVM**: `LauncherViewModel` quản lý toàn bộ trạng thái (`StateFlow<LauncherUiState>`) và sự kiện hệ thống (`SharedFlow<LauncherEvent>`), kết hợp với `MainActivity` xử lý `ActivityResultContracts`, `SpeechRecognizer`, Photo Picker, `RoleManager` và phím điều khiển TV.
- **Thành phần chính**:
  - **Trang chủ**: Lời chào kèm tên, đồng hồ 24h, lịch dương & **âm lịch Việt Nam** (thuật toán Hồ Ngọc Đức), thanh dung lượng & RAM, đếm ngược ngày lễ, cảnh báo mùng 1 / 14 / Rằm / cuối tháng âm lịch, Now Playing (`NotificationListenerService` + trình phát nhạc nội bộ), thời tiết **Open-Meteo** (GPS/mạng/IP hoặc chọn 34 tỉnh/thành & phường/xã), nút Trợ lý giọng nói, USB, Wi-Fi/LAN, Cài đặt, khung trình chiếu ảnh 144dp.
  - **Khung Watch TV & Dock 4 ứng dụng**: 2 kênh TV tùy biến (mở qua FPT Play, nhấn giữ để sửa), Dock với 3 tab màu (`Apps`, `Vừa mở`, `Dùng nhiều`), viền LED chuyển màu khi focus, nhấn giữ để Di chuyển (D-pad) / Ẩn / Xóa, lưới **Tất cả ứng dụng** cuộn xuống dưới.
  - **Nhạc & Trình chiếu ảnh**: Quét nhạc & ảnh trên bộ nhớ trong và USB (`MediaStore`), phát nhạc trực tiếp, trình chiếu ảnh toàn màn hình hỗ trợ `DPAD_LEFT`/`DPAD_RIGHT`/`OK`.
  - **Giọng nói & Bảng giá trực tuyến**: Nhận diện lệnh tiếng Việt/Anh mở app, chuyển kênh FPT Play / VTVgo / VieON, đọc giờ/ngày/thời tiết qua `TextToSpeech`, xem & đọc bảng giá Vàng SJC, Cà phê, Hồ tiêu, Xăng dầu Petrolimex qua WebView + JS.
  - **Hình nền & Cài đặt nâng cao**: 5 nền gradient, nền Tulip Liquid Glass, ảnh từ máy (Photo Picker), thư mục ứng dụng, hoặc gửi ảnh từ điện thoại qua **mã QR + HTTP server nội bộ** (`UploadServer`); gán phím tắt điều khiển từ xa; ADB nội bộ (`dadb` cổng 5555) đặt launcher mặc định / tắt TV; `CrashGuard` tự bật chế độ an toàn khi gặp lỗi.

## Cài đặt & Build
- Yêu cầu Android SDK 36 (`minSdk = 24`, `targetSdk = 36`) và Java 17+.
- Build debug APK: `gradle assembleDebug`
- Đặt làm launcher mặc định qua hệ thống hoặc ADB Wi-Fi (`adb shell cmd package set-home-activity com.vietlaucher.tt/vn.launcher.tv.MainActivity`).
