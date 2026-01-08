package com.example.metube.ui.video;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.metube.R;
import com.example.metube.model.Video;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class RecommendedVideoBottomSheet extends BottomSheetDialogFragment {

    private Video video;
    private BottomSheetListener listener;

    public interface BottomSheetListener {
        void onPlayNext(Video video);
        void onSaveToPlaylist(Video video);
        void onDownload(Video video);
        void onShare(Video video);
    }

    public RecommendedVideoBottomSheet(Video video, BottomSheetListener listener) {
        this.video = video;
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.layout_bottom_sheet_video_homepage, container, false);

        LinearLayout btnPlayNext = view.findViewById(R.id.option_play_next);
        LinearLayout btnSavePlaylist = view.findViewById(R.id.option_save_playlist);
        LinearLayout btnDownload = view.findViewById(R.id.option_download);
        LinearLayout btnShare = view.findViewById(R.id.option_share);
        LinearLayout btnRemoveHistory = view.findViewById(R.id.option_remove_history);

        // Vì đây là danh sách đề xuất (Recommended), thường ta sẽ ẩn nút "Xóa khỏi lịch sử"
        // trừ khi bạn muốn tái sử dụng cho cả lịch sử. Ở đây tôi để mặc định GONE như XML hoặc logic.
        btnRemoveHistory.setVisibility(View.GONE);

        btnPlayNext.setOnClickListener(v -> {
            if (listener != null) listener.onPlayNext(video);
            dismiss();
        });

        btnSavePlaylist.setOnClickListener(v -> {
            if (listener != null) listener.onSaveToPlaylist(video);
            dismiss();
        });

        btnDownload.setOnClickListener(v -> {
            if (listener != null) listener.onDownload(video);
            dismiss();
        });

        btnShare.setOnClickListener(v -> {
            if (listener != null) listener.onShare(video);
            dismiss();
        });

        return view;
    }
}