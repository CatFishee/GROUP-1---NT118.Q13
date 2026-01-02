package com.example.metube.ui.video;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.metube.R;
import com.example.metube.model.Video;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class YourVideoMenuBottomSheet extends BottomSheetDialogFragment {

    public interface YourVideoMenuListener {
        void onSaveToPlaylist(Video video);
        void onShareVideo(Video video);
        void onEditVideo(Video video);
        void onSaveToDevice(Video video);
        void onDeleteVideo(Video video);
    }

    private Video video;
    private YourVideoMenuListener listener;

    public YourVideoMenuBottomSheet(Video video, YourVideoMenuListener listener) {
        this.video = video;
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.layout_bottom_sheet_your_video_menu, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 1. Save to Playlist
        view.findViewById(R.id.option_save_playlist).setOnClickListener(v -> {
            if (listener != null) listener.onSaveToPlaylist(video);
            dismiss();
        });

        // 2. Share
        view.findViewById(R.id.option_share).setOnClickListener(v -> {
            if (listener != null) listener.onShareVideo(video);
            dismiss();
        });

        // 3. Edit
        view.findViewById(R.id.option_edit).setOnClickListener(v -> {
            if (listener != null) listener.onEditVideo(video);
            dismiss();
        });

        // 4. Save to Device (Download)
        view.findViewById(R.id.option_save_device).setOnClickListener(v -> {
            if (listener != null) listener.onSaveToDevice(video);
            dismiss();
        });

        // 5. Delete
        view.findViewById(R.id.option_delete).setOnClickListener(v -> {
            if (listener != null) listener.onDeleteVideo(video);
            dismiss();
        });

        // 6. Cancel
        view.findViewById(R.id.btn_cancel).setOnClickListener(v -> dismiss());
    }
}