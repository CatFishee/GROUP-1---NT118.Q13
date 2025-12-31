package com.example.metube.ui.playlist;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.metube.R;
import com.example.metube.model.Playlist;
import com.example.metube.model.Video;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class AddToPlaylistAdapter extends RecyclerView.Adapter<AddToPlaylistAdapter.ViewHolder> {

    private List<Playlist> playlists;
    private Video currentVideo; // Video đang được thao tác
    private Runnable onDismissListener;

    public AddToPlaylistAdapter(List<Playlist> playlists, Video currentVideo, Runnable onDismissListener) {
        this.playlists = playlists;
        this.currentVideo = currentVideo;
        this.onDismissListener = onDismissListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_playlist_selection, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Playlist playlist = playlists.get(position);
        holder.tvTitle.setText(playlist.getTitle());
        holder.tvVisibility.setText(playlist.getVisibility());

        // Kiểm tra xem video này đã có trong playlist chưa để tick sẵn
        boolean isAdded = playlist.getVideoIds() != null && playlist.getVideoIds().contains(currentVideo.getVideoID());

        // Quan trọng: Tạm thời gỡ listener để tránh kích hoạt khi setChecked code
        holder.cbSelect.setOnCheckedChangeListener(null);
        holder.cbSelect.setChecked(isAdded);

        // Gắn sự kiện click checkbox
        holder.cbSelect.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updatePlaylist(playlist, isChecked, holder.itemView);
        });

        // Cho phép bấm vào cả dòng cũng tick được
        holder.itemView.setOnClickListener(v -> holder.cbSelect.toggle());
    }

    @Override
    public int getItemCount() {
        return playlists.size();
    }

    private void updatePlaylist(Playlist playlist, boolean isAdding, View view) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String videoId = currentVideo.getVideoID();
        String playlistId = playlist.getPlaylistId();
        if (playlistId == null) {
            Toast.makeText(view.getContext(), "Error: Playlist ID is null", Toast.LENGTH_SHORT).show();
            return;
        }
        String videoTopic = (currentVideo.getTopics() != null && !currentVideo.getTopics().isEmpty())
                ? currentVideo.getTopics().get(0) : "Other";

        if (isAdding) {
            // 1. Thêm Video ID
            db.collection("playlists").document(playlistId)
                    .update("videoIds", FieldValue.arrayUnion(videoId));

            // 2. --- QUAN TRỌNG: Thêm Topic vào mảng containedTopics ---
            db.collection("playlists").document(playlistId)
                    .update("containedTopics", FieldValue.arrayUnion(videoTopic))
                    .addOnSuccessListener(aVoid -> {
                        // Cập nhật Local Data
                        if (playlist.getVideoIds() == null) playlist.setVideoIds(new ArrayList<>());
                        playlist.getVideoIds().add(videoId);

                        // Cập nhật Local Topic
                        if (playlist.getContainedTopics() == null) playlist.setContainedTopics(new ArrayList<>());
                        if (!playlist.getContainedTopics().contains(videoTopic)) {
                            playlist.getContainedTopics().add(videoTopic);
                        }

                        Toast.makeText(view.getContext(), "Added to " + playlist.getTitle(), Toast.LENGTH_SHORT).show();
                        if (onDismissListener != null) onDismissListener.run();
                    });

        } else {
            // Logic xóa (Chỉ xóa videoId, không cần xóa topic vì playlist có thể còn video khác cùng topic)
            db.collection("playlists").document(playlistId)
                    .update("videoIds", FieldValue.arrayRemove(videoId))
                    .addOnSuccessListener(aVoid -> {
                        if (playlist.getVideoIds() != null) playlist.getVideoIds().remove(videoId);
                        Toast.makeText(view.getContext(), "Removed from " + playlist.getTitle(), Toast.LENGTH_SHORT).show();
                    });
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvVisibility;
        CheckBox cbSelect;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_playlist_title);
            tvVisibility = itemView.findViewById(R.id.tv_visibility);
            cbSelect = itemView.findViewById(R.id.cb_select);
        }
    }
}