# MapApp: Visual-GNSS Navigation & Safety Monitor

[Tiếng Việt bên dưới](#tiếng-việt)

MapApp is a high-level Android application designed to demonstrate a professional integration of classic Android fundamentals with modern Jetpack components. It features real-time location tracking, persistent background monitoring, and a robust safety alert system built on a reactive, service-oriented architecture.

## 🚀 Key Features

-   **Persistent Background Tracking**: Uses a hybrid Started/Bound Foreground Service to maintain location updates even when the app is closed or backgrounded.
-   **Dynamic Dashboard**: A real-time UI built with Jetpack Compose that reacts to live coordinates, speed, and device status.
-   **Intelligent Safety Alerts**:
    *   **Speed Monitor**: Real-time checking against user-defined limits with system-level notifications and in-app modal warnings.
    *   **Drowsiness Detection**: Integrated with `WorkManager` for periodic safety checks.
-   **Service-Side Simulation**: A dedicated "Simulation Mode" managed entirely within the Service to test alert logic with randomized speed data (0-20 km/h).
-   **Open Maps Integration**: Powered by **OSMDroid** for flexible, offline-ready map rendering.
-   **System Awareness**: Monitors global GPS provider status in real-time with proactive UI feedback.

## 🏗️ Architecture & Communication

The project implements a **Service-Centric MVVM** architecture, prioritizing reliability and strict IPC (Inter-Process Communication) standards.

### 🔌 Communication: Handler & Messenger
A standout feature of this project is the use of the classic **Handler & Messenger** pattern for communication between the `LocationForegroundService` and `MainViewModel`.
-   **Reliable Messaging**: All data (Location, Speed, Config) is packed into `Bundle` objects and sent via `Message` instances.
-   **Bi-directional Commands**: The ViewModel sends command messages (Start/Stop, Simulation Toggle) while the Service streams data back to registered clients.
-   **Legacy Standards**: This ensures the app adheres to official Android IPC requirements while maintaining a modern UI state via `StateFlow`.

### 📂 Component Breakdown
1.  **`LocationForegroundService`**: The core logic engine. Manages GPS updates, speed simulation, notification lifecycle, and alert triggers.
2.  **`MainViewModel`**: The coordinator. Acts as a Messenger client, receiving data and exposing it to the UI through reactive flows.
3.  **`db` package (Room)**: Handles persistent storage of alert history.
4.  **`ui` package (Compose)**: A declarative UI layer that focuses strictly on rendering the state provided by the ViewModel.

## 🛠️ Technical Highlights
-   **Classic**: Hybrid Services, Intents, Broadcast Receivers, System Notifications, Permissions.
-   **Modern**: Jetpack Compose, ViewModel, StateFlow/SharedFlow, Room DB, WorkManager.
-   **Optimization**: GPS Jitter filtering (1.5 km/h threshold) for stable stationary readings.

---

<a name="tiếng-việt"></a>
# MapApp: Hệ Thống Giám Sát Hành Trình & Cảnh Báo An Toàn

MapApp là ứng dụng Android chuyên sâu nhằm trình bày cách kết hợp chuyên nghiệp giữa các nền tảng Android cổ điển (Classic Fundamentals) và các thành phần Jetpack hiện đại. Ứng dụng cung cấp khả năng theo dõi vị trí thời gian thực, giám sát chạy ngầm và hệ thống cảnh báo an toàn dựa trên kiến trúc hướng dịch vụ (Service-oriented).

## 🚀 Tính Năng Chính

-   **Theo dõi Chạy ngầm Bền bỉ**: Sử dụng Foreground Service (Started & Bound) để đảm bảo vị trí luôn được cập nhật ngay cả khi thoát ứng dụng.
-   **Bảng Điều khiển Linh hoạt**: Giao diện Jetpack Compose cập nhật thời gian thực tọa độ, tốc độ và các trạng thái cảnh báo.
-   **Cảnh báo An toàn Thông minh**:
    *   **Giám sát Tốc độ**: Kiểm tra tốc độ so với giới hạn người dùng đặt, kích hoạt cả thông báo hệ thống và Dialog cảnh báo trong app.
    *   **Chống ngủ gật**: Tích hợp `WorkManager` để thực hiện các kiểm tra an toàn định kỳ.
-   **Giả lập Dữ liệu (Simulation)**: Chế độ Test nằm hoàn toàn trong Service, tự động tạo tốc độ ngẫu nhiên (0-20 km/h) để kiểm tra logic cảnh báo mà không cần di chuyển thật.
-   **Tích hợp Bản đồ Mở**: Sử dụng thư viện **OSMDroid** mạnh mẽ và linh hoạt.
-   **Nhận biết Hệ thống**: Theo dõi trạng thái bật/tắt GPS của thiết bị và phản hồi lập tức lên giao diện.

## 🏗️ Kiến trúc & Giao tiếp

Ứng dụng tuân thủ mô hình **Service-Centric MVVM**, ưu tiên tính tin cậy và chuẩn hóa giao tiếp giữa các thành phần.

### 🔌 Giao tiếp: Handler & Messenger
Điểm nhấn kỹ thuật của dự án là việc sử dụng cơ chế **Handler & Messenger** kinh điển để kết nối `LocationForegroundService` và `MainViewModel`.
-   **Truyền tin chuẩn hóa**: Mọi dữ liệu (Vị trí, Tốc độ, Cấu hình) được đóng gói vào `Bundle` và gửi đi dưới dạng `Message`.
-   **Lệnh hai chiều**: ViewModel gửi các bản tin lệnh (Bắt đầu/Dừng, Bật Test), trong khi Service truyền luồng dữ liệu ngược lại cho các Client đã đăng ký.
-   **Tính tương thích**: Đảm bảo ứng dụng đáp ứng các yêu cầu IPC khắt khe của Android trong khi vẫn giữ được sự mượt mà của giao diện hiện đại qua `StateFlow`.

### 📂 Phân tách Thành phần
1.  **`LocationForegroundService`**: "Bộ não" của ứng dụng. Quản lý cập nhật GPS, giả lập tốc độ, vòng đời thông báo và kích hoạt cảnh báo.
2.  **`MainViewModel`**: Bộ điều phối. Đóng vai trò là Messenger Client, nhận dữ liệu và chuyển đổi thành các luồng Flow cho UI.
3.  **`db` package (Room)**: Quản lý lưu trữ bền vững lịch sử các lần vi phạm hoặc cảnh báo.
4.  **`ui` package (Compose)**: Lớp giao diện khai báo, chỉ tập trung vào việc hiển thị trạng thái từ ViewModel.

## 🛠️ Điểm nhấn Kỹ thuật
-   **Cổ điển**: Hybrid Services, Intents, Broadcast Receivers, System Notifications, Permissions.
-   **Hiện đại**: Jetpack Compose, ViewModel, StateFlow/SharedFlow, Room DB, WorkManager.
-   **Tối ưu hóa**: Bộ lọc nhiễu GPS (ngưỡng 1.5 km/h) giúp số liệu ổn định khi đứng yên.
