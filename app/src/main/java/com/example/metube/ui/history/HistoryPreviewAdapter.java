package com.example.metube.ui.history;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.metube.R;
import com.example.metube.model.Video;

import java.util.List;

public class HistoryPreviewAdapter extends RecyclerView.Adapter<HistoryPreviewAdapter.ViewHolder> {

    private List<Video> videoList;

    public HistoryPreviewAdapter(List<Video> videoList) {
        this.videoList = videoList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Sử dụng một layout item mới, phù hợp cho hiển thị ngang
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_video_preview_horizontal, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Video video = videoList.get(position);
        holder.bind(video);
    }

    @Override
    public int getItemCount() {
        return videoList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivThumbnail;
        TextView tvTitle;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumbnail = itemView.findViewById(R.id.iv_thumbnail);
            tvTitle = itemView.findViewById(R.id.tv_title);
        }

        void bind(Video video) {
            if (video == null) return;

            tvTitle.setText(video.getTitle());
            Glide.with(itemView.getContext())
                    .load(video.getThumbnailURL())
                    .placeholder(R.color.light_green_background) // Thêm màu này
                    .into(ivThumbnail);
        }
    }
}