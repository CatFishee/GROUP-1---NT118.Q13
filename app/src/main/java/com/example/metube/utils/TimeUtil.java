package com.example.metube.utils;
import java.util.Locale;
public class TimeUtil {
    // Dành cho Database lưu GIÂY (Seconds)
    public static String formatDuration(long durationSeconds) {
        if (durationSeconds <= 0) return "0:00";

        // KHÔNG CHIA CHO 1000 NỮA
        long seconds = durationSeconds;

        long minutes = seconds / 60;
        long hours = minutes / 60;

        long remainingMinutes = minutes % 60;
        long remainingSeconds = seconds % 60;

        // Nên dùng Locale.US để đảm bảo số hiển thị chuẩn 0-9 (tránh lỗi font số Ả Rập)
        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, remainingMinutes, remainingSeconds);
        } else {
            return String.format(Locale.US, "%d:%02d", minutes, remainingSeconds);
        }
    }
}
