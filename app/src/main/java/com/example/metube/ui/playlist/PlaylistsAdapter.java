package com.example.metube.ui.playlist;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.example.metube.R;
import com.example.metube.model.Playlist;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.List;

public class PlaylistsAdapter extends RecyclerView.Adapter<PlaylistsAdapter.ViewHolder> {

    private List<Playlist> playlists = new ArrayList<>();

    public PlaylistsAdapter(List<Playlist> playlists) {
        if (playlists != null) this.playlists = playlists;
    }
    public interface OnPlaylistMoreClickListener {
        void onMoreClick(Playlist playlist);
    }
    private OnPlaylistMoreClickListener moreClickListener;

    public void setOnPlaylistMoreClickListener(OnPlaylistMoreClickListener listener) {
        this.moreClickListener = listener;
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_playlist_vertical, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Playlist playlist = playlists.get(position);

        // 1. Set Text Info
        holder.tvTitle.setText(playlist.getTitle());
        String visibility = playlist.getVisibility() != null ? playlist.getVisibility() : "Private";
        String prettyVisibility = visibility.substring(0, 1).toUpperCase() + visibility.substring(1).toLowerCase();
        holder.tvInfo.setText(prettyVisibility + " • Playlist");
        int count = (playlist.getVideoIds() != null) ? playlist.getVideoIds().size() : 0;
        holder.tvCount.setText(String.valueOf(count));

        // 2. Reset Thumbnail & Color
        holder.ivThumb.setImageResource(android.R.color.black);
        resetStackColors(holder);

        // 3. Load Thumbnail Video Đầu Tiên & Trích xuất màu
        if (playlist.getThumbnailURL() != null && !playlist.getThumbnailURL().isEmpty()) {
            loadThumbnailAndColor(holder, playlist.getThumbnailURL());
        }
        // B. Nếu không có, kiểm tra xem playlist có video không?
        else if (playlist.getVideoIds() != null && !playlist.getVideoIds().isEmpty()) {
            String firstVideoId = playlist.getVideoIds().get(0);
            FirebaseFirestore.getInstance().collection("videos").document(firstVideoId).get()
                    .addOnSuccessListener(snapshot -> {
                        String url = snapshot.getString("thumbnailURL");
                        if (url != null && !url.isEmpty()) {
                            loadThumbnailAndColor(holder, url);
                        }
                    });
        }
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(holder.itemView.getContext(), PlaylistDetailActivity.class);
            intent.putExtra("playlist_id", playlist.getPlaylistId()); // Truyền ID sang
            holder.itemView.getContext().startActivity(intent);
        });
        holder.btnMore.setOnClickListener(v -> {
            if (moreClickListener != null) {
                moreClickListener.onMoreClick(playlist);
            }
        });
    }
    private void loadThumbnailAndColor(ViewHolder holder, String url) {
        Glide.with(holder.itemView.getContext())
                .asBitmap()
                .load(url)
                .centerCrop()
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                        // Set ảnh
                        holder.ivThumb.setImageBitmap(resource);

                        // Palette lấy màu
                        Palette.from(resource).generate(palette -> {
                            if (palette != null) {
                                int defColor = ContextCompat.getColor(holder.itemView.getContext(), android.R.color.darker_gray);
                                int color = palette.getDominantColor(defColor);
                                applyColorToStack(holder.viewStack, color);
                            }
                        });
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                        holder.ivThumb.setImageDrawable(placeholder);
                        resetStackColors(holder);
                    }
                });
    }

    private void applyColorToStack(View view, int color) {
        if (view.getBackground() instanceof GradientDrawable) {
            ((GradientDrawable) view.getBackground().mutate()).setColor(color);
        }
    }

    private void resetStackColors(ViewHolder holder) {
        int defColor = ContextCompat.getColor(holder.itemView.getContext(), android.R.color.darker_gray);
        applyColorToStack(holder.viewStack, defColor);
    }

    @Override public int getItemCount() { return playlists.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvInfo, tvCount;
        ImageView ivThumb, btnMore;
        View viewStack; // Ánh xạ view stack

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_playlist_title);
            tvInfo = itemView.findViewById(R.id.tv_playlist_info);
            tvCount = itemView.findViewById(R.id.tv_video_count);
            ivThumb = itemView.findViewById(R.id.iv_playlist_thumb);
            viewStack = itemView.findViewById(R.id.view_stack_1);
            btnMore = itemView.findViewById(R.id.btn_more);
        }
    }
}