package com.example.metube.ui.video;

import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.bumptech.glide.Glide;
import com.example.metube.R;
import com.example.metube.model.User;
import com.example.metube.model.Video;
import com.example.metube.utils.VideoQueueManager; // Đảm bảo bạn đã tạo file này
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

public class VideoActivity extends AppCompatActivity {

    private static final String TAG = "VideoActivity";

    // UI Components
    private PlayerView playerView;
    private TextView tvTitle, tvUploader, tvDescription, tvVideoStats;
    private MaterialButton btnLike, btnDislike;
    private CircleImageView ivChannelAvatar;
    private Button btnSubscribe;
    private MaterialCardView cardDescription;
    private ScrollView scrollView;

    // Player & Logic
    private ExoPlayer player;
    private DefaultTrackSelector trackSelector;
    private boolean isFullscreen = false;
    private boolean isDescriptionExpanded = false;
    private boolean hasViewCountBeenIncremented = false;

    // Data Variables
    private String currentVideoId; // ID video đang phát
    private String currentUploaderName = "";
    private String currentRelativeTime = "";
    private long startPosition = 0;

    // Firebase
    private FirebaseFirestore firestore;
    private FirebaseUser currentUser;
    private DatabaseReference videoStatRef;
    private ValueEventListener statListener;
    private com.google.firebase.firestore.ListenerRegistration userSettingsListener;

