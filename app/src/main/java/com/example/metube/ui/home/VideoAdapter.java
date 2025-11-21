package com.example.metube.ui.home;

import android.content.Context;
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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.Timestamp;

import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoViewHolder> {

    public interface OnVideoClickListener {
        void onVideoClick(Video video);
    }

    private List<Video> videoList;
    private Context context;
    private final OnVideoClickListener clickListener;

    public VideoAdapter(Context context, List<Video> videoList, OnVideoClickListener clickListener) {
        this.context = context;
        this.videoList = videoList;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_video, parent, false);
        return new VideoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        Video currentVideo = videoList.get(position);
        holder.bind(currentVideo, context, clickListener);
    }

    @Override
    public int getItemCount() {
        return videoList != null ? videoList.size() : 0;
    }

    public void setVideos(List<Video> videos) {
        this.videoList = videos;
        notifyDataSetChanged();
    }

    public static class VideoViewHolder extends RecyclerView.ViewHolder {
        ImageView thumbnail;
        CircleImageView avatar;
        TextView title;
        TextView info;

        private DatabaseReference videoStatRef;
        private ValueEventListener viewListener;

        // Cache data để build info text
        private String channelName = "";
        private long viewCount = 0;
        private String timeAgo = "";

        public VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.video_thumbnail);
            avatar = itemView.findViewById(R.id.channel_avatar);
            title = itemView.findViewById(R.id.video_title);
            info = itemView.findViewById(R.id.video_info);
        }

        public void bind(final Video video, Context context, final OnVideoClickListener listener) {
            // Reset cache
            channelName = "";
            viewCount = 0;
            timeAgo = "";

            title.setText(video.getTitle());
            info.setText("Loading...");

            // Load uploader info from Firestore
            loadChannelInfo(video);

            // Load view count from Realtime Database
            listenToViewCount(video);

            // Format time ago
            timeAgo = formatTimeAgo(video.getCreatedAt());

            // Load thumbnail
            Glide.with(context)
                    .load(video.getThumbnailURL())
                    .placeholder(R.color.light_green_background)
                    .error(R.drawable.logo_metube)
                    .into(thumbnail);

            // Load avatar
            loadChannelAvatar(video.getUploaderID(), context);

            // Set click listener
            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onVideoClick(video);
            });
        }

        private void loadChannelInfo(Video video) {
            String uploaderId = video.getUploaderID();
            if (uploaderId == null || uploaderId.isEmpty()) {
                channelName = "Unknown Channel";
                updateInfoText();
                return;
            }

            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(uploaderId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String name = documentSnapshot.getString("name");
                            channelName = (name != null && !name.isEmpty()) ? name : "Unknown Channel";
                        } else {
                            channelName = "Unknown Channel";
                        }
                        updateInfoText();
                    })
                    .addOnFailureListener(e -> {
                        channelName = "Unknown Channel";
                        updateInfoText();
                    });
        }

        private void loadChannelAvatar(String uploaderId, Context context) {
            if (uploaderId == null || uploaderId.isEmpty()) return;

            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(uploaderId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String avatarUrl = documentSnapshot.getString("avatarURL");
                            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                                Glide.with(context)
                                        .load(avatarUrl)
                                        .placeholder(R.drawable.ic_person)
                                        .error(R.drawable.ic_person)
                                        .into(avatar);
                            }
                        }
                    });
        }

        private String formatTimeAgo(Timestamp timestamp) {
            if (timestamp == null) return "";
            return android.text.format.DateUtils.getRelativeTimeSpanString(
                    timestamp.toDate().getTime(),
                    System.currentTimeMillis(),
                    android.text.format.DateUtils.MINUTE_IN_MILLIS
            ).toString();
        }

        private String formatViewCount(long views) {
            if (views < 1000) {
                return views + " views";
            } else if (views < 1000000) {
                return String.format("%.1fK views", views / 1000.0);
            } else {
                return String.format("%.1fM views", views / 1000000.0);
            }
        }

        private void listenToViewCount(Video video) {
            if (video.getVideoID() == null) return;

            // Remove old listener if any
            if (videoStatRef != null && viewListener != null) {
                videoStatRef.removeEventListener(viewListener);
            }

            videoStatRef = FirebaseDatabase.getInstance()
                    .getReference("videostat")
                    .child(video.getVideoID())
                    .child("viewCount");

            // Listen for realtime updates
            viewListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    Long views = snapshot.getValue(Long.class);
                    viewCount = (views != null) ? views : 0L;
                    updateInfoText();
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    // If Realtime DB fails, try Firestore
                    loadViewCountFromFirestore(video.getVideoID());
                }
            };

            videoStatRef.addValueEventListener(viewListener);
        }

        private void loadViewCountFromFirestore(String videoId) {
            FirebaseFirestore.getInstance()
                    .collection("videostat")
                    .document(videoId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            Long views = documentSnapshot.getLong("viewCount");
                            viewCount = (views != null) ? views : 0L;
                            updateInfoText();
                        }
                    });
        }

        // ✅ Build info text với format: Channel • Views • Time
        private void updateInfoText() {
            StringBuilder infoBuilder = new StringBuilder();

            // Add channel name
            if (!channelName.isEmpty()) {
                infoBuilder.append(channelName);
            }

            // Add view count
            if (viewCount > 0 || !channelName.isEmpty()) {
                if (infoBuilder.length() > 0) infoBuilder.append(" • ");
                infoBuilder.append(formatViewCount(viewCount));
            }

            // Add time ago
            if (!timeAgo.isEmpty()) {
                if (infoBuilder.length() > 0) infoBuilder.append(" • ");
                infoBuilder.append(timeAgo);
            }

            // Update TextView
            String finalText = infoBuilder.length() > 0 ? infoBuilder.toString() : "Loading...";
            info.setText(finalText);
        }
    }
}