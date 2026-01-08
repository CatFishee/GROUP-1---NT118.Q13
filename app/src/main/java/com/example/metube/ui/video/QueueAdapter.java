package com.example.metube.ui.video;

import android.util.Log;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QueueAdapter extends RecyclerView.Adapter<QueueAdapter.QueueViewHolder> {

    private static final String TAG = "QueueAdapter";
    private List<Video> queueList = new ArrayList<>();
    private int currentPlayingPosition = -1;
    private OnQueueItemClickListener listener;
    private FirebaseFirestore firestore;

    public interface OnQueueItemClickListener {
        void onItemClick(int absolutePosition);
        void onMoreClick(int absolutePosition, View view);
        void onItemMove(int fromPosition, int toPosition);
    }

    public QueueAdapter(List<Video> queueList, OnQueueItemClickListener listener) {
        this.queueList = new ArrayList<>(queueList);
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
        // ✅ position ở đây đã là ABSOLUTE position trong queue
        Video video = queueList.get(position);

        holder.tvTitle.setText(video.getTitle());

        // Load thumbnail
        if (video.getThumbnailURL() != null && !video.getThumbnailURL().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(video.getThumbnailURL())
                    .placeholder(R.drawable.logo_metube)
                    .into(holder.ivThumbnail);
        }

        // Load channel name
        loadChannelName(video.getUploaderID(), holder.tvChannel);

        // Load video stats
        loadVideoStats(video, holder.tvStats);

        // ✅ Highlight video đang phát
        boolean isPlaying = (position == currentPlayingPosition);
        holder.viewNowPlayingBorder.setVisibility(isPlaying ? View.VISIBLE : View.GONE);

        // ✅ Badge "Next up" cho video tiếp theo
        boolean isNextUp = (currentPlayingPosition != -1 && position == currentPlayingPosition + 1);
        holder.tvNextUpBadge.setVisibility(isNextUp ? View.VISIBLE : View.GONE);

        // ✅ Click listeners với ABSOLUTE position
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                int absolutePos = holder.getBindingAdapterPosition();
                Log.d(TAG, "onItemClick: absolutePos = " + absolutePos);
                listener.onItemClick(absolutePos);
            }
        });

        holder.btnMore.setOnClickListener(v -> {
            if (listener != null) {
                int absolutePos = holder.getBindingAdapterPosition();
                Log.d(TAG, "onMoreClick: absolutePos = " + absolutePos);
                listener.onMoreClick(absolutePos, v);
            }
        });
    }

    @Override
    public int getItemCount() {
        return queueList.size();
    }

    // ✅ Update queue với absolute current position
    public void updateQueue(List<Video> newQueue, int currentPos) {
        Log.d(TAG, "updateQueue: " + newQueue.size() + " videos, currentPos = " + currentPos);
        this.queueList = new ArrayList<>(newQueue);
        this.currentPlayingPosition = currentPos;
        notifyDataSetChanged();
    }

    public void setCurrentPlayingPosition(int position) {
        int oldPosition = this.currentPlayingPosition;
        this.currentPlayingPosition = position;

        Log.d(TAG, "setCurrentPlayingPosition: " + oldPosition + " -> " + position);

        // Update both old and new positions
        if (oldPosition >= 0 && oldPosition < queueList.size()) {
            notifyItemChanged(oldPosition);
            if (oldPosition + 1 < queueList.size()) {
                notifyItemChanged(oldPosition + 1);
            }
        }
        if (position >= 0 && position < queueList.size()) {
            notifyItemChanged(position);
            if (position + 1 < queueList.size()) {
                notifyItemChanged(position + 1);
            }
        }
    }

    // ✅ FIXED: onItemMove với absolute positions
    public void onItemMove(int fromPosition, int toPosition) {
        if (fromPosition < 0 || fromPosition >= queueList.size() ||
                toPosition < 0 || toPosition >= queueList.size()) {
            return;
        }

        Log.d(TAG, "onItemMove: " + fromPosition + " -> " + toPosition);

        // Swap in local list
        Collections.swap(queueList, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);

        // Update current playing position
        if (currentPlayingPosition == fromPosition) {
            currentPlayingPosition = toPosition;
        } else if (fromPosition < currentPlayingPosition && toPosition >= currentPlayingPosition) {
            currentPlayingPosition--;
        } else if (fromPosition > currentPlayingPosition && toPosition <= currentPlayingPosition) {
            currentPlayingPosition++;
        }

        // Notify listener
        if (listener != null) {
            listener.onItemMove(fromPosition, toPosition);
        }
    }

    public void removeItem(int position) {
        if (position < 0 || position >= queueList.size()) {
            return;
        }

        Log.d(TAG, "removeItem: position = " + position);

        queueList.remove(position);
        notifyItemRemoved(position);

        // Update current playing position if needed
        if (position < currentPlayingPosition) {
            currentPlayingPosition--;
        } else if (position == currentPlayingPosition) {
            currentPlayingPosition = -1;
        }
    }

    // ✅ Load video stats từ Realtime Database
    private void loadVideoStats(Video video, TextView textView) {
        if (video.getVideoID() == null) {
            textView.setText("No stats");
            return;
        }

        DatabaseReference statRef = FirebaseDatabase.getInstance()
                .getReference("videostat")
                .child(video.getVideoID())
                .child("viewCount");

        statRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long views = snapshot.exists() ? snapshot.getValue(Long.class) : 0;
                String timeStr = (video.getCreatedAt() != null)
                        ? getRelativeTime(video.getCreatedAt())
                        : "";
                textView.setText(formatNumber(views) + " views • " + timeStr);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                textView.setText("Stats unavailable");
            }
        });
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