package com.example.metube.ui.video;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import androidx.appcompat.widget.PopupMenu;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import de.hdodenhof.circleimageview.CircleImageView;

import com.example.metube.model.Subscription;
import com.example.metube.model.User;
import com.example.metube.model.UserWatchStat;
import com.example.metube.ui.notifications.NotificationHelper;
import com.example.metube.ui.video.QueueAdapter;
import com.example.metube.utils.DownloadUtil;
import com.example.metube.utils.ShareUtil;
import com.example.metube.utils.VideoQueueManager;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.card.MaterialCardView;

import com.bumptech.glide.Glide;
import com.example.metube.R;
import com.example.metube.model.Video;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters;
import com.google.firebase.database.*;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.exoplayer2.Player;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class VideoActivity extends AppCompatActivity {

    private static final String TAG = "VideoActivity_Debug";

    private PlayerView playerView;
    private ExoPlayer player;
    private TextView tvTitle, tvUploader, tvDescription, tvQuality, tvSpeed;
    private MaterialButton btnLike, btnDislike;
    private String currentVideoId;
    private boolean hasViewCountBeenIncremented = false;
    private enum UserVoteState { NONE, LIKED, DISLIKED }
    private UserVoteState currentUserVoteState = UserVoteState.NONE;
    private boolean isFullscreen = false;
    private DefaultTrackSelector trackSelector;
    private ScrollView scrollView;

    private TextView tvVideoStats;
    private CircleImageView ivChannelAvatar;
    private Button btnSubscribe;
    private MaterialCardView cardDescription;
    private boolean isDescriptionExpanded = false;
    private String currentUploaderName = "";
    private String currentRelativeTime = "";
    private String currentUploaderID = "";
    private Video currentVideoObject;
    private boolean isSubscribed = false;

    private DatabaseReference videoStatRef;
    private ValueEventListener statListener;
    private FirebaseFirestore firestore;
    private FirebaseUser currentUser;
    private float currentSpeed = 1.0f;
    private int currentQualityIndex = 0;
    private long startPosition = 0;
    private boolean isHistoryRecordingEnabled = true;
    private com.google.firebase.firestore.ListenerRegistration userSettingsListener;
    private com.google.firebase.firestore.ListenerRegistration subscriptionListener;
    private List<String> qualities = Arrays.asList("480p", "720p", "1080p");
    private long sessionStartTime = 0;

    private LinearLayout bottomSheetQueue;
    private BottomSheetBehavior<LinearLayout> queueBottomSheetBehavior;
    private RecyclerView rvQueue;
    private QueueAdapter queueAdapter;
    private TextView tvQueueTitle, tvQueueCount, tvPlaylistOwner;
    private ImageButton btnCloseQueue, btnRepeatQueue, btnShuffleQueue;
    private LinearLayout miniPlayerNext, layoutEmptyQueue;
    private TextView tvNextVideoTitle, tvNextVideoInfo;
    private ImageButton btnCollapseQueue;
    private MaterialButton btnShare, btnDownload;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_video);

            firestore = FirebaseFirestore.getInstance();
            currentUser = FirebaseAuth.getInstance().getCurrentUser();

            currentVideoId = getIntent().getStringExtra("video_id");
            startPosition = getIntent().getLongExtra("resume_position", 0);

            if (currentVideoId == null && VideoQueueManager.getInstance().getQueue().isEmpty()) {
                Toast.makeText(this, "Video not found", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            initViews();
            setupDescriptionToggle();
            checkUserHistorySetting();
            initQueueBottomSheet();

            String localPath = getIntent().getStringExtra("local_video_path");
            if (localPath != null) {
                // Handle offline video
                return;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
            Toast.makeText(this, "Error loading video: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }
    private void initQueueBottomSheet() {
        bottomSheetQueue = findViewById(R.id.bottom_sheet_queue);
        queueBottomSheetBehavior = BottomSheetBehavior.from(bottomSheetQueue);
        queueBottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

        // Đặt peek height = 0 để ẩn hoàn toàn khi hidden
        queueBottomSheetBehavior.setPeekHeight(0);

        // Initialize views
        rvQueue = findViewById(R.id.rv_queue);
        tvQueueTitle = findViewById(R.id.tv_queue_title);
        tvQueueCount = findViewById(R.id.tv_queue_count);
        tvPlaylistOwner = findViewById(R.id.tv_playlist_owner);
        btnCloseQueue = findViewById(R.id.btn_close_queue);
        btnRepeatQueue = findViewById(R.id.btn_repeat_queue);
        btnShuffleQueue = findViewById(R.id.btn_shuffle_queue);
        miniPlayerNext = findViewById(R.id.mini_player_next);
        layoutEmptyQueue = findViewById(R.id.layout_empty_queue);
        tvNextVideoTitle = findViewById(R.id.tv_next_video_title);
        tvNextVideoInfo = findViewById(R.id.tv_next_video_info);
        btnCollapseQueue = findViewById(R.id.btn_collapse_queue);


        // Setup RecyclerView
        rvQueue.setLayoutManager(new LinearLayoutManager(this));

        queueAdapter = new QueueAdapter(new ArrayList<>(), new QueueAdapter.OnQueueItemClickListener() {
            @Override
            public void onItemClick(int position) {
                List<Video> fullQueue = VideoQueueManager.getInstance().getQueue();
                if (position >= 0 && position < fullQueue.size()) {
                    Video selectedVideo = fullQueue.get(position);

                    // CẬP NHẬT currentVideoId TRƯỚC KHI SEEK
                    currentVideoId = selectedVideo.getVideoID();

                    // Hủy listener cũ
                    if (videoStatRef != null && statListener != null) {
                        videoStatRef.removeEventListener(statListener);
                        videoStatRef = null;
                        statListener = null;
                    }

                    // Reset view count flag
                    hasViewCountBeenIncremented = false;

                    // Seek đến video được chọn
                    player.seekTo(position, 0);
                    VideoQueueManager.getInstance().setCurrentPosition(position);

                    // Cập nhật UI
                    updateUIForCurrentVideo(selectedVideo);
                    updateQueueUI();

                    // Đóng bottom sheet
                    queueBottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                }
            }

            @Override
            public void onMoreClick(int position, View view) {
                showQueueItemMenu(position, view);
            }

            @Override
            public void onItemMove(int fromPosition, int toPosition) {
                List<Video> queue = VideoQueueManager.getInstance().getQueue();
                if (fromPosition < queue.size() && toPosition < queue.size()) {
                    Video movedVideo = queue.remove(fromPosition);
                    queue.add(toPosition, movedVideo);
                    player.moveMediaItem(fromPosition, toPosition);

                    // Cập nhật current position nếu bị ảnh hưởng
                    int currentPos = VideoQueueManager.getInstance().getCurrentPosition();
                    if (fromPosition == currentPos) {
                        VideoQueueManager.getInstance().setCurrentPosition(toPosition);
                    } else if (fromPosition < currentPos && toPosition >= currentPos) {
                        VideoQueueManager.getInstance().setCurrentPosition(currentPos - 1);
                    } else if (fromPosition > currentPos && toPosition <= currentPos) {
                        VideoQueueManager.getInstance().setCurrentPosition(currentPos + 1);
                    }
                }
            }
        });

        rvQueue.setAdapter(queueAdapter);

        // Enable drag & drop for reordering
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                queueAdapter.onItemMove(viewHolder.getAdapterPosition(),
                        target.getAdapterPosition());
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}
        });
        itemTouchHelper.attachToRecyclerView(rvQueue);

        // Button click listeners
        MaterialButton btnQueue = findViewById(R.id.btn_queue);
        if (btnQueue != null) {
            btnQueue.setOnClickListener(v -> openQueueBottomSheet());
        }

        btnCloseQueue.setOnClickListener(v -> {
            queueBottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        });

        btnCollapseQueue.setOnClickListener(v -> {
            queueBottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        });

        btnShuffleQueue.setOnClickListener(v -> shuffleQueue());

        btnRepeatQueue.setOnClickListener(v -> {
            // Toggle repeat mode
            Toast.makeText(this, "Repeat toggled", Toast.LENGTH_SHORT).show();
        });

        // Listen to bottom sheet state changes
        queueBottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    miniPlayerNext.setVisibility(View.GONE);
                } else if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    updateMiniPlayer();
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {}
        });
    }
    private void openQueueBottomSheet() {
        updateQueueUI();
        queueBottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    private void updateQueueUI() {
        List<Video> fullQueue = VideoQueueManager.getInstance().getQueue();
        int currentPos = VideoQueueManager.getInstance().getCurrentPosition();

        if (fullQueue.isEmpty()) {
            layoutEmptyQueue.setVisibility(View.VISIBLE);
            rvQueue.setVisibility(View.GONE);
            tvQueueCount.setText("0/0");
            return;
        }

        layoutEmptyQueue.setVisibility(View.GONE);
        rvQueue.setVisibility(View.VISIBLE);

        // Update header info
        tvQueueCount.setText((currentPos + 1) + "/" + fullQueue.size());

        // Load playlist/channel owner if available
        if (currentPos < fullQueue.size()) {
            Video currentVideo = fullQueue.get(currentPos);
            loadChannelName(currentVideo.getUploaderID(), tvPlaylistOwner);
        }

        // Show all videos in queue with current one highlighted
        queueAdapter.updateQueue(new ArrayList<>(fullQueue), currentPos);
    }

    private void updateMiniPlayer() {
        List<Video> queue = VideoQueueManager.getInstance().getQueue();
        int currentPos = VideoQueueManager.getInstance().getCurrentPosition();

        // Show mini player if there's a next video
        if (currentPos + 1 < queue.size()) {
            Video nextVideo = queue.get(currentPos + 1);
            tvNextVideoTitle.setText(nextVideo.getTitle());
            tvNextVideoInfo.setText(tvQueueTitle.getText() + " • " + tvQueueCount.getText());
            miniPlayerNext.setVisibility(View.VISIBLE);
        } else {
            miniPlayerNext.setVisibility(View.GONE);
        }
    }
    private void shuffleQueue() {
        List<Video> queue = VideoQueueManager.getInstance().getQueue();
        int currentPos = VideoQueueManager.getInstance().getCurrentPosition();

        if (currentPos + 1 >= queue.size()) {
            Toast.makeText(this, "No videos to shuffle", Toast.LENGTH_SHORT).show();
            return;
        }

        // Shuffle only upcoming videos
        List<Video> upcomingVideos = new ArrayList<>(queue.subList(currentPos + 1, queue.size()));
        Collections.shuffle(upcomingVideos);

        // Replace in main queue
        for (int i = 0; i < upcomingVideos.size(); i++) {
            queue.set(currentPos + 1 + i, upcomingVideos.get(i));
        }

        // Rebuild player playlist
        rebuildPlayerPlaylist(queue, currentPos);

        updateQueueUI();
        Toast.makeText(this, "Queue shuffled", Toast.LENGTH_SHORT).show();
    }

    private void rebuildPlayerPlaylist(List<Video> queue, int currentPos) {
        long currentPosition = player.getCurrentPosition();

        player.clearMediaItems();
        for (Video v : queue) {
            MediaItem mediaItem = MediaItem.fromUri(Uri.parse(v.getVideoURL()))
                    .buildUpon()
                    .setTag(v)
                    .build();
            player.addMediaItem(mediaItem);
        }

        // Restore playback position
        player.seekTo(currentPos, currentPosition);
    }

    private void showQueueItemMenu(int adapterPosition, View view) {
        PopupMenu popup = new PopupMenu(this, view);
        popup.inflate(R.menu.queue_item);

        int currentQueuePos = VideoQueueManager.getInstance().getCurrentPosition();
        int actualPosition = currentQueuePos + adapterPosition;

        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.menu_remove_from_queue) {
                removeFromQueue(actualPosition);
                return true;
            } else if (item.getItemId() == R.id.menu_play_next) {
                moveToPlayNext(actualPosition);
                return true;
            }
            return false;
        });

        popup.show();
    }

    private void removeFromQueue(int position) {
        List<Video> queue = VideoQueueManager.getInstance().getQueue();
        int currentPos = VideoQueueManager.getInstance().getCurrentPosition();

        if (position == currentPos) {
            Toast.makeText(this, "Cannot remove currently playing video", Toast.LENGTH_SHORT).show();
            return;
        }

        queue.remove(position);
        player.removeMediaItem(position);

        // Adjust current position if needed
        if (position < currentPos) {
            VideoQueueManager.getInstance().setCurrentPosition(currentPos - 1);
        }

        updateQueueUI();
        Toast.makeText(this, "Removed from queue", Toast.LENGTH_SHORT).show();
    }

    private void moveToPlayNext(int fromPosition) {
        int currentPos = VideoQueueManager.getInstance().getCurrentPosition();
        int toPosition = currentPos + 1;

        if (fromPosition == toPosition || fromPosition == currentPos) {
            return;
        }

        List<Video> queue = VideoQueueManager.getInstance().getQueue();
        Video video = queue.remove(fromPosition);
        queue.add(toPosition, video);

        player.moveMediaItem(fromPosition, toPosition);

        updateQueueUI();
        Toast.makeText(this, "Will play next", Toast.LENGTH_SHORT).show();
    }
    private void loadChannelName(String uploaderID, TextView textView) {
        if (uploaderID == null || uploaderID.isEmpty()) return;

        firestore.collection("users").document(uploaderID).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String name = doc.getString("name");
                        if (name != null) {
                            textView.setText(name);
                        }
                    }
                });
    }

    private void checkUserHistorySetting() {
        if (currentUser == null) return;
        firestore.collection("users").document(currentUser.getUid())
                .get().addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        User user = snapshot.toObject(User.class);
                        isHistoryRecordingEnabled = !user.isHistoryPaused();
                    }
                });
    }

    private void initViews() {
        playerView = findViewById(R.id.player_view);
        tvTitle = findViewById(R.id.tv_video_title);
        tvUploader = findViewById(R.id.tv_video_uploader);
        tvDescription = findViewById(R.id.tv_video_description);
        btnLike = findViewById(R.id.btn_like);
        btnDislike = findViewById(R.id.btn_dislike);
        tvVideoStats = findViewById(R.id.tv_video_stats);
        ivChannelAvatar = findViewById(R.id.iv_channel_avatar);
        LinearLayout layoutChannelInfo = findViewById(R.id.layout_channel_info);
        btnSubscribe = findViewById(R.id.btn_subscribe);
        cardDescription = findViewById(R.id.card_description);
        scrollView = findViewById(R.id.scroll_view_content);
        ivChannelAvatar = findViewById(R.id.iv_channel_avatar);
        tvUploader = findViewById(R.id.tv_video_uploader);

        btnLike.setOnClickListener(v -> onLikeClicked());
        btnDislike.setOnClickListener(v -> onDislikeClicked());
        btnSubscribe.setOnClickListener(v -> onSubscribeClicked());
        btnShare = findViewById(R.id.btn_share);
        btnDownload = findViewById(R.id.btn_download);
        btnShare.setOnClickListener(v -> onShareClicked());
        btnDownload.setOnClickListener(v -> onDownloadClicked());
        View.OnClickListener openProfileListener = v -> {
            if (currentUploaderID != null && !currentUploaderID.isEmpty()) {
                Intent intent = new Intent(VideoActivity.this, com.example.metube.ui.contentcreator.CreatorProfileActivity.class);
                intent.putExtra("creator_id", currentUploaderID);
                startActivity(intent);
            } else {
                Toast.makeText(this, "Channel info is loading...", Toast.LENGTH_SHORT).show();
            }
        };
        layoutChannelInfo.setOnClickListener(v -> {
            if (currentUploaderID != null && !currentUploaderID.isEmpty()) {
                Intent intent = new Intent(VideoActivity.this, com.example.metube.ui.contentcreator.CreatorProfileActivity.class);
                intent.putExtra("creator_id", currentUploaderID);
                startActivity(intent);
            }
        });
        ivChannelAvatar.setOnClickListener(openProfileListener);
        tvUploader.setOnClickListener(openProfileListener);
    }
    private void onShareClicked() {
        if (currentVideoObject == null) {
            Toast.makeText(this, "Video information not available", Toast.LENGTH_SHORT).show();
            return;
        }

        // Tạo URL giả định cho video (bạn có thể thay bằng deep link thực tế)
        String videoUrl = "https://metube.com/watch?v=" + currentVideoId;

        ShareUtil.shareVideo(
                this,
                currentVideoObject.getTitle() != null ? currentVideoObject.getTitle() : "Check out this video",
                videoUrl
        );
    }

    private void onDownloadClicked() {
        if (currentVideoObject == null) {
            Toast.makeText(this, "Video information not available", Toast.LENGTH_SHORT).show();
            return;
        }

        String videoUrl = currentVideoObject.getVideoURL();
        String videoTitle = currentVideoObject.getTitle();

        if (videoUrl == null || videoUrl.isEmpty()) {
            Toast.makeText(this, "Video URL not available for download", Toast.LENGTH_SHORT).show();
            return;
        }

        // Kiểm tra quyền WRITE_EXTERNAL_STORAGE nếu cần (Android 10 trở xuống)
        // Với Android 11+ thì không cần vì dùng MediaStore

        DownloadUtil.downloadVideo(
                this,
                videoUrl,
                videoTitle != null ? videoTitle : "video_" + System.currentTimeMillis()
        );
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

    @Override
    protected void onStart() {
        super.onStart();
        initializePlayer();
        startListeningToUserStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null) {
            player.play();
        }
        sessionStartTime = System.currentTimeMillis();
    }

    @Override
    protected void onPause() {
        super.onPause();
        long sessionDuration = System.currentTimeMillis() - sessionStartTime;
        // Nếu xem > 5 giây thì mới tính là có xem để update thống kê
        if (sessionDuration > 5000 && currentVideoId != null) {
            // Để lấy được object Video đầy đủ (có topics), bạn nên lưu biến 'currentVideo' toàn cục
            // ở hàm updateUIForCurrentVideo.
            if (currentVideoObject != null) {
                updateUserStatistics(currentVideoObject, sessionDuration);
            } else {
                Log.e(TAG, "Cannot update stats: currentVideoObject is NULL"); // Thêm log này để kiểm tra
            }
        }

        long currentPosition = player.getCurrentPosition();
        long duration = player.getDuration();

        if (currentPosition > 1000 && duration > 0) {
            if ((duration - currentPosition) < 1000) {
                saveWatchHistory(0);
            } else {
                saveWatchHistory(currentPosition);
            }
        }
        if (player != null) {
            player.pause();
        }
    }

    private void saveWatchHistory(long position) {
        if (currentUser == null) return;
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        if (!isHistoryRecordingEnabled) {
            Log.d(TAG, "History recording is PAUSED by user.");
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("userID", userId);
        data.put("videoID", currentVideoId);
        data.put("watchedAt", FieldValue.serverTimestamp());
        data.put("resumePosition", position);

        FirebaseFirestore.getInstance()
                .collection("watchHistory")
                .document(userId + "_" + currentVideoId)
                .set(data, SetOptions.merge());
    }

    private void startListeningToUserStatus() {
        if (currentUser == null) return;

        userSettingsListener = firestore.collection("users").document(currentUser.getUid())
                .addSnapshotListener((documentSnapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Listen failed.", e);
                        return;
                    }

                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null) {
                            isHistoryRecordingEnabled = !user.isHistoryPaused();
                            Log.d(TAG, "History Status Updated: " + isHistoryRecordingEnabled);
                        }
                    }
                });
    }

    private void initializePlayer() {
        if (player == null) {
            trackSelector = new DefaultTrackSelector(this);
            player = new ExoPlayer.Builder(this).setTrackSelector(trackSelector).build();
            playerView.setPlayer(player);
            setupCustomPlayerControls();

            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int state) {
                    // Trường hợp 1: Video đầu tiên nạp xong và chuẩn bị phát
                    if (state == Player.STATE_READY && player.getPlayWhenReady() && !hasViewCountBeenIncremented) {
                        incrementViewCount();
                        hasViewCountBeenIncremented = true;
                    }
                }
                @Override
                public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                    // KIỂM TRA CHẶN CRASH: Chỉ chạy nếu MediaItem có gắn Video object
                    if (mediaItem != null && mediaItem.localConfiguration != null &&
                            mediaItem.localConfiguration.tag instanceof Video) {

                        Video nextVideo = (Video) mediaItem.localConfiguration.tag;
                        hasViewCountBeenIncremented = false;
                        currentVideoId = nextVideo.getVideoID();
                        updateUIForCurrentVideo(nextVideo);

                        incrementViewCount();
                        hasViewCountBeenIncremented = true;

                        // Cập nhật vị trí trong Manager
                        int newIndex = player.getCurrentMediaItemIndex();
                        VideoQueueManager.getInstance().setCurrentPosition(newIndex);
                        if (queueBottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
                            updateQueueUI();
                        }
                        updateMiniPlayer();
                    }
                }
            });
        }

        // --- LOGIC PHÂN ĐỊNH KHÔNG GÂY CRASH ---
        if (currentVideoId != null) {
            // Nếu click xem video cụ thể -> Luôn vào đây để nạp dữ liệu trước
            addVideoToQueueAndPlay(currentVideoId);
        } else {
            // Nếu quay lại từ background mà không có video mới, kiểm tra queue cũ
            List<Video> currentQueue = VideoQueueManager.getInstance().getQueue();
            if (currentQueue != null && !currentQueue.isEmpty()) {
                playFromQueue();
            } else {
                finish(); // Không có gì để phát thì thoát
            }
        }
    }
    private void playFromQueue() {
        List<Video> queue = VideoQueueManager.getInstance().getQueue();
        int startIndex = VideoQueueManager.getInstance().getCurrentPosition();

        // ✅ KIỂM TRA AN TOÀN
        if (queue == null || queue.isEmpty()) {
            Log.e(TAG, "playFromQueue: Queue is empty!");
            Toast.makeText(this, "No videos in queue", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (startIndex < 0 || startIndex >= queue.size()) {
            Log.w(TAG, "Invalid startIndex: " + startIndex + ", resetting to 0");
            startIndex = 0;
            VideoQueueManager.getInstance().setCurrentPosition(0);
        }

        player.clearMediaItems();

        for (Video v : queue) {
            if (v.getVideoURL() == null || v.getVideoURL().isEmpty()) {
                Log.w(TAG, "Skipping video with no URL: " + v.getVideoID());
                continue;
            }

            MediaItem mediaItem = MediaItem.fromUri(Uri.parse(v.getVideoURL()))
                    .buildUpon()
                    .setTag(v)  // ✅ GẮN TAG để listener lấy được
                    .build();
            player.addMediaItem(mediaItem);
        }

        // ✅ KIỂM TRA LẠI SAU KHI THÊM MEDIA ITEMS
        if (player.getMediaItemCount() == 0) {
            Toast.makeText(this, "No valid videos to play", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        player.seekTo(startIndex, startPosition);
        player.prepare();
        player.play();

        // Cập nhật UI
        Video currentVideo = queue.get(startIndex);
        updateUIForCurrentVideo(currentVideo);
        updateQueueUI();
    }
    // Hàm phát video lẻ (Logic cũ nhưng tách ra)
//    private void loadSingleVideoAndPlay(String videoId) {
//        firestore.collection("videos").document(videoId).get()
//                .addOnSuccessListener(snapshot -> {
//                    if (!snapshot.exists()) return;
//                    Video video = snapshot.toObject(Video.class);
//                    if (video == null) return;
//                    video.setVideoID(snapshot.getId());
//
//                    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(video.getVideoURL()))
//                            .buildUpon()
//                            .setTag(video)
//                            .build();
//
//                    player.setMediaItem(mediaItem);
//                    if (startPosition > 0) {
//                        player.seekTo(startPosition);
//                        Toast.makeText(this, "Resuming...", Toast.LENGTH_SHORT).show();
//                    }
//                    player.prepare();
//                    player.play();
//
//                    updateUIForCurrentVideo(video);
//                });
//    }

    private void addVideoToQueueAndPlay(String videoId) {
        // Hiển thị loading
        playerView.setVisibility(View.INVISIBLE);

        firestore.collection("videos").document(videoId).get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        Toast.makeText(this, "Video not found", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    Video video = snapshot.toObject(Video.class);
                    if (video == null || video.getVideoURL() == null) {
                        Toast.makeText(this, "Video source not available", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    video.setVideoID(snapshot.getId());

                    VideoQueueManager manager = VideoQueueManager.getInstance();

                    // ✅ LOGIC MỚI: Kiểm tra trùng lặp ĐÚNG CÁCH
                    int existPos = -1;
                    List<Video> currentQueue = manager.getQueue();

                    for (int i = 0; i < currentQueue.size(); i++) {
                        if (currentQueue.get(i).getVideoID().equals(videoId)) {
                            existPos = i;
                            break;
                        }
                    }

                    if (existPos == -1) {
                        // Video chưa có trong queue -> Thêm mới
                        manager.addVideo(video);
                        manager.setCurrentPosition(manager.getQueue().size() - 1);
                    } else {
                        // Video đã có trong queue -> Chỉ cần chuyển đến vị trí đó
                        manager.setCurrentPosition(existPos);
                    }

                    // Phát từ queue
                    playFromQueue();
                    playerView.setVisibility(View.VISIBLE);

                    // Reset để không bị lặp lại
                    currentVideoId = null;
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load video", e);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    // --- HÀM CẬP NHẬT GIAO DIỆN KHI ĐỔI VIDEO ---
    private void updateUIForCurrentVideo(Video video) {
        if (video == null) {
            Log.e(TAG, "updateUIForCurrentVideo: video is NULL!");
            return;
        }

        // Gỡ bỏ listener cũ
        if (videoStatRef != null && statListener != null) {
            videoStatRef.removeEventListener(statListener);
            videoStatRef = null;
            statListener = null;
        }
        if (subscriptionListener != null) {
            subscriptionListener.remove();
            subscriptionListener = null;
        }

        // Cập nhật thông tin cơ bản
        currentVideoId = video.getVideoID();
        currentVideoObject = video;
        currentUploaderID = video.getUploaderID();

        tvTitle.setText(video.getTitle() != null ? video.getTitle() : "Untitled");
        tvDescription.setText(video.getDescription() != null ? video.getDescription() : "");

        if (video.getCreatedAt() != null) {
            currentRelativeTime = getRelativeTime(video.getCreatedAt());
        }

        // ✅ GỌI NGAY ĐỂ TRÁNH CRASH KHI LOAD USER LÂU
        fetchAndListenToVideoStats(currentVideoId);

        // Load thông tin user (có thể lâu)
        if (currentUploaderID != null && !currentUploaderID.isEmpty()) {
            firestore.collection("users").document(currentUploaderID).get()
                    .addOnSuccessListener(doc -> {
                        String uploaderName = doc.exists() ? doc.getString("name") : "Unknown";
                        currentUploaderName = uploaderName;
                        tvUploader.setText(uploaderName);

                        String avatar = doc.getString("profileURL");
                        if (avatar != null) {
                            Glide.with(this).load(avatar).into(ivChannelAvatar);
                        }

                        listenToSubscriptionStatus();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to load user info", e);
                        currentUploaderName = "Unknown";
                        tvUploader.setText("Unknown");
                    });
        } else {
            currentUploaderName = "Unknown";
            tvUploader.setText("Unknown");
        }
    }

    private void setupCustomPlayerControls() {
        ImageButton settingsButton = playerView.findViewById(R.id.exo_settings_button);
        ImageButton volumeButton = playerView.findViewById(R.id.exo_volume_button);
        ImageButton fullscreenButton = playerView.findViewById(R.id.exo_fullscreen_button);

        settingsButton.setOnClickListener(view -> {
            PopupMenu popupMenu = new PopupMenu(this, view);
            popupMenu.getMenuInflater().inflate(R.menu.player_settings_menu, popupMenu.getMenu());
            popupMenu.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.menu_speed_0_5x) player.setPlaybackParameters(new PlaybackParameters(0.5f));
                else if (id == R.id.menu_speed_1x) player.setPlaybackParameters(new PlaybackParameters(1.0f));
                else if (id == R.id.menu_speed_1_5x) player.setPlaybackParameters(new PlaybackParameters(1.5f));
                else if (id == R.id.menu_speed_2x) player.setPlaybackParameters(new PlaybackParameters(2.0f));
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

        fullscreenButton.setOnClickListener(view -> {
            if (isFullscreen) {
                exitFullscreen();
            } else {
                enterFullscreen();
            }
        });

        volumeButton.setOnClickListener(view -> {
            if (player.getVolume() > 0) {
                player.setVolume(0f);
                volumeButton.setImageResource(R.drawable.ic_volume_off);
            } else {
                player.setVolume(1f);
                volumeButton.setImageResource(R.drawable.ic_volume_up);
            }
        });
    }

    private void enterFullscreen() {
        isFullscreen = true;
        ImageButton fullscreenButton = playerView.findViewById(R.id.exo_fullscreen_button);
        fullscreenButton.setImageResource(R.drawable.ic_fullscreen_exit);

        WindowInsetsControllerCompat windowInsetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
        windowInsetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

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

        WindowInsetsControllerCompat windowInsetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars());

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

        scrollView.setVisibility(View.VISIBLE);
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) playerView.getLayoutParams();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.height = 0;
        params.dimensionRatio = "16:9";
        playerView.setLayoutParams(params);
    }

    private void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
    }

    private void updateStatsUI(Long viewCount) {
        if (viewCount == null) viewCount = 0L;

        if (currentUploaderName != null && !currentUploaderName.isEmpty()) {
            String stats = String.format("%s • %s views • %s",
                    currentUploaderName,
                    formatViewCount(viewCount),
                    currentRelativeTime);
            tvVideoStats.setText(stats);
            Log.d(TAG, "Stats updated: " + stats);
        } else {
            Log.d(TAG, "updateStatsUI: uploaderName chưa sẵn sàng");
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
                    currentVideoObject = video;

                    tvTitle.setText(video.getTitle());
                    if (video.getDescription() != null) {
                        tvDescription.setText(video.getDescription());
                    } else {
                        tvDescription.setText("");
                    }
                    if (video.getCreatedAt() != null) {
                        currentRelativeTime = getRelativeTime(video.getCreatedAt());
                    }

                    currentUploaderID = video.getUploaderID();

                    firestore.collection("users").document(video.getUploaderID()).get()
                            .addOnSuccessListener(doc -> {
                                String uploaderName = doc.exists() ? doc.getString("name") : "Unknown Channel";
                                tvUploader.setText(uploaderName);
                                currentUploaderName = uploaderName;

                                Log.d(TAG, "Uploader loaded: " + currentUploaderName + " (value assigned)");
                                String avatarUrl = doc.getString("profileURL");
                                if (avatarUrl != null && !avatarUrl.isEmpty()) {
                                    Glide.with(this).load(avatarUrl).into(ivChannelAvatar);
                                }
                                Log.d(TAG, "Calling fetchAndListenToVideoStats...");
                                fetchAndListenToVideoStats(videoId);
                                listenToSubscriptionStatus();
                            })
                            .addOnFailureListener(e -> {
                                tvUploader.setText("Unknown Channel");
                                currentUploaderName = "Unknown Channel";
                                fetchAndListenToVideoStats(videoId);
                            });

                    MediaItem mediaItem = MediaItem.fromUri(Uri.parse(video.getVideoURL()));
                    player.setMediaItem(mediaItem);
                    if (startPosition > 0) {
                        player.seekTo(startPosition);
                        Toast.makeText(this, "Resuming from where you left off", Toast.LENGTH_SHORT).show();
                    }
                    player.prepare();
                    player.play();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load video: ", e);
                    Toast.makeText(this, "Error loading video", Toast.LENGTH_SHORT).show();
                });
    }

    private void fetchAndListenToVideoStats(String videoId) {
        videoStatRef = FirebaseDatabase.getInstance().getReference("videostat").child(videoId);

        statListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    btnLike.setText("0");
                    btnDislike.setText("0");
                    updateStatsUI(0L);
                    return;
                }

                long likeCount = snapshot.child("likes").getChildrenCount();
                long dislikeCount = snapshot.child("dislikes").getChildrenCount();

                btnLike.setText(String.valueOf(likeCount));
                btnDislike.setText(String.valueOf(dislikeCount));
                Long viewCount = snapshot.child("viewCount").getValue(Long.class);
                Log.d(TAG, "Calling updateStatsUI with viewCount: " + viewCount + ", uploaderName: " + currentUploaderName);
                updateStatsUI(viewCount);

                if (currentUser != null) {
                    if (snapshot.child("likes").hasChild(currentUser.getUid())) {
                        currentUserVoteState = UserVoteState.LIKED;
                    } else if (snapshot.child("dislikes").hasChild(currentUser.getUid())) {
                        currentUserVoteState = UserVoteState.DISLIKED;
                    } else {
                        currentUserVoteState = UserVoteState.NONE;
                    }
                }
                updateLikeDislikeButtons();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { }
        };
        videoStatRef.addValueEventListener(statListener);
    }

    // NEW: Listen to subscription status and count
    private void listenToSubscriptionStatus() {
        if (currentUser == null || currentUploaderID == null || currentUploaderID.isEmpty()) {
            btnSubscribe.setEnabled(false);
            return;
        }

        // Don't allow subscribing to yourself
        if (currentUser.getUid().equals(currentUploaderID)) {
            btnSubscribe.setVisibility(View.GONE);
            return;
        }

        String subscriptionDocId = currentUser.getUid() + "_" + currentUploaderID;

        subscriptionListener = firestore.collection("subscriptions")
                .document(subscriptionDocId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Subscription listener error: ", error);
                        return;
                    }

                    if (snapshot != null && snapshot.exists()) {
                        Subscription subscription = snapshot.toObject(Subscription.class);
                        if (subscription != null) {
                            isSubscribed = (subscription.getStatus() == Subscription.Status.SUBSCRIBED
                                    || subscription.getStatus() == Subscription.Status.MEMBERSHIP);
                        } else {
                            isSubscribed = false;
                        }
                    } else {
                        isSubscribed = false;
                    }

                    updateSubscribeButton();
                    updateSubscriberCount();
                });
    }

    // NEW: Update subscribe button appearance
    private void updateSubscribeButton() {
        if (isSubscribed) {
            btnSubscribe.setText("Subscribed");
            btnSubscribe.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(this, R.color.sub_btn_inactive_bg)));
            btnSubscribe.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.sub_btn_inactive_text));
        } else {
            btnSubscribe.setText("Subscribe");
            btnSubscribe.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(this, R.color.sub_btn_active_bg)));

            // Đổi màu chữ
            btnSubscribe.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.sub_btn_active_text));
        }
    }

    // NEW: Update subscriber count display
    private void updateSubscriberCount() {
        if (currentUploaderID == null || currentUploaderID.isEmpty()) return;

        firestore.collection("subscriptions")
                .whereEqualTo("uploaderID", currentUploaderID)
                .whereIn("status", Arrays.asList(
                        Subscription.Status.SUBSCRIBED.name(),
                        Subscription.Status.MEMBERSHIP.name()
                ))
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    long count = querySnapshot.size();
                    String currentText = btnSubscribe.getText().toString();

                    if (isSubscribed) {
                        btnSubscribe.setText("Subscribed (" + formatSubscriberCount(count) + ")");
                    } else {
                        btnSubscribe.setText("Subscribe (" + formatSubscriberCount(count) + ")");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get subscriber count: ", e);
                });
    }

    // NEW: Handle subscribe button click
    private void onSubscribeClicked() {
        if (currentUser == null) {
            Toast.makeText(this, "Please login to subscribe", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentUploaderID == null || currentUploaderID.isEmpty()) {
            Toast.makeText(this, "Unable to subscribe at this time", Toast.LENGTH_SHORT).show();
            return;
        }

        String subscriptionDocId = currentUser.getUid() + "_" + currentUploaderID;

        if (isSubscribed) {
            // Unsubscribe
            Map<String, Object> updateData = new HashMap<>();
            updateData.put("status", Subscription.Status.UNSUBSCRIBED.name());

            firestore.collection("subscriptions")
                    .document(subscriptionDocId)
                    .update(updateData)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Unsubscribed", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to unsubscribe: ", e);
                        Toast.makeText(this, "Failed to unsubscribe", Toast.LENGTH_SHORT).show();
                    });
        } else {
            // Subscribe
            Subscription subscription = new Subscription(
                    subscriptionDocId,
                    currentUploaderID,
                    currentUser.getUid(),
                    Timestamp.now(),
                    Subscription.Status.SUBSCRIBED
            );

            firestore.collection("subscriptions")
                    .document(subscriptionDocId)
                    .set(subscription)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Subscribed!", Toast.LENGTH_SHORT).show();
                        NotificationHelper.notifyOwnerAboutNewSubscriber(currentUploaderID, currentUser.getUid());
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to subscribe: ", e);
                        Toast.makeText(this, "Failed to subscribe", Toast.LENGTH_SHORT).show();
                    });
        }

    }

    private void onLikeClicked() {
        if (videoStatRef == null || currentUser == null) return;

        DatabaseReference myLikeRef = videoStatRef.child("likes").child(currentUser.getUid());
        DatabaseReference myDislikeRef = videoStatRef.child("dislikes").child(currentUser.getUid());

        if (currentUserVoteState == UserVoteState.LIKED) {
            myLikeRef.removeValue();
        } else {
            myDislikeRef.removeValue();
            myLikeRef.setValue(true);
        }
    }

    private void onDislikeClicked() {
        if (videoStatRef == null || currentUser == null) return;

        DatabaseReference myLikeRef = videoStatRef.child("likes").child(currentUser.getUid());
        DatabaseReference myDislikeRef = videoStatRef.child("dislikes").child(currentUser.getUid());

        if (currentUserVoteState == UserVoteState.DISLIKED) {
            myDislikeRef.removeValue();
        } else {
            myLikeRef.removeValue();
            myDislikeRef.setValue(true);
        }
    }

    private void updateLikeDislikeButtons() {
        if (currentUserVoteState == UserVoteState.LIKED) {
            btnLike.setChecked(true);
            btnLike.setIconResource(R.drawable.ic_like_filled);
        } else {
            btnLike.setChecked(false);
            btnLike.setIconResource(R.drawable.ic_like);
        }

        if (currentUserVoteState == UserVoteState.DISLIKED) {
            btnDislike.setIconResource(R.drawable.ic_dislike_filled);
        } else {
            btnDislike.setIconResource(R.drawable.ic_dislike);
        }
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

        // Lưu lại ID và Object vào biến cục bộ để đảm bảo không bị đổi khi chuyển bài
        final String targetVideoId = currentVideoId;
        final String targetUploaderId = currentUploaderID;
        final Video targetVideoObj = currentVideoObject;

        videoStatRef.child("viewCount")
                .runTransaction(new Transaction.Handler() {
                    @NonNull
                    @Override
                    public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                        Integer current = currentData.getValue(Integer.class);
                        if (current == null) current = 0;
                        currentData.setValue(current + 1);
                        return Transaction.success(currentData);
                    }

                    @Override
                    public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot snapshot) {
                        if (committed && snapshot != null) {
                            Long views = snapshot.getValue(Long.class);
                            if (views != null && targetVideoObj != null) {
                                // ✅ LOGIC MỚI: Kiểm tra các mốc quan trọng
                                if (shouldTriggerMilestone(views)) {
                                    NotificationHelper.notifyOwnerAboutViewMilestone(
                                            targetUploaderId,
                                            targetVideoId,
                                            targetVideoObj.getTitle(),
                                            views,
                                            targetVideoObj.getThumbnailURL()
                                    );
                                    Log.d(TAG, "✅ Milestone triggered: " + views + " views");
                                }
                            }
                        }
                    }
                });
    }
    private boolean shouldTriggerMilestone(long views) {
        // Các mốc quan trọng
        long[] milestones = {
                5, 10, 25, 50, 100,           // Mốc nhỏ
                250, 500, 1000,               // Mốc trung bình
                5000, 10000, 25000, 50000,    // Mốc lớn
                100000, 500000, 1000000       // Mốc viral
        };

        for (long milestone : milestones) {
            if (views == milestone) {
                return true;
            }
        }
        return false;
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

    @Override
    protected void onStop() {
        super.onStop();
        releasePlayer();
        if (userSettingsListener != null) {
            userSettingsListener.remove();
        }
        if (subscriptionListener != null) {
            subscriptionListener.remove();
        }
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
        if (subscriptionListener != null) {
            subscriptionListener.remove();
        }
    }

    private String formatViewCount(long count) {
        if (count < 1000) return String.valueOf(count);
        if (count < 1000000) return String.format("%.1fK", count / 1000.0);
        return String.format("%.1fM", count / 1000000.0);
    }

    private String formatSubscriberCount(long count) {
        if (count < 1000) return String.valueOf(count);
        if (count < 1000000) return String.format("%.1fK", count / 1000.0);
        return String.format("%.1fM", count / 1000000.0);
    }

    private String getRelativeTime(Timestamp timestamp) {
        if (timestamp == null) return "";

        long timeInMillis = timestamp.toDate().getTime();
        long now = System.currentTimeMillis();

        return DateUtils.getRelativeTimeSpanString(
                timeInMillis,
                now,
                DateUtils.MINUTE_IN_MILLIS
        ).toString();
    }
    private void updateUserStatistics(Video video, long watchDuration) {
        if (currentUser == null || watchDuration <= 0) return;

        String userId = currentUser.getUid();

        String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());


        // 1. Tìm xem User này đã có thống kê chưa
        firestore.collection("userWatchStats")
                .whereEqualTo("userID", userId)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        // --- TRƯỜNG HỢP A: ĐÃ CÓ DỮ LIỆU -> UPDATE ---
                        DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                        DocumentReference docRef = doc.getReference();

                        Map<String, Object> updates = new HashMap<>();

                        // Cộng dồn thời gian xem (Tính bằng mili-giây)
                        updates.put("totalWatchTime", FieldValue.increment(watchDuration));

                        // Thêm Video ID vào danh sách (arrayUnion tự động lọc trùng)
                        updates.put("videosWatched", FieldValue.arrayUnion(video.getVideoID()));

                        // Thêm Topics vào danh sách
                        if (video.getTopics() != null) {
                            for (String topic : video.getTopics()) {
                                // Lưu dạng: topicCounts.Gaming = increment(1)
                                // Lưu ý: Topic không được chứa ký tự đặc biệt như dấu chấm (.)
                                String cleanTopic = topic.trim().replaceAll("\\.", "");
                                updates.put("topicCounts." + cleanTopic, FieldValue.increment(1));
                            }
                        }
                        updates.put("dailyWatchTime." + todayDate, FieldValue.increment(watchDuration));

                        docRef.update(updates)
                                .addOnSuccessListener(aVoid -> Log.d(TAG, "Stats updated successfully"))
                                .addOnFailureListener(e -> Log.e(TAG, "Failed to update stats", e));

                    } else {
                        // --- TRƯỜNG HỢP B: CHƯA CÓ DỮ LIỆU -> TẠO MỚI ---
                        String newDocId = firestore.collection("userWatchStats").document().getId();

                        UserWatchStat newStat = new UserWatchStat();
                        newStat.setUserWatchStatID(newDocId);
                        newStat.setUserID(userId);
                        newStat.setTotalWatchTime(watchDuration);
                        newStat.setCreatedAt(Timestamp.now());

                        // Tạo Map ngày
                        Map<String, Long> dailyMap = new HashMap<>();
                        dailyMap.put(todayDate, watchDuration);
                        newStat.setDailyWatchTime(dailyMap);

                        // Tạo list video đã xem
                        List<String> videoList = new ArrayList<>();
                        videoList.add(video.getVideoID());
                        newStat.setVideosWatched(videoList);

                        // Tạo list topic đã xem
                        Map<String, Long> initialTopics = new HashMap<>();
                        if (video.getTopics() != null) {
                            for (String topic : video.getTopics()) {
                                String cleanTopic = topic.trim().replaceAll("\\.", "");
                                initialTopics.put(cleanTopic, 1L); // Lần đầu xem là 1 view
                            }
                        }
                        newStat.setTopicCounts(initialTopics);

                        firestore.collection("userWatchStats").document(newDocId).set(newStat)
                                .addOnSuccessListener(aVoid -> Log.d(TAG, "New stats created"))
                                .addOnFailureListener(e -> Log.e(TAG, "Failed to create stats", e));
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error checking user stats", e));
    }
}