package com.example.metube.ui.video;

import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import androidx.appcompat.widget.PopupMenu;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable; // Import Nullable
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.bumptech.glide.Glide;
import com.example.metube.R;
import com.example.metube.model.Video;
import com.example.metube.model.VideoStat;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters;
import com.google.firebase.database.*;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.exoplayer2.Player;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Arrays;
import java.util.List;

public class VideoActivity extends AppCompatActivity {

    private static final String TAG = "VideoActivity_Debug";

    private PlayerView playerView;
    private ExoPlayer player;
    private TextView tvTitle, tvUploader, tvDescription, tvQuality, tvSpeed;
//    private ImageButton btnPlayPause, btnRewind;
    private MaterialButton btnLike, btnDislike;
//    private SeekBar volumeSeekBar;
    private String videoId;
    private boolean hasViewCountBeenIncremented = false;
    private enum UserVoteState { NONE, LIKED, DISLIKED }
    private UserVoteState currentUserVoteState = UserVoteState.NONE;
    private boolean isFullscreen = false;
    private DefaultTrackSelector trackSelector;
    private ScrollView scrollView;

    private DatabaseReference videoStatRef;
    private ValueEventListener statListener;
    private FirebaseFirestore firestore;
    private FirebaseUser currentUser;
    private float currentSpeed = 1.0f;
    private int currentQualityIndex = 0;
    private List<String> qualities = Arrays.asList("480p", "720p", "1080p"); // you can extend with actual URLs

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);
        scrollView = findViewById(R.id.scroll_view_content);

        firestore = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();


        // Get the video ID passed from HomepageActivity
        videoId = getIntent().getStringExtra("video_id");
        if (videoId == null) {
            Toast.makeText(this, "Video not found.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        OnBackPressedCallback callback = new OnBackPressedCallback(true /* enabled by default */) {
            @Override
            public void handleOnBackPressed() {
                if (isFullscreen) {
                    exitFullscreen();
                } else {
                    // Nếu không ở chế độ toàn màn hình, hãy vô hiệu hóa callback này
                    // và gọi lại hành động back mặc định.
                    setEnabled(false);
                    VideoActivity.super.onBackPressed();
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);

        initViews();
//        setupPlayer();
//        loadVideoDetails(videoId);
//        fetchAndListenToVideoStats(videoId);
    }

    private void initViews() {
        playerView = findViewById(R.id.player_view);
        tvTitle = findViewById(R.id.tv_video_title);
        tvUploader = findViewById(R.id.tv_video_uploader);
        tvDescription = findViewById(R.id.tv_video_description);
//        tvStats = findViewById(R.id.tv_video_stats);
//        btnPlayPause = findViewById(R.id.btn_play_pause);
//        btnRewind = findViewById(R.id.btn_rewind);
        btnLike = findViewById(R.id.btn_like);
        btnDislike = findViewById(R.id.btn_dislike);
//        tvQuality = findViewById(R.id.btn_quality);
//        tvSpeed = findViewById(R.id.btn_speed);
//        volumeSeekBar = findViewById(R.id.seekbar_volume);
//
//        btnPlayPause.setOnClickListener(v -> togglePlayPause());
//        btnRewind.setOnClickListener(v -> rewindVideo());
        btnLike.setOnClickListener(v -> onLikeClicked());
        btnDislike.setOnClickListener(v -> onDislikeClicked());
        scrollView = findViewById(R.id.scroll_view_content);
//        tvQuality.setOnClickListener(v -> changeQuality());
//        tvSpeed.setOnClickListener(v -> changeSpeed());
//        volumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
//            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
//                if (player != null) player.setVolume(progress / 100f);
//            }
//            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
//            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
//        });
    }

//    private void setupPlayer() {
//        player = new ExoPlayer.Builder(this).build();
//        playerView.setPlayer(player);
//        player.addListener(new Player.Listener() {
//            @Override
//            public void onPlaybackStateChanged(int state) {
//                // Lượt xem sẽ chỉ được tăng MỘT LẦN DUY NHẤT khi các điều kiện này được đáp ứng LẦN ĐẦU TIÊN
//                if (state == Player.STATE_READY && player.getPlayWhenReady() && !hasViewCountBeenIncremented) {
//                    incrementViewCount();
//                    hasViewCountBeenIncremented = true; // "HẠ CỜ" XUỐNG ĐỂ NGĂN VIỆC TĂNG LƯỢT XEM LẦN NỮA
//                }
//            }
//
//            // (Tùy chọn nâng cao) Xử lý đổi icon play/pause dựa trên trạng thái thật
//            @Override
//            public void onIsPlayingChanged(boolean isPlaying) {
//                if (isPlaying) {
//                    btnPlayPause.setImageResource(R.drawable.ic_pause);
//                } else {
//                    btnPlayPause.setImageResource(R.drawable.ic_play);
//                }
//            }
//        });
//    }
    @Override
    protected void onStart() {
        super.onStart();
        // Khởi tạo player khi Activity bắt đầu được hiển thị
        initializePlayer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Tiếp tục phát nếu player đã được khởi tạo
        if (player != null) {
            player.play();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Dừng và giải phóng player khi Activity không còn được nhìn thấy
        // Điều này rất quan trọng để trả lại tài nguyên âm thanh cho hệ thống
//        releasePlayer();
        if (player != null) {
            player.pause(); // Chỉ pause ở đây
        }
    }
    // --- HÀM KHỞI TẠO PLAYER MỚI ---
    private void initializePlayer() {
        if (player == null) {
//            player = new ExoPlayer.Builder(this).build();
//            playerView.setPlayer(player);
//            player.addListener(new Player.Listener() {
//                @Override
//                public void onPlaybackStateChanged(int state) {
//                    if (state == Player.STATE_READY && player.getPlayWhenReady() && !hasViewCountBeenIncremented) {
//                        incrementViewCount();
//                        hasViewCountBeenIncremented = true;
//                    }
//                }
//                @Override
//                public void onIsPlayingChanged(boolean isPlaying) {
//                    btnPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
//                }
//            });
            trackSelector = new DefaultTrackSelector(this);
            player = new ExoPlayer.Builder(this)
                    .setTrackSelector(trackSelector)
                    .build();

            playerView.setPlayer(player);
            setupCustomPlayerControls();
        }

        // Nếu đã có URL, bắt đầu phát video
//        if (videoUrlToPlay != null) {
//            MediaItem mediaItem = MediaItem.fromUri(Uri.parse(videoUrlToPlay));
//            player.setMediaItem(mediaItem);
//            player.prepare();
//            player.setPlayWhenReady(true);
//        }
        loadVideoDetails(videoId);
        fetchAndListenToVideoStats(videoId);
    }
    private void setupCustomPlayerControls() {
        // Rất quan trọng: Tìm các view bên trong playerView, không phải activity
        ImageButton settingsButton = playerView.findViewById(R.id.exo_settings_button);
        ImageButton volumeButton = playerView.findViewById(R.id.exo_volume_button);
        ImageButton fullscreenButton = playerView.findViewById(R.id.exo_fullscreen_button);

        // 1. Xử lý nút Cài đặt (Tốc độ, Chất lượng)
        settingsButton.setOnClickListener(view -> {
            PopupMenu popupMenu = new PopupMenu(this, view);
            popupMenu.getMenuInflater().inflate(R.menu.player_settings_menu, popupMenu.getMenu());
            popupMenu.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                // --- LOGIC TỐC ĐỘ ---
                if (id == R.id.menu_speed_0_5x) player.setPlaybackParameters(new PlaybackParameters(0.5f));
                else if (id == R.id.menu_speed_1x) player.setPlaybackParameters(new PlaybackParameters(1.0f));
                else if (id == R.id.menu_speed_1_5x) player.setPlaybackParameters(new PlaybackParameters(1.5f));
                else if (id == R.id.menu_speed_2x) player.setPlaybackParameters(new PlaybackParameters(2.0f));

                    // --- LOGIC CHẤT LƯỢNG ---
                else if (id == R.id.menu_quality_auto) {
                    TrackSelectionParameters params = trackSelector.getParameters().buildUpon().clearVideoSizeConstraints().build();
                    trackSelector.setParameters(params);
                } else if (id == R.id.menu_quality_1080p) {
                    TrackSelectionParameters params = trackSelector.getParameters().buildUpon().setMaxVideoSize(1920, 1080).build();
                    trackSelector.setParameters(params);
                } else if (id == R.id.menu_quality_720p) {
                    TrackSelectionParameters params = trackSelector.getParameters().buildUpon().setMaxVideoSize(1280, 720).build();
                    trackSelector.setParameters(params);
                } else if (id == R.id.menu_quality_480p) {
                    TrackSelectionParameters params = trackSelector.getParameters().buildUpon().setMaxVideoSize(854, 480).build();
                    trackSelector.setParameters(params);
                }

                return true;
            });
            popupMenu.show();
        });

        // Xử lý nút Toàn màn hình
        fullscreenButton.setOnClickListener(view -> {
            if (isFullscreen) {
                exitFullscreen();
            } else {
                enterFullscreen();
            }
        });

        // 2. Xử lý nút Âm lượng
        volumeButton.setOnClickListener(view -> {
            if (player.getVolume() > 0) {
                player.setVolume(0f);
                volumeButton.setImageResource(R.drawable.ic_volume_off);
            } else {
                player.setVolume(1f);
                volumeButton.setImageResource(R.drawable.ic_volume_up);
            }
        });

//        // 3. Xử lý nút Toàn màn hình
//        fullscreenButton.setOnClickListener(view -> {
//            // TODO: Triển khai logic vào/thoát chế độ toàn màn hình
//            // Ví dụ: thay đổi orientation, ẩn status bar...
//            Toast.makeText(this, "Chức năng toàn màn hình chưa được triển khai", Toast.LENGTH_SHORT).show();
//        });
    }
    private void enterFullscreen() {
        isFullscreen = true;
        ImageButton fullscreenButton = playerView.findViewById(R.id.exo_fullscreen_button);
        fullscreenButton.setImageResource(R.drawable.ic_fullscreen_exit); // Cần icon thoát fullscreen

        // 1. Ẩn thanh trạng thái và thanh điều hướng
        WindowInsetsControllerCompat windowInsetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
        windowInsetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        // 2. Xoay màn hình sang ngang
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

        // 3. Ẩn các view khác và cho PlayerView chiếm toàn bộ màn hình
        scrollView.setVisibility(View.GONE);
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) playerView.getLayoutParams();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        playerView.setLayoutParams(params);
    }

    private void exitFullscreen() {
        isFullscreen = false;
        ImageButton fullscreenButton = playerView.findViewById(R.id.exo_fullscreen_button);
        fullscreenButton.setImageResource(R.drawable.ic_fullscreen_enter);

        // 1. Hiện lại thanh trạng thái và thanh điều hướng
        WindowInsetsControllerCompat windowInsetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars());

        // 2. Xoay màn hình về lại bình thường
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

        // 3. Hiện lại các view khác và trả PlayerView về kích thước cũ
        scrollView.setVisibility(View.VISIBLE);
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) playerView.getLayoutParams();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.height = 0; // Để constraint ratio hoạt động lại
        params.dimensionRatio = "16:9";
        playerView.setLayoutParams(params);
    }
