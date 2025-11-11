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

        public VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.video_thumbnail);
            avatar = itemView.findViewById(R.id.channel_avatar);
            title = itemView.findViewById(R.id.video_title);
            info = itemView.findViewById(R.id.video_info);
        }

        public void bind(final Video video, Context context, final OnVideoClickListener listener) {
            title.setText(video.getTitle());
            info.setText("Loading...");

            // Load uploader info from Firestore
            loadChannelAndVideoInfo(video, info);

            // Load thumbnail
            Glide.with(context)
                    .load(video.getThumbnailURL())
                    .placeholder(R.color.light_green_background)
                    .error(R.drawable.logo_metube)
                    .into(thumbnail);

            // Set click listener
            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onVideoClick(video);
            });

            // Load realtime view count
            listenToViewCount(video);
        }

        private void loadChannelAndVideoInfo(Video video, TextView infoTextView) {
            String uploaderId = video.getUploaderID();
            if (uploaderId == null || uploaderId.isEmpty()) {
                infoTextView.setText("Unknown Channel");
                return;
            }

            FirebaseFirestore.getInstance().collection("users").document(uploaderId).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        String channelName = "Unknown Channel";
                        if (documentSnapshot.exists()) {
                            String name = documentSnapshot.getString("name");
                            if (name != null && !name.isEmpty()) channelName = name;
                        }

                        String timeAgo = formatTimeAgo(video.getCreatedAt());
                        infoTextView.setText(channelName + " • " + timeAgo);
                    })
                    .addOnFailureListener(e -> infoTextView.setText("Unknown Channel"));
        }

        private String formatTimeAgo(Timestamp timestamp) {
            if (timestamp == null) return "";
            return android.text.format.DateUtils.getRelativeTimeSpanString(
                    timestamp.toDate().getTime(),
                    System.currentTimeMillis(),
                    android.text.format.DateUtils.MINUTE_IN_MILLIS).toString();
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
                    .child("views");

            // Listen for realtime updates
            viewListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    Long viewCount = snapshot.getValue(Long.class);
                    if (viewCount == null) viewCount = 0L;

                    // Append view count below uploader info
                    String currentText = info.getText().toString();
                    if (!currentText.contains("views")) {
                        info.setText(currentText + " • " + viewCount + " views");
                    } else {
                        info.setText(currentText.replaceAll("\\d+ views", viewCount + " views"));
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    // ignored
                }
            };

            videoStatRef.addValueEventListener(viewListener);
        }
    }
}
