package com.example.metube.utils;

import android.content.Context;
import android.content.Intent;

public class ShareUtil {

    private static void startShareIntent(Context context, String body) {
        Intent sharingIntent = new Intent(Intent.ACTION_SEND);
        sharingIntent.setType("text/plain");
        // Chỉ gửi phần văn bản chính là link
        sharingIntent.putExtra(Intent.EXTRA_TEXT, body);
        context.startActivity(Intent.createChooser(sharingIntent, "Share via"));
    }

    // 1. Chia sẻ Video - Chỉ hiện duy nhất link Cloudinary
    public static void shareVideo(Context context, String videoUrl) {
        // Gửi duy nhất videoUrl
        startShareIntent(context, videoUrl);
    }

    // 2. Chia sẻ Channel (Nếu bạn cũng muốn chỉ hiện link ảnh đại diện hoặc text rỗng)
    public static void shareChannel(Context context, String channelName) {
        String appLink = "https://play.google.com/store/apps/details?id=" + context.getPackageName();
        String body = "Check out the channel \"" + channelName + "\" on MeTube!\nDownload the app to view: " + appLink;
        startShareIntent(context, body);
    }

    // 3. Chia sẻ Playlist (Tên + Link tải App)
    public static void sharePlaylist(Context context, String playlistTitle) {
        String appLink = "https://play.google.com/store/apps/details?id=" + context.getPackageName();
        String body = "Check out the playlist \"" + playlistTitle + "\" on MeTube!\nListen here: " + appLink;
        startShareIntent(context, body);
    }
}
