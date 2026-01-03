package com.example.metube.watchtogether;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class VideoPickerAdapter extends RecyclerView.Adapter<VideoPickerAdapter.ViewHolder> {
    private List<Video> videoList = new ArrayList<>();
    private OnVideoSelectedListener listener;

    public interface OnVideoSelectedListener {
        void onVideoSelected(Video video);
    }

    public VideoPickerAdapter(OnVideoSelectedListener listener) {
        this.listener = listener;
    }

    public void setVideos(List<Video> videos) {
        this.videoList = videos;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Now using the specific layout
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_video_search, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Video video = videoList.get(position);

        holder.tvTitle.setText(video.getTitle());
        // Use Uploader Name if available, otherwise just "Unknown"
        // (You might need to join user table in real app, but for now use static or object data)
        holder.tvUploader.setText(video.getUploaderName() != null ? video.getUploaderName() : "Unknown Uploader");

        // Format duration
        long sec = video.getDuration() / 1000;
        holder.tvDuration.setText(String.format(Locale.getDefault(), "%02d:%02d", sec / 60, sec % 60));

        // Load Thumbnail with Glide
        if (video.getThumbnailURL() != null && !video.getThumbnailURL().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(video.getThumbnailURL())
                    .centerCrop()
                    .into(holder.ivThumbnail);
        }

        holder.itemView.setOnClickListener(v -> listener.onVideoSelected(video));
    }

    @Override
    public int getItemCount() {
        return videoList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvUploader, tvDuration;
        ImageView ivThumbnail;

        ViewHolder(View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvUploader = itemView.findViewById(R.id.tvUploader);
            tvDuration = itemView.findViewById(R.id.tvDuration);
            ivThumbnail = itemView.findViewById(R.id.ivThumbnail);
        }
    }
}