//    @Override
//    public void onBackPressed() {
//        if (isFullscreen) {
//            exitFullscreen();
//        } else {
//            super.onBackPressed();
//        }
//    }

    // --- HÀM GIẢI PHÓNG PLAYER MỚI ---
    private void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
    }

    private void loadVideoDetails(String videoId) {
        firestore.collection("videos").document(videoId).get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        Toast.makeText(this, "Video not found", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    Video video = snapshot.toObject(Video.class);
                    if (video == null || video.getVideoURL() == null || video.getVideoURL().isEmpty()) {
                        Toast.makeText(this, "Video source not available.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    tvTitle.setText(video.getTitle());
                    tvDescription.setText(video.getDescription());

                    // Load uploader name
                    firestore.collection("users").document(video.getUploaderID()).get()
                            .addOnSuccessListener(doc -> {
                                String uploaderName = doc.exists()
                                        ? doc.getString("channelName")
                                        : "Unknown Channel";
                                tvUploader.setText(uploaderName);
                            })
                            .addOnFailureListener(e -> tvUploader.setText("Unknown Channel"));

                    // Load video
//                    if (video != null && video.getVideoURL() != null && !video.getVideoURL().isEmpty()) {
////                        MediaItem mediaItem = MediaItem.fromUri(Uri.parse(video.getVideoURL()));
//                        videoUrlToPlay = video.getVideoURL();
////                        player.setMediaItem(mediaItem);
////                        player.prepare();
////                        player.setPlayWhenReady(true); // Yêu cầu tự động phát khi sẵn sàng
//                        if (player != null) {
//                            MediaItem mediaItem = MediaItem.fromUri(Uri.parse(videoUrlToPlay));
//                            player.setMediaItem(mediaItem);
//                            player.prepare();
//                            player.setPlayWhenReady(true);
//                        }
//                    }
                    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(video.getVideoURL()));
                    player.setMediaItem(mediaItem);
                    player.prepare();
                    player.play(); // Bắt đầu phát
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load video: ", e);
                    Toast.makeText(this, "Error loading video", Toast.LENGTH_SHORT).show();
                });
    }

