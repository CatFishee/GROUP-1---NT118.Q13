package com.example.metube.ui.contentcreator;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.metube.R;
import com.example.metube.model.Playlist;
import java.util.ArrayList;
import java.util.List;

public class PlaylistVerticalAdapter extends RecyclerView.Adapter<PlaylistVerticalAdapter.ViewHolder> {

    private List<Playlist> playlists = new ArrayList<>();
    private Context context;
    private OnPlaylistClickListener listener;

    public interface OnPlaylistClickListener {
        void onPlaylistClick(Playlist playlist);
//        void onMoreClick(Playlist playlist, View view);
    }

    public PlaylistVerticalAdapter(Context context, OnPlaylistClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setPlaylists(List<Playlist> playlists) {
        this.playlists = playlists;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_playlist_vertical, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Playlist playlist = playlists.get(position);

        // ✅ Title (đổi từ getName() → getTitle())
        holder.tvTitle.setText(playlist.getTitle());

        // ✅ Video count (đổi từ getVideoIDs() → getVideoIds())
        int videoCount = playlist.getVideoIds() != null ? playlist.getVideoIds().size() : 0;
        holder.tvVideoCount.setText(String.valueOf(videoCount));

        // ✅ Info (visibility + type) - đổi từ isPublic() → getVisibility()
        String visibility = playlist.getVisibility();
        if (visibility == null) {
            visibility = "PRIVATE";
        }

        // Format visibility text
        String visibilityText;
        switch (visibility.toUpperCase()) {
            case "PUBLIC":
                visibilityText = "Public";
                break;
            case "UNLISTED":
                visibilityText = "Unlisted";
                break;
            case "PRIVATE":
            default:
                visibilityText = "Private";
                break;
        }
        holder.tvInfo.setText(visibilityText + " • Playlist");

        // Load thumbnail
        if (playlist.getThumbnailURL() != null && !playlist.getThumbnailURL().isEmpty()) {
            Glide.with(context)
                    .load(playlist.getThumbnailURL())
                    .centerCrop()
                    .placeholder(R.color.black)
                    .into(holder.ivThumbnail);
        } else {
            holder.ivThumbnail.setImageResource(R.color.black);
        }

        // Click listeners
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPlaylistClick(playlist);
            }
        });

//        holder.btnMore.setOnClickListener(v -> {
//            if (listener != null) {
//                listener.onMoreClick(playlist, v);
//            }
//        });
        holder.btnMore.setVisibility(View.GONE);
    }

    @Override
    public int getItemCount() {
        return playlists.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivThumbnail, btnMore;
        TextView tvTitle, tvInfo, tvVideoCount;

        ViewHolder(View itemView) {
            super(itemView);
            ivThumbnail = itemView.findViewById(R.id.iv_playlist_thumb);
            tvTitle = itemView.findViewById(R.id.tv_playlist_title);
            tvInfo = itemView.findViewById(R.id.tv_playlist_info);
            tvVideoCount = itemView.findViewById(R.id.tv_video_count);
            btnMore = itemView.findViewById(R.id.btn_more);
        }
    }
}