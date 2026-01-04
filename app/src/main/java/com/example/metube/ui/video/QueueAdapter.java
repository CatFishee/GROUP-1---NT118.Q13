package com.example.metube.ui.video;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.metube.R;
import com.example.metube.model.Video;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.Collections;
import java.util.List;

public class QueueAdapter extends RecyclerView.Adapter<QueueAdapter.QueueViewHolder> {

    private List<Video> queueList;
    private int currentPlayingPosition = -1;
    private OnQueueItemClickListener listener;
    private FirebaseFirestore firestore;

    public interface OnQueueItemClickListener {
        void onItemClick(int position);
        void onMoreClick(int position, View view);
        void onItemMove(int fromPosition, int toPosition);
    }

    public QueueAdapter(List<Video> queueList, OnQueueItemClickListener listener) {
        this.queueList = queueList;
        this.listener = listener;
        this.firestore = FirebaseFirestore.getInstance();
    }

    @NonNull
    @Override
    public QueueViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_queue_video, parent, false);
        return new QueueViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull QueueViewHolder holder, int position) {
        Video video = queueList.get(position);
        holder.tvTitle.setText(video.getTitle());

        if (video.getThumbnailURL() != null && !video.getThumbnailURL().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(video.getThumbnailURL())
                    .placeholder(R.drawable.logo_metube)
                    .into(holder.ivThumbnail);
        }

        loadChannelName(video.getUploaderID(), holder.tvChannel);

        // GỌI HÀM LOAD STATS TỪ REALTIME DB
        loadVideoStats(video, holder.tvStats);

        // Trạng thái đang phát
        holder.viewNowPlayingBorder.setVisibility(position == currentPlayingPosition ? View.VISIBLE : View.GONE);
        holder.tvNextUpBadge.setVisibility((currentPlayingPosition != -1 && position == currentPlayingPosition + 1) ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(holder.getBindingAdapterPosition());
        });

        holder.btnMore.setOnClickListener(v -> {
            if (listener != null) listener.onMoreClick(holder.getBindingAdapterPosition(), v);
        });
    }

    // ĐƯA HÀM NÀY RA NGOÀI onBindViewHolder
    private void loadVideoStats(Video video, TextView textView) {
        if (video.getVideoID() == null) return;
        DatabaseReference statRef = FirebaseDatabase.getInstance().getReference("videostat")
                .child(video.getVideoID()).child("viewCount");

        statRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long views = snapshot.exists() ? snapshot.getValue(Long.class) : 0;
                String timeStr = (video.getCreatedAt() != null) ? getRelativeTime(video.getCreatedAt()) : "";
                textView.setText(formatNumber(views) + " views • " + timeStr);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    @Override
    public int getItemCount() {
        return queueList.size();
    }

    public void setCurrentPlayingPosition(int position) {
        int oldPosition = this.currentPlayingPosition;
        this.currentPlayingPosition = position;

        // Update both old and new positions
        if (oldPosition >= 0 && oldPosition < queueList.size()) {
            notifyItemChanged(oldPosition);
            // Also update the next video badge
            if (oldPosition + 1 < queueList.size()) {
                notifyItemChanged(oldPosition + 1);
            }
        }
        if (position >= 0 && position < queueList.size()) {
            notifyItemChanged(position);
            // Also update the next video badge
            if (position + 1 < queueList.size()) {
                notifyItemChanged(position + 1);
            }
        }
    }

    public void onItemMove(int fromPosition, int toPosition) {
        Collections.swap(queueList, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);

        // Update current playing position if affected by the move
        if (currentPlayingPosition == fromPosition) {
            currentPlayingPosition = toPosition;
        } else if (fromPosition < currentPlayingPosition && toPosition >= currentPlayingPosition) {
            currentPlayingPosition--;
        } else if (fromPosition > currentPlayingPosition && toPosition <= currentPlayingPosition) {
            currentPlayingPosition++;
        }

        if (listener != null) {
            listener.onItemMove(fromPosition, toPosition);
        }
    }

    public void removeItem(int position) {
        queueList.remove(position);
        notifyItemRemoved(position);

        // Update current playing position if needed
        if (position < currentPlayingPosition) {
            currentPlayingPosition--;
        } else if (position == currentPlayingPosition) {
            currentPlayingPosition = -1;
        }
    }

    public void updateQueue(List<Video> newQueue, int currentPos) {
        this.queueList = newQueue;
        this.currentPlayingPosition = currentPos;
        notifyDataSetChanged();
    }

    private void loadChannelName(String uploaderID, TextView textView) {
        if (uploaderID == null || uploaderID.isEmpty()) {
            textView.setText("Unknown");
            return;
        }

        firestore.collection("users").document(uploaderID).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String name = doc.getString("name");
                        textView.setText(name != null ? name : "Unknown");
                    } else {
                        textView.setText("Unknown");
                    }
                })
                .addOnFailureListener(e -> textView.setText("Unknown"));
    }

    private String formatViewCount(Video video) {
        // You might want to load this from Firebase Realtime Database
        return "views";
    }
    // Hàm format số (1500 -> 1.5K)
    private String formatNumber(long count) {
        if (count < 1000) return String.valueOf(count);
        if (count < 1000000) return String.format("%.1fK", count / 1000.0);
        return String.format("%.1fM", count / 1000000.0);
    }
    private String getRelativeTime(com.google.firebase.Timestamp timestamp) {
        long timeInMillis = timestamp.toDate().getTime();
        long now = System.currentTimeMillis();
        long diff = now - timeInMillis;

        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        long weeks = days / 7;
        long months = days / 30;
        long years = days / 365;

        if (years > 0) return years + " year" + (years > 1 ? "s" : "") + " ago";
        if (months > 0) return months + " month" + (months > 1 ? "s" : "") + " ago";
        if (weeks > 0) return weeks + " week" + (weeks > 1 ? "s" : "") + " ago";
        if (days > 0) return days + " day" + (days > 1 ? "s" : "") + " ago";
        if (hours > 0) return hours + " hour" + (hours > 1 ? "s" : "") + " ago";
        if (minutes > 0) return minutes + " minute" + (minutes > 1 ? "s" : "") + " ago";
        return "just now";
    }

    static class QueueViewHolder extends RecyclerView.ViewHolder {
        ImageView ivDragHandle, ivThumbnail;
        View viewNowPlayingBorder;
        TextView tvTitle, tvChannel, tvStats, tvDuration, tvNextUpBadge;
        ImageButton btnMore;

        public QueueViewHolder(@NonNull View itemView) {
            super(itemView);
            ivDragHandle = itemView.findViewById(R.id.iv_drag_handle);
            ivThumbnail = itemView.findViewById(R.id.iv_queue_thumbnail);
            viewNowPlayingBorder = itemView.findViewById(R.id.view_now_playing_border);
            tvTitle = itemView.findViewById(R.id.tv_queue_title);
            tvChannel = itemView.findViewById(R.id.tv_queue_channel);
            tvStats = itemView.findViewById(R.id.tv_queue_stats);
            tvDuration = itemView.findViewById(R.id.tv_video_duration);
            tvNextUpBadge = itemView.findViewById(R.id.tv_next_up_badge);
            btnMore = itemView.findViewById(R.id.btn_queue_more);
        }
    }
}