//    private void listenToVideoStats(String videoId) {
//        videoStatRef = FirebaseDatabase.getInstance()
//                .getReference("videostat")
//                .child(videoId);
//
//        statListener = new ValueEventListener() {
//            @Override
//            public void onDataChange(@NonNull DataSnapshot snapshot) {
//                VideoStat stat = snapshot.getValue(VideoStat.class);
//                if (stat != null) {
//                    tvStats.setText(String.format(
//                            "%d views • %d likes • %d dislikes",
//                            stat.getViewCount(), stat.getLikeCount(), stat.getDislikeCount()));
//                } else {
//                    tvStats.setText("0 views • 0 likes • 0 dislikes");
//                }
//            }
//
//            @Override
//            public void onCancelled(@NonNull DatabaseError error) {
//                Log.w(TAG, "Failed to listen to stats: ", error.toException());
//            }
//        };
//
//        videoStatRef.addValueEventListener(statListener);
//    }
private void fetchAndListenToVideoStats(String videoId) {
    videoStatRef = FirebaseDatabase.getInstance().getReference("videostat").child(videoId);

    statListener = new ValueEventListener() {
        @Override
        public void onDataChange(@NonNull DataSnapshot snapshot) {
            if (!snapshot.exists()) return;

            // Cập nhật lại số liệu
            long likeCount = snapshot.child("likes").getChildrenCount();
            long dislikeCount = snapshot.child("dislikes").getChildrenCount();
            Long viewCount = snapshot.child("viewCount").getValue(Long.class);
            if (viewCount == null) viewCount = 0L;

//            tvStats.setText(String.format("%d views • %d likes", viewCount, likeCount));
            btnLike.setText(String.valueOf(likeCount));

            // KIỂM TRA TRẠNG THÁI VOTE CỦA NGƯỜI DÙNG HIỆN TẠI
            if (currentUser != null) {
                if (snapshot.child("likes").hasChild(currentUser.getUid())) {
                    currentUserVoteState = UserVoteState.LIKED;
                } else if (snapshot.child("dislikes").hasChild(currentUser.getUid())) {
                    currentUserVoteState = UserVoteState.DISLIKED;
                } else {
                    currentUserVoteState = UserVoteState.NONE;
                }
            }
            // CẬP NHẬT GIAO DIỆN NÚT BẤM SAU KHI LẤY DỮ LIỆU
            updateLikeDislikeButtons();
        }
        @Override public void onCancelled(@NonNull DatabaseError error) { /* ... */ }
    };
    videoStatRef.addValueEventListener(statListener);
}
    private void onLikeClicked() {
        if (videoStatRef == null || currentUser == null) return;

        DatabaseReference myLikeRef = videoStatRef.child("likes").child(currentUser.getUid());
        DatabaseReference myDislikeRef = videoStatRef.child("dislikes").child(currentUser.getUid());

        if (currentUserVoteState == UserVoteState.LIKED) {
            // Hủy Like
            myLikeRef.removeValue();
        } else {
            // Like (và đồng thời hủy Dislike nếu có)
            myDislikeRef.removeValue();
            myLikeRef.setValue(true);
        }
    }
    private void onDislikeClicked() {
        if (videoStatRef == null || currentUser == null) return;

        DatabaseReference myLikeRef = videoStatRef.child("likes").child(currentUser.getUid());
        DatabaseReference myDislikeRef = videoStatRef.child("dislikes").child(currentUser.getUid());

        if (currentUserVoteState == UserVoteState.DISLIKED) {
            // Hủy Dislike
            myDislikeRef.removeValue();
        } else {
            // Dislike (và đồng thời hủy Like nếu có)
            myLikeRef.removeValue();
            myDislikeRef.setValue(true);
        }
    }
    private void updateLikeDislikeButtons() {
        // Cập nhật nút LIKE
//        btnLike.setImageResource(
//                currentUserVoteState == UserVoteState.LIKED ? R.drawable.ic_like_filled : R.drawable.ic_like
//        );
//        // Cập nhật nút DISLIKE
//        btnDislike.setImageResource(
//                currentUserVoteState == UserVoteState.DISLIKED ? R.drawable.ic_dislike_filled : R.drawable.ic_dislike
//        );
        btnLike.setIconResource(
                currentUserVoteState == UserVoteState.LIKED ? R.drawable.ic_like_filled : R.drawable.ic_like
        );
        btnDislike.setIconResource(
                currentUserVoteState == UserVoteState.DISLIKED ? R.drawable.ic_dislike_filled : R.drawable.ic_dislike
        );
    }
    private void updateCountTransaction(DatabaseReference ref, int amount) {
        ref.runTransaction(new Transaction.Handler() {
            @NonNull @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                Integer current = currentData.getValue(Integer.class);
                if (current == null) {
                    currentData.setValue(amount > 0 ? amount : 0);
                } else {
                    currentData.setValue(Math.max(0, current + amount));
                }
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                if (!committed) {
                    Log.e(TAG, "Transaction failed: ", error.toException());
                }
            }
        });
    }

    private void incrementViewCount() {
        if (videoStatRef == null) return;
        videoStatRef.child("viewCount")
                .runTransaction(new Transaction.Handler() {
                    @NonNull
                    @Override
                    public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                        Integer current = currentData.getValue(Integer.class);
                        currentData.setValue(current == null ? 1 : current + 1);
                        return Transaction.success(currentData);
                    }
                    @Override
                    public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                        // Corrected onComplete method signature
                    }
                });
    }

    private void incrementLike() {
        if (videoStatRef == null) return;
        videoStatRef.child("likeCount")
                .setValue(ServerValue.increment(1));
    }

    private void incrementDislike() {
        if (videoStatRef == null) return;
        videoStatRef.child("dislikeCount")
                .setValue(ServerValue.increment(1));
    }

