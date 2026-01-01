package com.example.metube.ui.video;

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
import android.widget.ScrollView;
import android.widget.SeekBar;
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
import de.hdodenhof.circleimageview.CircleImageView;

import com.example.metube.model.Subscription;
import com.example.metube.model.User;
import com.google.android.material.card.MaterialCardView;

import com.bumptech.glide.Glide;
import com.example.metube.R;
import com.example.metube.model.Video;
import com.example.metube.model.VideoStat;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters;
import com.google.firebase.database.*;
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VideoActivity extends AppCompatActivity {

    private static final String TAG = "VideoActivity_Debug";

    private PlayerView playerView;
    private ExoPlayer player;
    private TextView tvTitle, tvUploader, tvDescription, tvQuality, tvSpeed;
    private MaterialButton btnLike, btnDislike;
    private String videoId;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);
        scrollView = findViewById(R.id.scroll_view_content);

        firestore = FirebaseFirestore.getInstance();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        hasViewCountBeenIncremented = false;
        videoStatRef = null;
        statListener = null;

        videoId = getIntent().getStringExtra("video_id");
        startPosition = getIntent().getLongExtra("resume_position", 0);
        Log.d("VIDEO_ID_CHECK", "Video ID nhận được là: " + videoId);
        if (videoId == null) {
            Toast.makeText(this, "Video not found.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isFullscreen) {
                    exitFullscreen();
                } else {
                    setEnabled(false);
                    VideoActivity.super.onBackPressed();
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);

        initViews();
        setupDescriptionToggle();
        checkUserHistorySetting();
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
        btnSubscribe = findViewById(R.id.btn_subscribe);
        cardDescription = findViewById(R.id.card_description);
        scrollView = findViewById(R.id.scroll_view_content);

        btnLike.setOnClickListener(v -> onLikeClicked());
        btnDislike.setOnClickListener(v -> onDislikeClicked());
        btnSubscribe.setOnClickListener(v -> onSubscribeClicked());
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
    }

    @Override
    protected void onPause() {
        super.onPause();
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
        data.put("videoID", videoId);
        data.put("watchedAt", FieldValue.serverTimestamp());
        data.put("resumePosition", position);

        FirebaseFirestore.getInstance()
                .collection("watchHistory")
                .document(userId + "_" + videoId)
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
            player = new ExoPlayer.Builder(this)
                    .setTrackSelector(trackSelector)
                    .build();

            playerView.setPlayer(player);
            setupCustomPlayerControls();
            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_READY && player.getPlayWhenReady() && !hasViewCountBeenIncremented) {
                        incrementViewCount();
                        hasViewCountBeenIncremented = true;
                    }
                }
            });
        }

        loadVideoDetails(videoId);
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
        if (videoStatRef != null && statListener != null) {
            Log.d(TAG, "Listener đã được đăng ký rồi, bỏ qua!");
            return;
        }
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
            btnSubscribe.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));
        } else {
            btnSubscribe.setText("Subscribe");
            btnSubscribe.setBackgroundColor(getResources().getColor(android.R.color.holo_red_dark));
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
}