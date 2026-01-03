package com.example.metube.utils;
import java.util.Locale;
public class TimeUtil {
    // Dành cho Database lưu GIÂY (Seconds)
    // Trong file TimeUtil.java
    public static String formatDuration(long durationMs) {
        long totalSeconds = durationMs / 1000; // QUAN TRỌNG: Chia 1000 trước
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }
    }
}
