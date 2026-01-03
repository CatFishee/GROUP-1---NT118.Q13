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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnItemMoreClickListener {
        void onMoreClick(HistoryItem historyItem, int position);
    }
    private OnItemMoreClickListener moreClickListener;

    public void setOnItemMoreClickListener(OnItemMoreClickListener listener) {
        this.moreClickListener = listener;
    }

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_VIDEO = 1;

    private List<Object> items;

    public HistoryAdapter(List<Object> items) {
        this.items = items;
    }
    public void removeItem(int position) {
        if (position >= 0 && position < items.size()) {
            items.remove(position);
            notifyItemRemoved(position);
            // Có thể cần check xem ngày đó còn video nào không để xóa luôn DateHeader
        }
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
            ((VideoHistoryViewHolder) holder).bind((HistoryItem) items.get(position), moreClickListener);
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
        ImageView ivThumbnail, btnMore;
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
            btnMore = itemView.findViewById(R.id.btn_more_options);
        }

        void bind(HistoryItem historyItem, OnItemMoreClickListener listener) {
            if (historyItem.getVideo() == null) {
                tvVideoTitle.setText("Video not found");
                tvChannelName.setText(""); // Xóa text nếu không có video
                tvViewCount.setText("");
                return;
            }

            Video video = historyItem.getVideo();
            tvVideoTitle.setText(video.getTitle());
            tvDuration.setText(TimeUtil.formatDuration(video.getDuration()));
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

            FirebaseFirestore.getInstance().collection("users")
                    .document(video.getUploaderID())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        String name = "Unknown Channel";
                        if (documentSnapshot.exists()) {
                            name = documentSnapshot.getString("name");
                        }
                        tvChannelName.setText(name);
                    })
                    .addOnFailureListener(e -> tvChannelName.setText("Unknown Channel"));

            FirebaseDatabase.getInstance().getReference("videostat")
                    .child(video.getVideoID())
                    .child("viewCount")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            long views = 0;
                            if (snapshot.exists()) {
                                Object val = snapshot.getValue();
                                if (val instanceof Long) views = (Long) val;
                                else if (val instanceof Integer) views = ((Integer) val).longValue();
                            }
                            // CHỈ HIỂN THỊ SỐ VIEW
                            tvViewCount.setText(formatViewCount(views));
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            tvViewCount.setText("0 views");
                        }
                    });

            itemView.setOnClickListener(v -> {
                android.content.Context context = itemView.getContext();
                android.content.Intent intent = new android.content.Intent(context, com.example.metube.ui.video.VideoActivity.class);

                // 1. Truyền ID Video
                intent.putExtra("video_id", video.getVideoID());

                // 2. Truyền vị trí đã xem (Lấy từ HistoryItem)
                intent.putExtra("resume_position", historyItem.getResumePosition());

                context.startActivity(intent);
            });
            btnMore.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onMoreClick(historyItem, getBindingAdapterPosition());
                }
            });
        }

        private String formatViewCount(long count) {
            if (count < 1000) return count + " views";
            if (count < 1000000) return String.format(Locale.getDefault(), "%.1fK views", count / 1000.0);
            return String.format(Locale.getDefault(), "%.1fM views", count / 1000000.0);
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
}