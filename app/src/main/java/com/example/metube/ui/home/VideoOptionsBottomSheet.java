package com.example.metube.ui.home;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import com.example.metube.R;
import com.example.metube.model.Video;
import com.google.android.material.bottomsheet.BottomSheetDialog;

public class VideoOptionsBottomSheet {

    public interface OnOptionSelectedListener {
        void onPlayNext(Video video);
        void onSaveToPlaylist(Video video);
        void onDownload(Video video);
        void onShare(Video video);
        void onDontRecommend(Video video);
        void onRemoveFromHistory(Video video);
    }

    /**
     * Hiển thị bottom sheet với các tùy chọn cho video
     * @param context Context
     * @param video Video object
     * @param showRemoveHistory Có hiển thị nút "Remove from history" không
     * @param listener Callback khi chọn option
     */
    public static void show(Context context, Video video, boolean showRemoveHistory, OnOptionSelectedListener listener) {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.layout_bottom_sheet_video_homepage, null);
        bottomSheetDialog.setContentView(view);

        // Lấy các view
        View optionPlayNext = view.findViewById(R.id.option_play_next);
        View optionSavePlaylist = view.findViewById(R.id.option_save_playlist);
        View optionDownload = view.findViewById(R.id.option_download);
        View optionShare = view.findViewById(R.id.option_share);
        View optionDontRecommend = view.findViewById(R.id.option_dont_recommend);
        View optionRemoveHistory = view.findViewById(R.id.option_remove_history);

        // Hiển thị/ẩn option Remove from history
        optionRemoveHistory.setVisibility(showRemoveHistory ? View.VISIBLE : View.GONE);

        // Set click listeners
        optionPlayNext.setOnClickListener(v -> {
            if (listener != null) listener.onPlayNext(video);
            bottomSheetDialog.dismiss();
        });

        optionSavePlaylist.setOnClickListener(v -> {
            if (listener != null) listener.onSaveToPlaylist(video);
            bottomSheetDialog.dismiss();
        });

        optionDownload.setOnClickListener(v -> {
            if (listener != null) listener.onDownload(video);
            bottomSheetDialog.dismiss();
        });

        optionShare.setOnClickListener(v -> {
            if (listener != null) listener.onShare(video);
            bottomSheetDialog.dismiss();
        });

        optionDontRecommend.setOnClickListener(v -> {
            if (listener != null) listener.onDontRecommend(video);
            bottomSheetDialog.dismiss();
        });

        optionRemoveHistory.setOnClickListener(v -> {
            if (listener != null) listener.onRemoveFromHistory(video);
            bottomSheetDialog.dismiss();
        });

        bottomSheetDialog.show();
    }
}