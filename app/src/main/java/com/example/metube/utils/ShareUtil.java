package com.example.metube.utils;

import android.content.Context;
import android.content.Intent;

public class ShareUtil {

    public static void shareVideo(Context context, String videoTitle, String videoUrl) {
        // 1. Tạo nội dung muốn chia sẻ
        // Ví dụ: "Xem video này hay lắm: [Title] - [Link]"
        String shareBody = "Check out this video: " + videoTitle + "\n" + videoUrl;

        // 2. Tạo Intent
        Intent sharingIntent = new Intent(Intent.ACTION_SEND);

        // 3. Quy định kiểu dữ liệu là văn bản (text)
        sharingIntent.setType("text/plain");

        // 4. Đưa nội dung vào Intent
        sharingIntent.putExtra(Intent.EXTRA_SUBJECT, "Shared Video from MeTube"); // Tiêu đề (cho Email)
        sharingIntent.putExtra(Intent.EXTRA_TEXT, shareBody); // Nội dung chính

        // 5. Mở bảng chia sẻ hệ thống (System Share Sheet)
        // "Share via" là tiêu đề hiện lên nếu máy đời cũ, máy đời mới sẽ hiện như ảnh bạn gửi
        context.startActivity(Intent.createChooser(sharingIntent, "Share via"));
    }
}