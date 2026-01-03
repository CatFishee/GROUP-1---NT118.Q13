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
import java.util.Locale;

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

    // 1. Thêm Interface
    public interface OnItemMoreClickListener {
        void onMoreClick(Video video, int position);
    }
    private OnItemMoreClickListener moreClickListener;

    public void setOnItemMoreClickListener(OnItemMoreClickListener listener) {
        this.moreClickListener = listener;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Video video = videoList.get(position);
        holder.bind(video, moreClickListener);
    }

    @Override
    public int getItemCount() {
        return videoList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivThumbnail, btnMore;
        TextView tvTitle, tvChannelName, tvDuration;
        android.widget.ProgressBar pbVideoProgress;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumbnail = itemView.findViewById(R.id.iv_thumbnail);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvChannelName = itemView.findViewById(R.id.tv_channel_name);
            tvDuration = itemView.findViewById(R.id.tv_duration);
            pbVideoProgress = itemView.findViewById(R.id.pb_video_progress);
            btnMore = itemView.findViewById(R.id.btn_more_options);
        }

        void bind(Video video, OnItemMoreClickListener listener) {
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
            itemView.setOnClickListener(v -> {
                android.content.Context context = itemView.getContext();
                android.content.Intent intent = new android.content.Intent(context, com.example.metube.ui.video.VideoActivity.class);

                // 1. Truyền ID Video
                intent.putExtra("video_id", video.getVideoID());

                // 2. Truyền vị trí đã xem (Lấy từ field @Exclude mà ta đã merge)
                intent.putExtra("resume_position", video.getResumePosition());

                context.startActivity(intent);
            });
            btnMore.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onMoreClick(video, getBindingAdapterPosition());
                }
            });
        }
    }
    public String formatDuration(long durationMs) {
        // QUAN TRỌNG: Phải đổi từ Mili-giây sang Giây trước khi tính toán
        long totalSeconds = durationMs / 1000;

        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }
    }
}