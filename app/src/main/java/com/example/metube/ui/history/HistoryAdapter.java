package com.example.metube.ui.history;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.metube.R;
import com.example.metube.model.DateHeader;
import com.example.metube.model.HistoryItem;
import com.example.metube.utils.TimeUtil;
import com.example.metube.model.Video;

import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_VIDEO = 1;

    private List<Object> items;

    public HistoryAdapter(List<Object> items) {
        this.items = items;
    }

    @Override
    public int getItemViewType(int position) {
        if (items.get(position) instanceof DateHeader) {
            return VIEW_TYPE_HEADER;
        } else {
            return VIEW_TYPE_VIDEO;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_HEADER) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_date_header, parent, false);
            return new DateHeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_video_history, parent, false);
            return new VideoHistoryViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof DateHeaderViewHolder) {
            ((DateHeaderViewHolder) holder).bind((DateHeader) items.get(position));
        } else if (holder instanceof VideoHistoryViewHolder) {
            ((VideoHistoryViewHolder) holder).bind((HistoryItem) items.get(position));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // ViewHolder for Date Headers
    static class DateHeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvDateHeader;
        DateHeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDateHeader = itemView.findViewById(R.id.tv_date_header);
        }
        void bind(DateHeader dateHeader) {
            tvDateHeader.setText(dateHeader.getDateString());
        }
    }

    // ViewHolder for Video Items
    static class VideoHistoryViewHolder extends RecyclerView.ViewHolder {
        ImageView ivThumbnail;
        TextView tvDuration, tvVideoTitle, tvChannelName, tvViewCount;
        ProgressBar pbVideoProgress;

        VideoHistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumbnail = itemView.findViewById(R.id.iv_thumbnail);
            tvDuration = itemView.findViewById(R.id.tv_duration);
            tvVideoTitle = itemView.findViewById(R.id.tv_video_title);
            tvChannelName = itemView.findViewById(R.id.tv_channel_name);
            tvViewCount = itemView.findViewById(R.id.tv_view_count);
            pbVideoProgress = itemView.findViewById(R.id.pb_video_progress);
        }

        void bind(HistoryItem historyItem) {
            if (historyItem.getVideo() == null) {
                tvVideoTitle.setText("Video not found");
                tvChannelName.setText(""); // Xóa text nếu không có video
                tvViewCount.setText("");
                return;
            }

            Video video = historyItem.getVideo();
            tvVideoTitle.setText(video.getTitle());
            tvDuration.setText(formatDuration(video.getDuration()));
            long totalDuration = video.getDuration();
            long currentPos = historyItem.getResumePosition();

            if (totalDuration > 0 && currentPos > 0) {
                int percentage = (int) ((currentPos * 100) / totalDuration);

                // Nếu xem gần hết (>95%) thì hiện full 100%
                if (percentage > 95) percentage = 100;

                pbVideoProgress.setProgress(percentage);
                pbVideoProgress.setVisibility(View.VISIBLE);
            } else {
                pbVideoProgress.setVisibility(View.GONE);
            }

            Glide.with(itemView.getContext())
                    .load(video.getThumbnailURL())
                    .placeholder(R.color.light_green_background) // Thêm màu này vào colors.xml
                    .into(ivThumbnail);

            String uploaderName = "Unknown Channel";
            if (historyItem.getUploader() != null && historyItem.getUploader().getName() != null) {
                uploaderName = historyItem.getUploader().getName();
            } else if (video.getUploaderName() != null) {
            uploaderName = video.getUploaderName();
            }
            tvChannelName.setText(uploaderName);

            String viewCountFormatted = formatViewCount(video.getViewCount());
            tvViewCount.setText(viewCountFormatted);

            itemView.setOnClickListener(v -> {
                android.content.Context context = itemView.getContext();
                android.content.Intent intent = new android.content.Intent(context, com.example.metube.ui.video.VideoActivity.class);

                // 1. Truyền ID Video
                intent.putExtra("video_id", video.getVideoID());

                // 2. Truyền vị trí đã xem (Lấy từ HistoryItem)
                intent.putExtra("resume_position", historyItem.getResumePosition());

                context.startActivity(intent);
            });
        }

        private String formatDuration(long seconds) {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            long secs = seconds % 60;
            if (hours > 0) {
                return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, secs);
            } else {
                return String.format(Locale.getDefault(), "%d:%02d", minutes, secs);
            }
        }

        private String formatViewCount(long count) {
            if (count < 1000) return count + " views";
            if (count < 1000000) return String.format(Locale.getDefault(), "%.1fK views", count / 1000.0);
            return String.format(Locale.getDefault(), "%.1fM views", count / 1000000.0);
        }
    }
}