    // State
    private enum UserVoteState { NONE, LIKED, DISLIKED }
    private UserVoteState currentUserVoteState = UserVoteState.NONE;
    private boolean isHistoryRecordingEnabled = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);

        firestore = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        // 1. Nhận dữ liệu từ Intent
        currentVideoId = getIntent().getStringExtra("video_id");
        startPosition = getIntent().getLongExtra("resume_position", 0);

        if (currentVideoId == null && VideoQueueManager.getInstance().getQueue().isEmpty()) {
            Toast.makeText(this, "No video to play.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 2. Xử lý nút Back
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isFullscreen) {
                    exitFullscreen();
                } else {
                    setEnabled(false);
                    finish();
                }
            }
        });

        // 3. Init UI & Logic
        initViews();
        setupDescriptionToggle();
    }

    private void initViews() {
        playerView = findViewById(R.id.player_view);
        tvTitle = findViewById(R.id.tv_video_title);
        tvUploader = findViewById(R.id.tv_video_uploader);
        tvDescription = findViewById(R.id.tv_video_description);
        tvVideoStats = findViewById(R.id.tv_video_stats);
        btnLike = findViewById(R.id.btn_like);
        btnDislike = findViewById(R.id.btn_dislike);
        ivChannelAvatar = findViewById(R.id.iv_channel_avatar);
        btnSubscribe = findViewById(R.id.btn_subscribe);
        cardDescription = findViewById(R.id.card_description);
        scrollView = findViewById(R.id.scroll_view_content);

        btnLike.setOnClickListener(v -> onLikeClicked());
        btnDislike.setOnClickListener(v -> onDislikeClicked());
    }

    private void setupDescriptionToggle() {
        cardDescription.setOnClickListener(v -> {
            if (isDescriptionExpanded) {
                tvDescription.setMaxLines(3);
                isDescriptionExpanded = false;
            } else {
                tvDescription.setMaxLines(Integer.MAX_VALUE);
                isDescriptionExpanded = true;
            }
        });
    }

    // --- LIFECYCLE ---

    @Override
    protected void onStart() {
        super.onStart();
        initializePlayer();
        startListeningToUserStatus(); // Lắng nghe cài đặt Pause History
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null) {
            player.play();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) {
            // Lưu lịch sử trước khi pause
            long currentPos = player.getCurrentPosition();
            long duration = player.getDuration();

            // Logic lưu thông minh: Xem > 5s và chưa hết
            if (currentPos > 5000 && duration > 0) {
                if ((duration - currentPos) < 1000) {
                    saveWatchHistory(0); // Đã xem hết -> Reset
                } else {
                    saveWatchHistory(currentPos); // Đang xem dở
                }
            }
            player.pause();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (userSettingsListener != null) {
            userSettingsListener.remove();
        }
        releasePlayer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (videoStatRef != null && statListener != null) {
            videoStatRef.removeEventListener(statListener);
        }
    }

    // --- PLAYER INITIALIZATION (QUAN TRỌNG) ---

    private void initializePlayer() {
        if (player == null) {
            trackSelector = new DefaultTrackSelector(this);
            player = new ExoPlayer.Builder(this)
                    .setTrackSelector(trackSelector)
                    .build();
            playerView.setPlayer(player);
            setupCustomPlayerControls();

            // Lắng nghe sự kiện chuyển bài (cho Queue)
            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_READY && player.getPlayWhenReady() && !hasViewCountBeenIncremented) {
                        incrementViewCount();
                        hasViewCountBeenIncremented = true;
                    }
                }

                @Override
                public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                    // Khi chuyển sang video khác trong danh sách phát
                    if (mediaItem != null && mediaItem.localConfiguration != null) {
                        Video video = (Video) mediaItem.localConfiguration.tag;
                        if (video != null) {
                            // Reset trạng thái view count cho video mới
                            hasViewCountBeenIncremented = false;
                            // Cập nhật UI (Title, Desc...) cho video mới
                            updateUIForCurrentVideo(video);
                            // Cập nhật currentVideoId để dùng cho Like/History
                            currentVideoId = video.getVideoID();
                            // Load lại stats của video mới
                            fetchAndListenToVideoStats(currentVideoId);
                        }
                    }
                }
            });
        }

        // --- LOAD DANH SÁCH PHÁT ---
        List<Video> queue = VideoQueueManager.getInstance().getQueue();

        // Nếu Queue rỗng (mở từ Home/Search lẻ), ta tự thêm video hiện tại vào Queue giả
        if (queue.isEmpty() && currentVideoId != null) {
            // Cần load video info trước để tạo object Video (hoặc query nhanh)
            // Ở đây để đơn giản, ta load details rồi play 1 bài
            loadSingleVideoAndPlay(currentVideoId);
        } else {
            // Nếu Queue có sẵn (từ nút Play All)
            playFromQueue();
        }
    }

    private void playFromQueue() {
        List<Video> queue = VideoQueueManager.getInstance().getQueue();
        int startIndex = VideoQueueManager.getInstance().getCurrentPosition();

        player.clearMediaItems();
        for (Video v : queue) {
            MediaItem mediaItem = MediaItem.fromUri(Uri.parse(v.getVideoURL()));
            // Gắn Tag để lấy lại thông tin khi chuyển bài
            MediaItem itemWithMeta = mediaItem.buildUpon().setTag(v).build();
            player.addMediaItem(itemWithMeta);
        }

        // Seek đến bài đang chọn và vị trí resume (nếu có)
        player.seekTo(startIndex, startPosition);
        player.prepare();
        player.play();
    }

    private void loadSingleVideoAndPlay(String videoId) {
        firestore.collection("videos").document(videoId).get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) return;
                    Video video = snapshot.toObject(Video.class);
                    if (video == null) return;
                    video.setVideoID(snapshot.getId()); // Đảm bảo có ID

                    // Setup Player cho 1 bài
                    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(video.getVideoURL()));
                    // Gắn Tag
                    MediaItem itemWithMeta = mediaItem.buildUpon().setTag(video).build();

                    player.setMediaItem(itemWithMeta);

                    // Tua đến vị trí Resume (nếu có)
                    if (startPosition > 0) {
                        player.seekTo(startPosition);
                        Toast.makeText(this, "Resuming video", Toast.LENGTH_SHORT).show();
                    }

                    player.prepare();
                    player.play();

                    // Cập nhật UI ngay lập tức
                    updateUIForCurrentVideo(video);
                    fetchAndListenToVideoStats(videoId);
                });
    }

    // --- UI UPDATES ---

    private void updateUIForCurrentVideo(Video video) {
        if (video == null) return;

        // 1. Cập nhật Text
        tvTitle.setText(video.getTitle());
        tvDescription.setText(video.getDescription() != null ? video.getDescription() : "");
        if (video.getCreatedAt() != null) {
            currentRelativeTime = getRelativeTime(video.getCreatedAt());
        }

        // 2. Load thông tin kênh (Uploader)
        firestore.collection("users").document(video.getUploaderID()).get()
                .addOnSuccessListener(doc -> {
                    String name = doc.exists() ? doc.getString("name") : "Unknown";
                    currentUploaderName = name;
                    tvUploader.setText(name);

                    String avatar = doc.getString("profileURL");
                    if (avatar != null) Glide.with(this).load(avatar).into(ivChannelAvatar);

                    // Gọi lại update stats để refresh tên người đăng
                    // (Lấy viewCount hiện tại từ view cũ nếu có, hoặc để listener tự update)
                });
    }

    // --- CÁC HÀM TIỆN ÍCH KHÁC (GIỮ NGUYÊN LOGIC CỦA BẠN) ---

    private void setupCustomPlayerControls() {
        ImageButton settingsButton = playerView.findViewById(R.id.exo_settings_button);
        ImageButton volumeButton = playerView.findViewById(R.id.exo_volume_button);
        ImageButton fullscreenButton = playerView.findViewById(R.id.exo_fullscreen_button);

        settingsButton.setOnClickListener(this::showSettingsMenu);

        volumeButton.setOnClickListener(v -> {
            boolean isMuted = player.getVolume() == 0f;
            player.setVolume(isMuted ? 1f : 0f);
            volumeButton.setImageResource(isMuted ? R.drawable.ic_volume_up : R.drawable.ic_volume_off);
        });

        fullscreenButton.setOnClickListener(v -> {
            if (isFullscreen) exitFullscreen(); else enterFullscreen();
        });
    }

    private void showSettingsMenu(View view) {
        PopupMenu popup = new PopupMenu(this, view);
        popup.getMenu().add(0, 1, 0, "0.5x");
        popup.getMenu().add(0, 2, 0, "1.0x (Normal)");
        popup.getMenu().add(0, 3, 0, "1.5x");
        popup.getMenu().add(0, 4, 0, "2.0x");

        popup.setOnMenuItemClickListener(item -> {
            float speed = 1.0f;
            switch (item.getItemId()) {
                case 1: speed = 0.5f; break;
                case 3: speed = 1.5f; break;
                case 4: speed = 2.0f; break;
            }
            player.setPlaybackParameters(new PlaybackParameters(speed));
            return true;
        });
        popup.show();
    }

    private void enterFullscreen() {
        isFullscreen = true;
        playerView.findViewById(R.id.exo_fullscreen_button).setBackgroundResource(R.drawable.ic_fullscreen_exit);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

        scrollView.setVisibility(View.GONE);
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) playerView.getLayoutParams();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        playerView.setLayoutParams(params);
    }

    private void exitFullscreen() {
        isFullscreen = false;
        playerView.findViewById(R.id.exo_fullscreen_button).setBackgroundResource(R.drawable.ic_fullscreen_enter);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.show(WindowInsetsCompat.Type.systemBars());
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

        scrollView.setVisibility(View.VISIBLE);
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) playerView.getLayoutParams();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.height = 0; // ratio
        playerView.setLayoutParams(params);
    }

    private void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
    }

    // --- FIREBASE LOGIC (HISTORY, STATS, LIKE) ---

    private void startListeningToUserStatus() {
        if (currentUser == null) return;
        userSettingsListener = firestore.collection("users").document(currentUser.getUid())
                .addSnapshotListener((snapshot, e) -> {
                    if (snapshot != null && snapshot.exists()) {
                        User user = snapshot.toObject(User.class);
                        if (user != null) isHistoryRecordingEnabled = !user.isHistoryPaused();
                    }
                });
    }

    private void saveWatchHistory(long position) {
        if (currentUser == null || !isHistoryRecordingEnabled || currentVideoId == null) return;

        Map<String, Object> data = new HashMap<>();
        data.put("userID", currentUser.getUid());
        data.put("videoID", currentVideoId);
        data.put("watchedAt", FieldValue.serverTimestamp());
        data.put("resumePosition", position);

        firestore.collection("watchHistory")
                .document(currentUser.getUid() + "_" + currentVideoId)
                .set(data, SetOptions.merge());
    }

    private void fetchAndListenToVideoStats(String vid) {
        if (videoStatRef != null && statListener != null) {
            videoStatRef.removeEventListener(statListener);
        }
        videoStatRef = FirebaseDatabase.getInstance().getReference("videostat").child(vid);

        statListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    btnLike.setText("0");
                    btnDislike.setText("0");
                    updateStatsUI(0L);
                    return;
                }
                long likes = snapshot.child("likes").getChildrenCount();
                long dislikes = snapshot.child("dislikes").getChildrenCount();
                Long views = snapshot.child("viewCount").getValue(Long.class);

                btnLike.setText(formatViewCount(likes));
                btnDislike.setText(formatViewCount(dislikes));
                updateStatsUI(views);

                if (currentUser != null) {
                    if (snapshot.child("likes").hasChild(currentUser.getUid())) currentUserVoteState = UserVoteState.LIKED;
                    else if (snapshot.child("dislikes").hasChild(currentUser.getUid())) currentUserVoteState = UserVoteState.DISLIKED;
                    else currentUserVoteState = UserVoteState.NONE;
                }
                updateLikeDislikeButtons();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        videoStatRef.addValueEventListener(statListener);
    }

    private void updateStatsUI(Long viewCount) {
        if (viewCount == null) viewCount = 0L;
        String stats = String.format("%s • %s views • %s",
                currentUploaderName,
                formatViewCount(viewCount),
                currentRelativeTime);
        tvVideoStats.setText(stats);
    }

    // Các hàm helper (Like, Dislike, Increment View) giữ nguyên như cũ
    private void onLikeClicked() {
        if (videoStatRef == null || currentUser == null) return;
        DatabaseReference likesRef = videoStatRef.child("likes").child(currentUser.getUid());
        DatabaseReference dislikesRef = videoStatRef.child("dislikes").child(currentUser.getUid());

        if (currentUserVoteState == UserVoteState.LIKED) likesRef.removeValue();
        else { dislikesRef.removeValue(); likesRef.setValue(true); }
    }

    private void onDislikeClicked() {
        if (videoStatRef == null || currentUser == null) return;
        DatabaseReference likesRef = videoStatRef.child("likes").child(currentUser.getUid());
        DatabaseReference dislikesRef = videoStatRef.child("dislikes").child(currentUser.getUid());

        if (currentUserVoteState == UserVoteState.DISLIKED) dislikesRef.removeValue();
        else { likesRef.removeValue(); dislikesRef.setValue(true); }
    }

    private void updateLikeDislikeButtons() {
        if (currentUserVoteState == UserVoteState.LIKED) {
            btnLike.setIconResource(R.drawable.ic_like_filled);
            btnDislike.setIconResource(R.drawable.ic_dislike);
        } else if (currentUserVoteState == UserVoteState.DISLIKED) {
            btnLike.setIconResource(R.drawable.ic_like);
            btnDislike.setIconResource(R.drawable.ic_dislike_filled);
        } else {
            btnLike.setIconResource(R.drawable.ic_like);
            btnDislike.setIconResource(R.drawable.ic_dislike);
        }
    }

    private void incrementViewCount() {
        if (videoStatRef == null) return;
        videoStatRef.child("viewCount").runTransaction(new Transaction.Handler() {
            @NonNull @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                Integer current = currentData.getValue(Integer.class);
                currentData.setValue(current == null ? 1 : current + 1);
                return Transaction.success(currentData);
            }
            @Override public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {}
        });
    }

    private String formatViewCount(long count) {
        if (count < 1000) return String.valueOf(count);
        if (count < 1000000) return String.format("%.1fK", count / 1000.0);
        return String.format("%.1fM", count / 1000000.0);
    }

    private String getRelativeTime(Timestamp timestamp) {
        if (timestamp == null) return "";
        return DateUtils.getRelativeTimeSpanString(timestamp.toDate().getTime(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString();
    }
}