package com.example.metube.utils;

import android.content.Context;
import android.content.Intent;

public class ShareUtil {

    // Hàm nội bộ để gọi bảng chia sẻ của hệ thống
    private static void startShareIntent(Context context, String subject, String body) {
        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
        sharingIntent.setType("text/plain");
        sharingIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
        sharingIntent.putExtra(Intent.EXTRA_TEXT, body);
        context.startActivity(Intent.createChooser(sharingIntent, "Share via"));
    }

    // 1. Chia sẻ Video (Dùng trong History/VideoDetail)
    public static void shareVideo(Context context, String videoTitle, String videoUrl) {
        String shareBody = "Check out this video: " + videoTitle + "\n" + videoUrl;
        startShareIntent(context, "Shared Video from MeTube", shareBody);
    }

    // 2. Chia sẻ Channel (Dùng trong PersonFragment)
    public static void shareChannel(Context context, String channelName, String channelUrl) {
        String shareBody = "Check out this channel: " + channelName + "\n" + channelUrl;
        startShareIntent(context, "Shared Channel from MeTube", shareBody);
    }
}