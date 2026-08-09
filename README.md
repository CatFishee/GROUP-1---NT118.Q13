# MeTube — Online Video Sharing App with Watch Together

> **Đồ án môn Phát triển Ứng dụng trên Thiết bị Di động (NT118.Q13)** — Nhóm 1

## 🌐 Summary (EN)

MeTube is a native Android video-sharing application inspired by platforms like YouTube and Twitch. Beyond core features (upload, watch, comment, subscribe), it includes a real-time **Watch Together** feature that lets multiple users synchronously watch videos in the same room, and an **on-device AI content moderation** system (Google ML Kit) that automatically flags sensitive content during upload. Built on a Backend-as-a-Service architecture using Firebase (Auth, Firestore, Realtime Database) and Cloudinary (media CDN).

## 📌 Tóm tắt

MeTube là ứng dụng xem video trực tuyến trên nền tảng Android Native, mô phỏng lại mô hình hoạt động của các nền tảng chia sẻ video phổ biến (YouTube, Twitch), đồng thời phát triển thêm các tính năng nâng cao như **Watch Together** (xem video đồng bộ theo thời gian thực trong cùng một phòng), quản lý kênh cho người sáng tạo nội dung, và **kiểm duyệt hình ảnh bằng AI** khi upload video.

Hệ thống dùng kiến trúc BaaS (Backend-as-a-Service): **Firebase** đảm nhiệm xác thực người dùng, cơ sở dữ liệu chính (Cloud Firestore) và dữ liệu tần suất cao (Realtime Database), trong khi **Cloudinary** đóng vai trò lưu trữ và phân phối video qua CDN.

---

## 👥 Đóng góp của các thành viên

### Lê Nguyễn Phương Giang – 23520407

Phụ trách phần **giao diện chính** và các **tính năng tiện ích cho người dùng/người sáng tạo nội dung**:

- Lên ý tưởng giao diện tổng thể, thiết lập theme và điều hướng cho màn hình chính.
- Xây dựng chức năng **tìm kiếm video** (theo text và giọng nói), sử dụng kỹ thuật đánh chỉ mục từ khóa (`searchKeywords`) trên Cloud Firestore.
- Hoàn thiện luồng **upload video tích hợp AI Detection** (Google ML Kit) — tự động nhận diện và kiểm duyệt hình ảnh nhạy cảm trước khi video được đăng tải.
- Xây dựng chức năng **hiển thị thông báo** (in-app notification, dựa trên `addSnapshotListener` của Firestore).
- Xây dựng chức năng **Share video** qua Intent hệ thống Android.
- Xây dựng chức năng **quản lý video đã đăng** (chỉnh sửa tiêu đề, mô tả, thumbnail, chế độ hiển thị Public/Unlisted/Private, bật/tắt bình luận).
- Thiết kế giao diện và chức năng **User Profile** và **Settings**.

**Tech stack:** Java (Android Native), Cloud Firestore, Google ML Kit, Cloudinary SDK.

### Bùi Thiên Phú – 23521176 (Trưởng nhóm)

Phụ trách phần **thiết kế hệ thống** và **các tính năng lõi liên quan đến video/tương tác xã hội**:

- Thiết kế database, sơ đồ use case, sơ đồ phân rã chức năng.
- Xây dựng chức năng đăng nhập/đăng ký với Google, Facebook.
- Xây dựng chức năng xem video, hiển thị video đề xuất theo chủ đề.
- Xây dựng chức năng hiển thị thông số video (lượt xem, lượt thích...).
- Setup luồng upload video.
- Thiết kế giao diện và chức năng **Creator Tab**.
- Xây dựng tính năng **Watch Together**.
- Xây dựng chức năng Comment, Subscribe, Switch account/Logout, Download video, Creator Profile.

---

## 🏗️ Kiến trúc & Công nghệ

| Thành phần | Công nghệ |
|---|---|
| **Nền tảng** | Android Native (Java) |
| **Xác thực** | Firebase Authentication (Google, Facebook) |
| **CSDL chính** | Cloud Firestore (NoSQL) |
| **CSDL phụ (tần suất cao)** | Firebase Realtime Database — đồng bộ Watch Together, view/like count |
| **Lưu trữ media** | Cloudinary (CDN) |
| **Video player** | ExoPlayer (tùy chỉnh giao diện điều khiển) |
| **AI kiểm duyệt nội dung** | Google ML Kit (on-device) |
| **Image loading** | Glide |

## ✨ Tính năng chính

- Đăng ký/đăng nhập qua Google, Facebook
- Xem video, đề xuất theo chủ đề, tìm kiếm text/voice
- **Watch Together** — xem đồng bộ nhiều người dùng theo mô hình Host/Guest
- Upload video kèm kiểm duyệt AI, quản lý video đã đăng
- Like, Subscribe, Comment, Share, Download offline
- Thông báo thời gian thực, Dark/Light mode

## ⚠️ Hạn chế đã biết

- Thông báo chỉ hoạt động khi ứng dụng đang mở (chưa có Push Notification khi app bị kill).
- Tìm kiếm chưa hỗ trợ fuzzy search / sửa lỗi chính tả.
- Một số API key (Cloudinary) hiện đang khởi tạo phía client — cần chuyển qua server trung gian cho môi trường production.

## Thành viên nhóm

| MSSV | Họ và tên | Vai trò |
|---|---|---|
| 23521176 | Bùi Thiên Phú | Trưởng nhóm |
| 23520407 | Lê Nguyễn Phương Giang | Thành viên |

## 🚀 Yêu cầu hệ thống

- Android Studio
- JDK 11+
- Tài khoản Firebase (Authentication, Firestore, Realtime Database)
- Tài khoản Cloudinary
