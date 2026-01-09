package com.example.metube.ui.video;

import android.content.Context;
import android.content.Intent;
import android.text.format.DateUtils;
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
import com.example.metube.model.Video;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;
import java.util.Locale;

public class RecommendedVideosAdapter extends RecyclerView.Adapter<RecommendedVideosAdapter.ViewHolder> {

    private Context context;
    private List<Video> videoList;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(Video video);
        void onOptionClick(Video video);
    }

    public RecommendedVideosAdapter(Context context, List<Video> videoList, OnItemClickListener listener) {
        this.context = context;
        this.videoList = videoList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Sử dụng layout item_video_history như yêu cầu
        View view = LayoutInflater.from(context).inflate(R.layout.item_video_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Video video = videoList.get(position);

        holder.tvTitle.setText(video.getTitle());
        holder.tvViewCount.setText(formatViewCount(video.getViewCount()));
        loadRealtimeViewCount(video.getVideoID(), holder.tvViewCount);

        // Format thời lượng
        if (video.getDuration() > 0) {
            holder.tvDuration.setText(formatDuration(video.getDuration()));
            holder.tvDuration.setVisibility(View.VISIBLE);
        } else {
            holder.tvDuration.setVisibility(View.GONE);
        }

        // Load Thumbnail
        Glide.with(context)
                .load(video.getThumbnailURL())
                .placeholder(R.color.creator_card_bg) // Màu nền tạm
                .into(holder.ivThumbnail);

        // Load Channel Name (Cần query Firestore nếu video object chưa có tên channel,
        // nhưng để tối ưu ta giả định UI lấy uploaderID hoặc query nhanh)
        loadChannelName(video.getUploaderID(), holder.tvChannelName);

        // Progress bar trong item history thường dùng để hiển thị mức độ đã xem
        // Ở đây là recommend nên ta ẩn đi
        holder.progressBar.setVisibility(View.GONE);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(video);
        });

        holder.btnOptions.setOnClickListener(v -> {
            if (listener != null) listener.onOptionClick(video);
        });
    }

    @Override
    public int getItemCount() {
        return videoList.size();
    }
    private void loadRealtimeViewCount(String videoId, TextView tvViewCount) {
        if (videoId == null) return;

        // Dùng addListenerForSingleValueEvent để lấy 1 lần, tránh performance kém khi scroll list
        FirebaseDatabase.getInstance().getReference("videostat")
                .child(videoId)
                .child("viewCount")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            long views = 0;
                            Object val = snapshot.getValue();
                            if (val instanceof Long) {
                                views = (Long) val;
                            } else if (val instanceof Integer) {
                                views = ((Integer) val).longValue();
                            }

                            // Cập nhật lại UI với số view chính xác
                            tvViewCount.setText(formatViewCount(views));
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // Nếu lỗi thì giữ nguyên số cũ
                    }
                });
    }

    private void loadChannelName(String uploaderId, TextView textView) {
        if (uploaderId == null) return;
        FirebaseFirestore.getInstance().collection("users").document(uploaderId)
                .get().addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        textView.setText(doc.getString("name"));
                    }
                });
    }

    private String formatViewCount(long count) {
        if (count == 0) return "No views";
        if (count < 1000) return count + " views";
        if (count < 1000000) return String.format(Locale.US, "%.1fK views", count / 1000.0);
        return String.format(Locale.US, "%.1fM views", count / 1000000.0);
    }

    private String formatDuration(long durationMs) {
        long seconds = durationMs / 1000;
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        return String.format(Locale.US, "%02d:%02d", m, s);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivThumbnail;
        TextView tvDuration, tvTitle, tvChannelName, tvViewCount;
        ProgressBar progressBar;
        ImageButton btnOptions;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumbnail = itemView.findViewById(R.id.iv_thumbnail);
            tvDuration = itemView.findViewById(R.id.tv_duration);
            tvTitle = itemView.findViewById(R.id.tv_video_title);
            tvChannelName = itemView.findViewById(R.id.tv_channel_name);
            tvViewCount = itemView.findViewById(R.id.tv_view_count);
            progressBar = itemView.findViewById(R.id.pb_video_progress);
            btnOptions = itemView.findViewById(R.id.btn_more_options);
        }
    }
}