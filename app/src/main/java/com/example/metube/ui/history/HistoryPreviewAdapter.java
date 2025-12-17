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
import com.example.metube.model.User;
import com.example.metube.utils.TimeUtil;

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
        TextView tvTitle, tvChannelName, tvDuration;
        android.widget.ProgressBar pbVideoProgress;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumbnail = itemView.findViewById(R.id.iv_thumbnail);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvChannelName = itemView.findViewById(R.id.tv_channel_name);
            tvDuration = itemView.findViewById(R.id.tv_duration);
            pbVideoProgress = itemView.findViewById(R.id.pb_video_progress);
        }

        void bind(Video video) {
            if (video == null) return;

            tvTitle.setText(video.getTitle());
            tvDuration.setText(TimeUtil.formatDuration(video.getDuration()));
            long total = video.getDuration();
            long current = video.getResumePosition();

            // Chỉ hiện nếu đã xem > 0 và tổng > 0
            if (total > 0 && current > 0) {
                // Tính phần trăm
                int progress = (int) ((current * 100) / total);

                // Tinh chỉnh hiển thị
                if (progress > 90) progress = 100; // Xem gần hết thì cho full

                // Ánh xạ view: ProgressBar pbVideoProgress = itemView.findViewById(R.id.pb_video_progress);
                pbVideoProgress.setProgress(progress);
                pbVideoProgress.setVisibility(View.VISIBLE);
            } else {
                pbVideoProgress.setVisibility(View.GONE);
            }

            if (video.getUploaderName() != null) {
                tvChannelName.setText(video.getUploaderName());
            } else {
                tvChannelName.setText("Loading...");
            }
            Glide.with(itemView.getContext())
                    .load(video.getThumbnailURL())
                    .placeholder(R.color.light_green_background)
                    .centerCrop()
                    .into(ivThumbnail);
        }
    }
}