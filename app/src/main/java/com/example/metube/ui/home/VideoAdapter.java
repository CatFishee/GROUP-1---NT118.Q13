package com.example.metube.ui.home;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.metube.R;
import com.example.metube.model.Video;
import com.example.metube.ui.video.VideoActivity;
import com.example.metube.utils.DownloadUtil;
import com.example.metube.utils.ShareUtil;
import com.example.metube.utils.VideoQueueManager;
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
        void onAvatarClick(String uploaderId);
    }

    private List<Video> videoList;
    private Context context;
    private final OnVideoClickListener clickListener;

    private ExoPlayer sharedPlayer;
    private Player.Listener currentVideoListener;
    private int currentPlayingPos = -1;
    private boolean isAutoplayEnabled = false;
    private boolean isHistoryMode = false;


    public VideoAdapter(Context context, List<Video> videoList, OnVideoClickListener clickListener) {
        this.context = context;
        this.videoList = filterVideos(videoList, false);
        this.clickListener = clickListener;
    }

    // Constructor với history mode
    public VideoAdapter(Context context, List<Video> videoList, OnVideoClickListener clickListener, boolean isHistoryMode) {
        this.context = context;
        this.clickListener = clickListener;
        this.isHistoryMode = isHistoryMode;
        this.videoList = filterVideos(videoList, isHistoryMode);
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
            DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                    .setBufferDurationsMs(1500, 3000, 500, 1000)
                    .build();

            sharedPlayer = new ExoPlayer.Builder(context)
                    .setLoadControl(loadControl)
                    .build();

            sharedPlayer.setVolume(0f);
            sharedPlayer.setRepeatMode(Player.REPEAT_MODE_ONE);
        }
    }

    // 2. Hàm điều khiển phát từ Activity
    public void playVideoAt(int position) {
        if (position == currentPlayingPos && sharedPlayer != null && sharedPlayer.isPlaying()) {
            return;
        }

        if (position < 0 || position >= videoList.size() || !isAutoplayEnabled) {
            return;
        }

        int lastPos = currentPlayingPos;
        currentPlayingPos = position;

        if (lastPos != -1 && lastPos != currentPlayingPos) {
            notifyItemChanged(lastPos, "DETACH_PLAYER");
        }

        initPlayer(context);

        if (currentVideoListener != null) {
            sharedPlayer.removeListener(currentVideoListener);
        }

        currentVideoListener = new Player.Listener() {
            @Override
            public void onRenderedFirstFrame() {
                notifyItemChanged(currentPlayingPos, "HIDE_THUMBNAIL");
            }
        };
        sharedPlayer.addListener(currentVideoListener);

        Video video = videoList.get(position);
        sharedPlayer.setMediaItem(MediaItem.fromUri(video.getVideoURL()));
        sharedPlayer.prepare();
        sharedPlayer.setPlayWhenReady(true);

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

        // Logic hiển thị PlayerView
        if (position == currentPlayingPos && sharedPlayer != null) {
            holder.playerView.setVisibility(View.VISIBLE);
            holder.playerView.setPlayer(sharedPlayer);
        } else {
            holder.playerView.setVisibility(View.GONE);
            holder.playerView.setPlayer(null);
            holder.thumbnail.setVisibility(View.VISIBLE);
        }


        // ✅ SỬA LỖI: Dùng currentVideo thay vì video
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, VideoActivity.class);
            intent.putExtra("video_id", currentVideo.getVideoID());
            context.startActivity(intent);
        });

        // Click vào nút 3 chấm
        holder.btnOptions.setOnClickListener(v -> {
            showVideoOptions(currentVideo, position);
        });

    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            for (Object payload : payloads) {
                if (payload.equals("ATTACH_PLAYER")) {
                    holder.playerView.setVisibility(View.VISIBLE);
                    holder.playerView.setPlayer(sharedPlayer);
                } else if (payload.equals("HIDE_THUMBNAIL")) {
                    holder.thumbnail.setVisibility(View.INVISIBLE);
                } else if (payload.equals("DETACH_PLAYER")) {
                    holder.playerView.setVisibility(View.GONE);
                    holder.playerView.setPlayer(null);
                    holder.thumbnail.setVisibility(View.VISIBLE);
                }
            }
        } else {
            onBindViewHolder(holder, position);
        }
    }

    private void showVideoOptions(Video video, int position) {
        VideoOptionsBottomSheet.show(
                context,
                video,
                isHistoryMode,
                new VideoOptionsBottomSheet.OnOptionSelectedListener() {
                    @Override
                    public void onPlayNext(Video video) {
                        VideoQueueManager.getInstance().playNext(video);
                        Toast.makeText(context, "Added to play next", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onSaveToPlaylist(Video video) {
                        Toast.makeText(context, "Save to playlist feature coming soon", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onDownload(Video video) {
                        // ✅ SỬ DỤNG DownloadUtil
                        if (video.getVideoURL() != null && !video.getVideoURL().isEmpty()) {
                            DownloadUtil.downloadVideo(
                                    context,
                                    video.getVideoURL(),
                                    video.getTitle() != null ? video.getTitle() : "video"
                            );
                        } else {
                            Toast.makeText(context, "Video URL not available", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onShare(Video video) {
                        if (video != null && video.getVideoURL() != null) {
                            ShareUtil.shareVideo(context, video.getVideoURL());
                        }
                    }

                    @Override
                    public void onDontRecommend(Video video) {
                        Toast.makeText(context, "Channel will not be recommended", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onRemoveFromHistory(Video video) {
                        videoList.remove(position);
                        notifyItemRemoved(position);
                        notifyItemRangeChanged(position, videoList.size());
                        Toast.makeText(context, "Removed from watch history", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    @Override
    public int getItemCount() {
        return videoList != null ? videoList.size() : 0;
    }

    public void setVideos(List<Video> videos) {
        this.videoList = filterVideos(videos, this.isHistoryMode);
        notifyDataSetChanged();
    }
    private List<Video> filterVideos(List<Video> originalList, boolean historyMode) {
        if (originalList == null) return new java.util.ArrayList<>();

        // Nếu đang ở màn hình Lịch sử (historyMode = true), hiển thị tất cả
        if (historyMode) {
            return originalList;
        }

        // Nếu ở Homepage (historyMode = false), chỉ lấy video "Public"
        List<Video> filteredList = new java.util.ArrayList<>();
        for (Video v : originalList) {
            if (v.getVisibility() != null && v.getVisibility().equalsIgnoreCase("Public")) {
                filteredList.add(v);
            }
        }
        return filteredList;
    }

    public static class VideoViewHolder extends RecyclerView.ViewHolder {
        ImageView thumbnail;
        CircleImageView avatar;
        TextView title, tvDurationOverlay;
        TextView info;
        PlayerView playerView;
        ImageButton btnOptions; // ✅ THÊM DÒNG NÀY

        private DatabaseReference videoStatRef;
        private ValueEventListener viewListener;

        private String channelName = "";
        private long viewCount = 0;
        private String timeAgo = "";
        private String currentBoundVideoId = "";

        public VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.video_thumbnail);
            avatar = itemView.findViewById(R.id.channel_avatar);
            title = itemView.findViewById(R.id.video_title);
            info = itemView.findViewById(R.id.video_info);
            playerView = itemView.findViewById(R.id.player_view_autoplay);
            tvDurationOverlay = itemView.findViewById(R.id.tv_duration_overlay);
            btnOptions = itemView.findViewById(R.id.btn_video_options); // ✅ THÊM DÒNG NÀY
        }

        public void bind(final Video video, Context context, final OnVideoClickListener listener) {
            if (currentBoundVideoId.equals(video.getVideoID())) {
                itemView.setOnClickListener(v -> {
                    if (listener != null) listener.onVideoClick(video);
                });
                return;
            }

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

            loadChannelInfo(video);
            listenToViewCount(video);
            timeAgo = formatTimeAgo(video.getCreatedAt());

            Glide.with(context)
                    .load(video.getThumbnailURL())
                    .placeholder(R.color.light_green_background)
                    .error(R.drawable.logo_metube)
                    .into(thumbnail);

            loadChannelAvatar(video.getUploaderID(), context);
            avatar.setOnClickListener(v -> {
                if (listener != null && video.getUploaderID() != null) {
                    listener.onAvatarClick(video.getUploaderID());
                }
            });

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

            if (videoStatRef != null && viewListener != null) {
                videoStatRef.removeEventListener(viewListener);
            }

            videoStatRef = FirebaseDatabase.getInstance()
                    .getReference("videostat")
                    .child(video.getVideoID())
                    .child("viewCount");

            viewListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    Long views = snapshot.getValue(Long.class);
                    viewCount = (views != null) ? views : 0L;
                    updateInfoText();
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
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

        private void updateInfoText() {
            StringBuilder infoBuilder = new StringBuilder();

            if (!channelName.isEmpty()) {
                infoBuilder.append(channelName);
            }

            if (viewCount > 0 || !channelName.isEmpty()) {
                if (infoBuilder.length() > 0) infoBuilder.append(" • ");
                infoBuilder.append(formatViewCount(viewCount));
            }

            if (!timeAgo.isEmpty()) {
                if (infoBuilder.length() > 0) infoBuilder.append(" • ");
                infoBuilder.append(timeAgo);
            }

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