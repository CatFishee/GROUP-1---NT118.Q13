package com.example.metube.ui.home;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
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
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.Timestamp;

import java.util.List;
import java.util.Locale;

import de.hdodenhof.circleimageview.CircleImageView;

public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoViewHolder> {

    public interface OnVideoClickListener {
        void onVideoClick(Video video);
    }

    private List<Video> videoList;
    private Context context;
    private final OnVideoClickListener clickListener;

    private ExoPlayer sharedPlayer;
    private Player.Listener currentVideoListener;
    private int currentPlayingPos = -1;
    private boolean isAutoplayEnabled = false;


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
    // 1. Khởi tạo Player tối ưu (Mute + Low Buffer)
    private void initPlayer(Context context) {
        if (sharedPlayer == null) {
            // ✅ Giảm buffer để start nhanh hơn
            // minBuffer: 1.5s, maxBuffer: 3s, playback: 500ms, rebuffer: 1s
            DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                            1500,  // minBufferMs - tối thiểu buffer trước khi play
                            3000,  // maxBufferMs - tối đa buffer
                            500,   // bufferForPlaybackMs - buffer để bắt đầu phát
                            1000   // bufferForPlaybackAfterRebufferMs - buffer sau khi rebuffer
                    )
                    .build();

            sharedPlayer = new ExoPlayer.Builder(context)
                    .setLoadControl(loadControl)
                    .build();

            sharedPlayer.setVolume(0f); // MUTE
            sharedPlayer.setRepeatMode(Player.REPEAT_MODE_ONE); // Loop
        }
    }

    // 2. Hàm điều khiển phát từ Activity
    public void playVideoAt(int position) {
        // ✅ 1. Kiểm tra nếu đang phát video này rồi thì bỏ qua
        if (position == currentPlayingPos && sharedPlayer != null && sharedPlayer.isPlaying()) {
            return;
        }

        // ✅ 2. Validate
        if (position < 0 || position >= videoList.size() || !isAutoplayEnabled) {
            return;
        }

        // ✅ 3. Dừng video cũ (nếu có)
        int lastPos = currentPlayingPos;
        currentPlayingPos = position;

        if (lastPos != -1 && lastPos != currentPlayingPos) {
            notifyItemChanged(lastPos, "DETACH_PLAYER");
        }

        // ✅ 4. Init player nếu chưa có
        initPlayer(context);

        // ✅ 5. Gỡ listener cũ
        if (currentVideoListener != null) {
            sharedPlayer.removeListener(currentVideoListener);
        }

        // ✅ 6. Tạo listener mới để ẩn thumbnail khi video render xong
        currentVideoListener = new Player.Listener() {
            @Override
            public void onRenderedFirstFrame() {
                notifyItemChanged(currentPlayingPos, "HIDE_THUMBNAIL");
            }
        };
        sharedPlayer.addListener(currentVideoListener);

        // ✅ 7. Set MediaItem và prepare
        Video video = videoList.get(position);
        sharedPlayer.setMediaItem(MediaItem.fromUri(video.getVideoURL()));

        // ✅ 8. Prepare + Play ngay
        sharedPlayer.prepare();
        sharedPlayer.setPlayWhenReady(true); // Play ngay khi prepare xong

        // ✅ 9. Attach player vào view
        notifyItemChanged(currentPlayingPos, "ATTACH_PLAYER");
    }

    public void stopVideo() {
        if (sharedPlayer != null) {
            sharedPlayer.stop();
            currentPlayingPos = -1;
            notifyDataSetChanged();
        }
    }

    public void releasePlayer() {
        if (sharedPlayer != null) {
            if (currentVideoListener != null) {
                sharedPlayer.removeListener(currentVideoListener);
            }
            sharedPlayer.release();
            sharedPlayer = null;
        }
    }

    public void setAutoplayEnabled(boolean enabled) {
        this.isAutoplayEnabled = enabled;
        if (!enabled) stopVideo();
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        Video currentVideo = videoList.get(position);
        holder.bind(currentVideo, context, clickListener);

        // ✅ Logic hiển thị PlayerView
        if (position == currentPlayingPos && sharedPlayer != null) {
            holder.playerView.setVisibility(View.VISIBLE);
            holder.playerView.setPlayer(sharedPlayer);
            // Không ẩn thumbnail ở đây, để listener tự ẩn khi ready
        } else {
            holder.playerView.setVisibility(View.GONE);
            holder.playerView.setPlayer(null);
            holder.thumbnail.setVisibility(View.VISIBLE);
        }
    }
    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            for (Object payload : payloads) {
                if (payload.equals("ATTACH_PLAYER")) {
                    // Chỉ hiện PlayerView và gắn Player, tuyệt đối không chạm vào info text
                    holder.playerView.setVisibility(View.VISIBLE);
                    holder.playerView.setPlayer(sharedPlayer);
                } else if (payload.equals("HIDE_THUMBNAIL")) {
                    // Chỉ ẩn thumbnail khi video đã chạy
                    holder.thumbnail.setVisibility(View.INVISIBLE);
                } else if (payload.equals("DETACH_PLAYER")) {
                    // Trả về trạng thái thumbnail bình thường cho bài cũ
                    holder.playerView.setVisibility(View.GONE);
                    holder.playerView.setPlayer(null);
                    holder.thumbnail.setVisibility(View.VISIBLE);
                }
            }
        } else {
            // Chỉ khi trượt tới item mới hoàn toàn (chưa có data) mới gọi bind()
            onBindViewHolder(holder, position);
        }
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
        TextView title, tvDurationOverlay;
        TextView info;

        private DatabaseReference videoStatRef;
        private ValueEventListener viewListener;

        // Cache data để build info text
        private String channelName = "";
        private long viewCount = 0;
        private String timeAgo = "";
        private String currentBoundVideoId = ""; // Để kiểm tra tránh nạp lại dữ liệu cũ
        PlayerView playerView;

        public VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.video_thumbnail);
            avatar = itemView.findViewById(R.id.channel_avatar);
            title = itemView.findViewById(R.id.video_title);
            info = itemView.findViewById(R.id.video_info);
            playerView = itemView.findViewById(R.id.player_view_autoplay);
            tvDurationOverlay = itemView.findViewById(R.id.tv_duration_overlay);
        }

        public void bind(final Video video, Context context, final OnVideoClickListener listener) {
            // Reset cache
            // Kiểm tra nếu videoId giống video đang hiện thì không reset chữ (Tránh nhảy chữ)
            if (currentBoundVideoId.equals(video.getVideoID())) {
                // Chỉ cập nhật listener click (phòng trường hợp listener thay đổi)
                itemView.setOnClickListener(v -> {
                    if (listener != null) listener.onVideoClick(video);
                });
                return;
            }

            // Nếu là video mới hoàn toàn thì mới reset
            currentBoundVideoId = video.getVideoID();
            channelName = "";
            viewCount = 0;
            timeAgo = "";

            title.setText(video.getTitle());
            if (info.getText().toString().isEmpty() || info.getText().equals("Loading...")) {
                info.setText("Loading...");
            }
            if (video.getDuration() > 0) {
                tvDurationOverlay.setVisibility(View.VISIBLE);
                tvDurationOverlay.setText(formatDuration(video.getDuration()));
            } else {
                tvDurationOverlay.setVisibility(View.GONE);
            }


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
                            String avatarUrl = documentSnapshot.getString("profileURL");
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
        private String formatDuration(long durationMs) {
            long seconds = durationMs / 1000;
            long h = seconds / 3600;
            long m = (seconds % 3600) / 60;
            long s = seconds % 60;
            if (h > 0) return String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s);
            return String.format(Locale.getDefault(), "%02d:%02d", m, s);
        }
    }
}