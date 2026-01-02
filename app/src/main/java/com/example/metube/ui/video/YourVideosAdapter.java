package com.example.metube.ui.video;

import android.content.Context;
import android.content.Intent;
import android.text.format.DateUtils;
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
import com.example.metube.utils.TimeUtil;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.List;
import java.util.Locale;

public class YourVideosAdapter extends RecyclerView.Adapter<YourVideosAdapter.ViewHolder> {

    private List<Video> videos;

    public YourVideosAdapter(List<Video> videos) {
        this.videos = videos;
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_your_videos, parent, false);
        return new ViewHolder(view);
    }
    public interface OnItemMoreClickListener {
        void onMoreClick(Video video);
    }
    private OnItemMoreClickListener moreClickListener;

    public void setOnItemMoreClickListener(OnItemMoreClickListener listener) {
        this.moreClickListener = listener;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Video video = videos.get(position);
        Context context = holder.itemView.getContext();

        // 1. Title & Duration
        holder.tvTitle.setText(video.getTitle());
        holder.tvDuration.setText(TimeUtil.formatDuration(video.getDuration()));

        // 2. Thumbnail
        Glide.with(context)
                .load(video.getThumbnailURL())
                .placeholder(R.color.light_green_background)
                .centerCrop()
                .into(holder.ivThumbnail);

        // 3. Visibility Icon
        String vis = video.getVisibility();

        // ✅ NẾU VISIBILITY NULL HOẶC RỖNG -> MẶC ĐỊNH LÀ PRIVATE
        if (vis == null || vis.isEmpty()) {
            vis = "Private";
        }

        if ("Public".equalsIgnoreCase(vis)) {
            holder.ivVisibility.setImageResource(R.drawable.ic_public);
        } else if ("Unlisted".equalsIgnoreCase(vis)) {
            holder.ivVisibility.setImageResource(R.drawable.ic_link);
        } else {
            holder.ivVisibility.setImageResource(R.drawable.ic_lock);
        }

        // 4. Time Ago
        String timeAgo = "Just now";
        if (video.getCreatedAt() != null) {
            timeAgo = DateUtils.getRelativeTimeSpanString(
                    video.getCreatedAt().toDate().getTime(),
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS).toString();
        }

        // 5. Lấy Stats từ Realtime DB (View, Like, Comment)
        String finalTimeAgo = timeAgo;
        FirebaseDatabase.getInstance().getReference("videostat")
                .child(video.getVideoID())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        // Lấy View
                        long views = 0;
                        if (snapshot.child("viewCount").exists()) {
                            Object val = snapshot.child("viewCount").getValue();
                            if (val instanceof Long) views = (Long) val;
                            else if (val instanceof Integer) views = ((Integer) val).longValue();
                        }

                        // Lấy Like
                        long likes = snapshot.child("likes").getChildrenCount();

                        // Lấy Comment (Giả sử bạn lưu comment count hoặc đếm số node con)
                        // Nếu chưa có field commentCount, tạm thời để 0 hoặc query node comments
                        long comments = snapshot.child("comments").getChildrenCount(); // Hoặc query bảng comments riêng

                        // Cập nhật UI
                        holder.tvMeta.setText(formatCount(views) + " views • " + finalTimeAgo);
                        holder.tvLikeCount.setText(String.valueOf(likes));
                        holder.tvCommentCount.setText(String.valueOf(comments));
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        // Fallback
                        holder.tvMeta.setText("0 views • " + finalTimeAgo);
                    }
                });

        // 6. Click mở video
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, com.example.metube.ui.video.VideoActivity.class);
            intent.putExtra("video_id", video.getVideoID());
            context.startActivity(intent);
        });

        // 7. Click 3 chấm (Xử lý Edit/Delete sau)
        holder.btnMore.setOnClickListener(v -> {
            if (moreClickListener != null) {
                moreClickListener.onMoreClick(video);
            }
        });
    }

    private String formatCount(long count) {
        if (count == 0) return "No";
        if (count < 1000) return String.valueOf(count);
        return String.format(Locale.US, "%.1fK", count / 1000.0);
    }

    @Override public int getItemCount() { return videos != null ? videos.size() : 0; }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivThumbnail, ivVisibility, btnMore;
        TextView tvTitle, tvDuration, tvMeta, tvLikeCount, tvCommentCount;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumbnail = itemView.findViewById(R.id.iv_thumbnail);
            tvDuration = itemView.findViewById(R.id.tv_duration);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvMeta = itemView.findViewById(R.id.tv_meta);
            ivVisibility = itemView.findViewById(R.id.iv_visibility);
            tvLikeCount = itemView.findViewById(R.id.tv_like_count);
            tvCommentCount = itemView.findViewById(R.id.tv_comment_count);
            btnMore = itemView.findViewById(R.id.btn_more);
        }
    }
}