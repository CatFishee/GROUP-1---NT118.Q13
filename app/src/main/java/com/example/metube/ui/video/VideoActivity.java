package com.example.metube.ui.video;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable; // Import Nullable
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.metube.R;
import com.example.metube.model.Video;
import com.example.metube.model.VideoStat;
import com.google.firebase.database.*;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.Player;

import java.util.Arrays;
import java.util.List;

public class VideoActivity extends AppCompatActivity {

    private static final String TAG = "VideoActivity_Debug";

    private PlayerView playerView;
    private ExoPlayer player;
    private TextView tvTitle, tvUploader, tvDescription, tvStats, tvQuality, tvSpeed;
    private ImageButton btnPlayPause, btnRewind, btnLike, btnDislike;
    private SeekBar volumeSeekBar;
    private String videoId;

    private DatabaseReference videoStatRef;
    private ValueEventListener statListener;
    private FirebaseFirestore firestore;
    private float currentSpeed = 1.0f;
    private int currentQualityIndex = 0;
    private List<String> qualities = Arrays.asList("480p", "720p", "1080p"); // you can extend with actual URLs

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);

        firestore = FirebaseFirestore.getInstance();

        // Get the video ID passed from HomepageActivity
        videoId = getIntent().getStringExtra("video_id");
        if (videoId == null) {
            Toast.makeText(this, "Video not found.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupPlayer();
        loadVideoDetails(videoId);
        listenToVideoStats(videoId);
    }

    private void initViews() {
        playerView = findViewById(R.id.player_view);
        tvTitle = findViewById(R.id.tv_video_title);
        tvUploader = findViewById(R.id.tv_video_uploader);
        tvDescription = findViewById(R.id.tv_video_description);
        tvStats = findViewById(R.id.tv_video_stats);
        btnPlayPause = findViewById(R.id.btn_play_pause);
        btnRewind = findViewById(R.id.btn_rewind);
        btnLike = findViewById(R.id.btn_like);
        btnDislike = findViewById(R.id.btn_dislike);
        tvQuality = findViewById(R.id.btn_quality);
        tvSpeed = findViewById(R.id.btn_speed);
        volumeSeekBar = findViewById(R.id.seekbar_volume);

        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        btnRewind.setOnClickListener(v -> rewindVideo());
        btnLike.setOnClickListener(v -> incrementLike());
        btnDislike.setOnClickListener(v -> incrementDislike());
        tvQuality.setOnClickListener(v -> changeQuality());
        tvSpeed.setOnClickListener(v -> changeSpeed());
        volumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (player != null) player.setVolume(progress / 100f);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void setupPlayer() {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY && player.getPlayWhenReady()) {
                    incrementViewCount();
                }
            }
        });
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
                    if (video == null) return;

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
                    if (video.getVideoURL() != null) {
                        MediaItem mediaItem = MediaItem.fromUri(Uri.parse(video.getVideoURL()));
                        player.setMediaItem(mediaItem);
                        player.prepare();
                        player.play();
                        btnPlayPause.setImageResource(R.drawable.ic_pause);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load video: ", e);
                    Toast.makeText(this, "Error loading video", Toast.LENGTH_SHORT).show();
                });
    }

    private void listenToVideoStats(String videoId) {
        videoStatRef = FirebaseDatabase.getInstance()
                .getReference("videostat")
                .child(videoId);

        statListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                VideoStat stat = snapshot.getValue(VideoStat.class);
                if (stat != null) {
                    tvStats.setText(String.format(
                            "%d views • %d likes • %d dislikes",
                            stat.getViewCount(), stat.getLikeCount(), stat.getDislikeCount()));
                } else {
                    tvStats.setText("0 views • 0 likes • 0 dislikes");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Failed to listen to stats: ", error.toException());
            }
        };

        videoStatRef.addValueEventListener(statListener);
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

    private void togglePlayPause() {
        if (player == null) return;
        if (player.isPlaying()) {
            player.pause();
            btnPlayPause.setImageResource(R.drawable.ic_pause);
        } else {
            player.play();
            btnPlayPause.setImageResource(R.drawable.ic_play);
        }
    }

    private void rewindVideo() {
        if (player != null) {
            long newPosition = Math.max(player.getCurrentPosition() - 5000, 0);
            player.seekTo(newPosition);
        }
    }

    private void changeQuality() {
        currentQualityIndex = (currentQualityIndex + 1) % qualities.size();
        tvQuality.setText(qualities.get(currentQualityIndex));
        Toast.makeText(this, "Switched to " + qualities.get(currentQualityIndex), Toast.LENGTH_SHORT).show();
        // (optional) reload video with actual quality-specific URL if you support multiple sources
    }

    private void changeSpeed() {
        currentSpeed = (currentSpeed == 1.0f) ? 1.5f : (currentSpeed == 1.5f) ? 2.0f : 1.0f;
        player.setPlaybackParameters(new PlaybackParameters(currentSpeed));
        tvSpeed.setText(currentSpeed + "x");
        Toast.makeText(this, "Speed: " + currentSpeed + "x", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) {
            player.pause();
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
    }
}