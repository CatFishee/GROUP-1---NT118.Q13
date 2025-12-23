package com.example.metube.ui.playlist;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.metube.R;
import com.example.metube.model.Playlist;
import java.util.List;

public class PlaylistsAdapter extends RecyclerView.Adapter<PlaylistsAdapter.ViewHolder> {

    private List<Playlist> playlists;

    public PlaylistsAdapter(List<Playlist> playlists) {
        this.playlists = playlists;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_playlist_vertical, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Playlist playlist = playlists.get(position);
        holder.bind(playlist);
    }

    @Override
    public int getItemCount() {
        return playlists.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvInfo, tvCount;
        // ImageView ivThumb; // Để sau load ảnh

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_playlist_title);
            tvInfo = itemView.findViewById(R.id.tv_playlist_info);
            tvCount = itemView.findViewById(R.id.tv_video_count);
        }

        void bind(Playlist playlist) {
            tvTitle.setText(playlist.getTitle());

            // Info: "Private • Playlist"
            String visibility = playlist.getVisibility() != null ? playlist.getVisibility() : "Private";
            // Viết hoa chữ cái đầu cho đẹp (Private)
            String prettyVisibility = visibility.substring(0, 1).toUpperCase() + visibility.substring(1).toLowerCase();
            tvInfo.setText(prettyVisibility + " • Playlist");

            // Số lượng video
            int count = (playlist.getVideoIds() != null) ? playlist.getVideoIds().size() : 0;
            tvCount.setText(String.valueOf(count));

            // TODO: Load thumbnail nếu playlist có video (lấy video đầu tiên)
        }
    }
}