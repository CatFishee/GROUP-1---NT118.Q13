package com.example.metube.utils;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;
import java.io.File;

public class DownloadUtil {

    public static void downloadVideo(Context context, String videoUrl, String videoTitle) {
        if (videoUrl == null || videoUrl.isEmpty()) {
            Toast.makeText(context, "Link video bị lỗi", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // 1. Xác định đường dẫn lưu file để hiển thị cho user
            String fileName = videoTitle.replaceAll("[^a-zA-Z0-9.\\-]", "_") + ".mp4";
            String destinationPath = Environment.DIRECTORY_MOVIES + "/MeTube"; // Thư mục lưu

            // 2. Tạo Request
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(videoUrl));
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
            request.setTitle(videoTitle);
            request.setDescription("Downloading...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, "MeTube/" + fileName);

            // 3. Gửi yêu cầu tải
            DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);

            if (downloadManager != null) {
                // Đăng ký lắng nghe sự kiện KHI TẢI XONG
                registerDownloadCompleteReceiver(context, fileName, destinationPath);

                long downloadId = downloadManager.enqueue(request);
                Toast.makeText(context, "Bắt đầu tải xuống...", Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            Toast.makeText(context, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // Hàm lắng nghe sự kiện tải xong
    private static void registerDownloadCompleteReceiver(Context context, String fileName, String path) {
        BroadcastReceiver onComplete = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctxt, Intent intent) {
                // Hiển thị thông báo đường dẫn file
                Toast.makeText(ctxt, "Đã tải xong: " + fileName + "\nLưu tại: " + path, Toast.LENGTH_LONG).show();

                // Hủy đăng ký ngay sau khi nhận tin (để tránh rò rỉ bộ nhớ)
                try {
                    ctxt.unregisterReceiver(this);
                } catch (IllegalArgumentException e) {
                    // Receiver đã bị hủy hoặc không tồn tại, bỏ qua
                }
            }
        };

        // Đăng ký receiver (hỗ trợ Android 12+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(onComplete,
                    new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(onComplete,
                    new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        }
    }
}