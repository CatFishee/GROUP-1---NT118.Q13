package com.example.metube.ui.history;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.metube.R;
import com.example.metube.model.Video;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class HistoryMenuBottomSheet extends BottomSheetDialogFragment {

    // Interface để gửi sự kiện về Activity
    public interface HistoryMenuListener {
        void onRemoveFromHistory(String docId, int position);
        void onSaveToPlaylist(Video video);
        void onDownload(Video video);
        void onShare(Video video);
        void onPlayNextInQueue(Video video);
    }

    private Video video;
    private String historyDocId; // ID document để xóa
    private int position; // Vị trí trong list để xóa UI mượt
    private HistoryMenuListener listener;
    public static final int TYPE_HISTORY = 0;
    public static final int TYPE_PLAYLIST = 1;

    private int menuType = TYPE_HISTORY; // Mặc định là History
    private String playlistTitle = "";   // Dùng cho Playlist (Remove from [Name])

    public HistoryMenuBottomSheet(Video video, String docId, int position, int type, String title, HistoryMenuListener listener) {
        this.video = video;
        this.historyDocId = docId;
        this.position = position;
        this.menuType = type;
        this.playlistTitle = title;
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.layout_bottom_sheet_history_menu, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView tvRemove = view.findViewById(R.id.tv_remove_text);
        if (menuType == TYPE_HISTORY) {
            tvRemove.setText("Remove from watch history");
        } else if (menuType == TYPE_PLAYLIST) {
            // Thay đổi text: Remove from [Tên Playlist]
            tvRemove.setText("Remove from " + playlistTitle);

            // Icon có thể đổi thành dấu gạch chéo hoặc thùng rác tùy ý
            // ((ImageView)view.findViewById(R.id.iv_remove_icon)).setImageResource(...);
        }

        // 1. Remove
        view.findViewById(R.id.option_remove).setOnClickListener(v -> {
            if (listener != null) {
                // Gọi hàm remove chung, Activity sẽ tự biết phải xóa ở đâu
                listener.onRemoveFromHistory(historyDocId, position);
            }
            dismiss();
        });

        // 2. Save Playlist
        view.findViewById(R.id.option_save_playlist).setOnClickListener(v -> {
            if (listener != null) listener.onSaveToPlaylist(video);
            dismiss();
        });

        // 3. Download
        view.findViewById(R.id.option_download).setOnClickListener(v -> {
            if (listener != null) listener.onDownload(video);
            dismiss();
        });

        // 4. Share
        view.findViewById(R.id.option_share).setOnClickListener(v -> {
            if (listener != null) listener.onShare(video);
            dismiss();
        });
        view.findViewById(R.id.option_play_next).setOnClickListener(v -> {
            if (listener != null) listener.onPlayNextInQueue(video);
            dismiss();
        });
    }
}