//    private void togglePlayPause() {
//        if (player == null) return;
////        if (player.isPlaying()) {
////            player.pause();
////            btnPlayPause.setImageResource(R.drawable.ic_pause);
////        } else {
////            player.play();
////            btnPlayPause.setImageResource(R.drawable.ic_play);
////        }
//        player.setPlayWhenReady(!player.getPlayWhenReady());
//    }

//    private void rewindVideo() {
//        if (player != null) {
//            long newPosition = Math.max(player.getCurrentPosition() - 5000, 0);
//            player.seekTo(newPosition);
//        }
//    }

//    private void changeQuality() {
//        currentQualityIndex = (currentQualityIndex + 1) % qualities.size();
//        tvQuality.setText(qualities.get(currentQualityIndex));
//        Toast.makeText(this, "Switched to " + qualities.get(currentQualityIndex), Toast.LENGTH_SHORT).show();
//        // (optional) reload video with actual quality-specific URL if you support multiple sources
//    }
//
//    private void changeSpeed() {
//        currentSpeed = (currentSpeed == 1.0f) ? 1.5f : (currentSpeed == 1.5f) ? 2.0f : 1.0f;
//        player.setPlaybackParameters(new PlaybackParameters(currentSpeed));
//        tvSpeed.setText(currentSpeed + "x");
//        Toast.makeText(this, "Speed: " + currentSpeed + "x", Toast.LENGTH_SHORT).show();
//    }

    @Override
    protected void onStop() {
        super.onStop();
        releasePlayer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
        }
        if (videoStatRef != null && statListener != null) {
            videoStatRef.removeEventListener(statListener);
        }
    }
}