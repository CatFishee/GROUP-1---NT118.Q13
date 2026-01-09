package com.example.metube.ui.contentcreator;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.metube.R;
import com.example.metube.model.ContentCreatorStat;
import com.example.metube.model.Playlist;
import com.example.metube.model.Subscription;
import com.example.metube.model.Video;
import com.example.metube.ui.home.VideoAdapter;
import com.example.metube.ui.video.VideoActivity;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class CreatorProfileActivity extends AppCompatActivity {

    private String creatorId;
    private String currentUserId;
    private FirebaseFirestore db;
    private boolean isSubscribed = false;

    // UI
    private CircleImageView ivChannelAvatar;
    private TextView tvName, tvHandle, tvStats;
    private TextView tvEmptyPlaylists;
    private Button btnSub;
    private RecyclerView rvVideos, rvPlaylists;
    private VideoAdapter videoAdapter;
    private PlaylistVerticalAdapter playlistAdapter;

    // State variables for Stats (to prevent overwriting during async calls)
    private long mSubscriberCount = 0;
    private long mTotalVideosCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_creator_profile);

        creatorId = getIntent().getStringExtra("creator_id");
        currentUserId = FirebaseAuth.getInstance().getUid();
        db = FirebaseFirestore.getInstance();

        if (creatorId == null) {
            finish();
            return;
        }

        initViews();
        loadCreatorInfo();
        loadCreatorVideos();
        loadCreatorPlaylists();
        checkSubscriptionStatus();
        loadCreatorStats(); // New method to get total videos from Stats collection
    }

    private void initViews() {
        ivChannelAvatar = findViewById(R.id.iv_channel_avatar);
        tvName = findViewById(R.id.tv_channel_name);
        tvHandle = findViewById(R.id.tv_channel_handle);
        tvStats = findViewById(R.id.tv_channel_stats);
        tvEmptyPlaylists = findViewById(R.id.tv_empty_playlists);
        btnSub = findViewById(R.id.btn_subscribe);
        rvVideos = findViewById(R.id.rv_channel_videos);
        rvPlaylists = findViewById(R.id.rv_channel_playlists);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // Setup Videos RecyclerView
        rvVideos.setLayoutManager(new LinearLayoutManager(this));
        videoAdapter = new VideoAdapter(this, new ArrayList<>(), new VideoAdapter.OnVideoClickListener() {
            @Override
            public void onVideoClick(Video video) {
                if (video != null && video.getVideoID() != null) {
                    Intent intent = new Intent(CreatorProfileActivity.this, VideoActivity.class);
                    intent.putExtra("video_id", video.getVideoID());
                    startActivity(intent);
                }
            }

            @Override
            public void onAvatarClick(String uploaderId) {
                Log.d("CreatorProfile", "Already on this profile");
            }
        });
        rvVideos.setAdapter(videoAdapter);

        // Setup Playlists RecyclerView
        rvPlaylists.setLayoutManager(new LinearLayoutManager(this));
        playlistAdapter = new PlaylistVerticalAdapter(this, new PlaylistVerticalAdapter.OnPlaylistClickListener() {
            @Override
            public void onPlaylistClick(Playlist playlist) {
                boolean isOwner = currentUserId != null && currentUserId.equals(creatorId);

                if (isOwner) {
                    // Owner -> Open Details to manage
                    Intent intent = new Intent(CreatorProfileActivity.this, com.example.metube.ui.playlist.PlaylistDetailActivity.class);
                    intent.putExtra("playlist_id", playlist.getPlaylistId());
                    startActivity(intent);
                } else {
                    // Visitor -> Play All directly
                    playPlaylistDirectly(playlist);
                }
            }
        });
        rvPlaylists.setAdapter(playlistAdapter);

        btnSub.setOnClickListener(v -> toggleSubscription());
    }

    // --- NEW: Centralized method to update the stats text line ---
    private void updateStatsUI() {
        String subText = formatCount(mSubscriberCount) + " subscribers";
        String vidText = mTotalVideosCount + " videos";
        tvStats.setText(subText + " • " + vidText);
    }

    // --- NEW: Load Stats from ContentCreatorStat collection ---
    private void loadCreatorStats() {
        /*
           NOTE: Ensure your Firestore Security Rules allow 'read' access to
           'contentCreatorStats' for public users if you want visitors to see this count.
         */
        db.collection("contentCreatorStats")
                .whereEqualTo("userID", creatorId)
                .orderBy("createdAt", Query.Direction.DESCENDING) // Get the latest stat entry
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        ContentCreatorStat stat = querySnapshot.getDocuments().get(0).toObject(ContentCreatorStat.class);
                        if (stat != null) {
                            mTotalVideosCount = stat.getTotalVideos();
                            updateStatsUI();
                        }
                    } else {
                        Log.d("CreatorProfile", "No stats found for user, defaulting to 0");
                        mTotalVideosCount = 0;
                        updateStatsUI();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("CreatorProfile", "Error loading stats", e);
                });
    }

    private void checkSubscriptionStatus() {
        if (currentUserId == null || currentUserId.equals(creatorId)) {
            btnSub.setVisibility(View.GONE);
            return;
        }

        String docId = currentUserId + "_" + creatorId;
        db.collection("subscriptions").document(docId).get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                String status = snapshot.getString("status");
                isSubscribed = "SUBSCRIBED".equals(status) || "MEMBERSHIP".equals(status);
            } else {
                isSubscribed = false;
            }
            updateSubscribeButtonUI();
        });
    }

    private void playPlaylistDirectly(Playlist playlist) {
        if (playlist.getVideoIds() == null || playlist.getVideoIds().isEmpty()) {
            Toast.makeText(this, "This playlist is empty", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Loading playlist...", Toast.LENGTH_SHORT).show();

        List<String> videoIds = playlist.getVideoIds();

        db.collection("videos")
                .whereIn(com.google.firebase.firestore.FieldPath.documentId(), videoIds)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) return;

                    List<Video> videosToPlay = new ArrayList<>();
                    java.util.Map<String, Video> videoMap = new java.util.HashMap<>();

                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot) {
                        Video v = doc.toObject(Video.class);
                        if (v != null) {
                            v.setVideoID(doc.getId());
                            videoMap.put(doc.getId(), v);
                        }
                    }

                    // Sort to match playlist order
                    for (String id : videoIds) {
                        if (videoMap.containsKey(id)) {
                            videosToPlay.add(videoMap.get(id));
                        }
                    }

                    if (!videosToPlay.isEmpty()) {
                        com.example.metube.utils.VideoQueueManager.getInstance().playPlaylist(videosToPlay);
                        Intent intent = new Intent(CreatorProfileActivity.this, VideoActivity.class);
                        intent.putExtra("video_id", videosToPlay.get(0).getVideoID());
                        startActivity(intent);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to play playlist", Toast.LENGTH_SHORT).show();
                    Log.e("CreatorProfile", "Play playlist error", e);
                });
    }

    private void toggleSubscription() {
        if (currentUserId == null) {
            Toast.makeText(this, "Please login to subscribe", Toast.LENGTH_SHORT).show();
            return;
        }

        String docId = currentUserId + "_" + creatorId;
        if (isSubscribed) {
            db.collection("subscriptions").document(docId)
                    .update("status", "UNSUBSCRIBED")
                    .addOnSuccessListener(aVoid -> {
                        isSubscribed = false;
                        updateSubscribeButtonUI();
                        loadSubscriberCount();
                    });
        } else {
            Subscription sub = new Subscription(docId, creatorId, currentUserId, Timestamp.now(), Subscription.Status.SUBSCRIBED);
            db.collection("subscriptions").document(docId).set(sub)
                    .addOnSuccessListener(aVoid -> {
                        isSubscribed = true;
                        updateSubscribeButtonUI();
                        loadSubscriberCount();
                    });
        }
    }

    private void updateSubscribeButtonUI() {
        if (isSubscribed) {
            btnSub.setText("Subscribed");
            btnSub.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#333333")));
            btnSub.setTextColor(Color.WHITE);
        } else {
            btnSub.setText("Subscribe");
            btnSub.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.app_main_color)));
            btnSub.setTextColor(Color.WHITE);
        }
    }

    private void loadCreatorInfo() {
        db.collection("users").document(creatorId).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                String name = doc.getString("name");
                tvName.setText(name);
                tvHandle.setText("@" + name.toLowerCase().replace(" ", ""));

                String avatar = doc.getString("profileURL");
                if (avatar != null && !avatar.isEmpty()) {
                    Glide.with(this)
                            .load(avatar)
                            .placeholder(R.drawable.ic_person)
                            .into(ivChannelAvatar);
                }

                loadSubscriberCount();
            }
        });
    }

    private void loadSubscriberCount() {
        db.collection("subscriptions")
                .whereEqualTo("uploaderID", creatorId)
                .whereIn("status", Arrays.asList("SUBSCRIBED", "MEMBERSHIP"))
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    mSubscriberCount = querySnapshot.size();
                    updateStatsUI(); // Update UI with cached video count and new sub count
                });
    }

    private void loadCreatorVideos() {
        db.collection("videos")
                .whereEqualTo("uploaderID", creatorId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Video> videos = querySnapshot.toObjects(Video.class);
                    videoAdapter.setVideos(videos);
                    // REMOVED: Logic that updated tvStats based on list size.
                    // We now rely on loadCreatorStats() for the count.
                })
                .addOnFailureListener(e -> {
                    Log.e("CreatorProfile", "Error loading videos", e);
                });
    }

    private void loadCreatorPlaylists() {
        boolean isOwnProfile = creatorId.equals(currentUserId);
        Query query = db.collection("playlists").whereEqualTo("ownerId", creatorId);

        if (!isOwnProfile) {
            query = query.whereIn("visibility", Arrays.asList("Public", "PUBLIC"));
        }

        query.orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Playlist> playlists = querySnapshot.toObjects(Playlist.class);

                    for (Playlist playlist : playlists) {
                        loadPlaylistThumbnail(playlist);
                    }

                    if (playlists.isEmpty()) {
                        rvPlaylists.setVisibility(View.GONE);
                        tvEmptyPlaylists.setVisibility(View.VISIBLE);
                        if (isOwnProfile) {
                            tvEmptyPlaylists.setText("You don't have any playlists yet");
                        } else {
                            tvEmptyPlaylists.setText("No public playlists available");
                        }
                    } else {
                        rvPlaylists.setVisibility(View.VISIBLE);
                        tvEmptyPlaylists.setVisibility(View.GONE);
                        playlistAdapter.setPlaylists(playlists);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("CreatorProfile", "Error loading playlists", e);
                    rvPlaylists.setVisibility(View.GONE);
                    tvEmptyPlaylists.setVisibility(View.VISIBLE);
                });
    }

    private void loadPlaylistThumbnail(Playlist playlist) {
        if (playlist.getVideoIds() == null || playlist.getVideoIds().isEmpty()) {
            playlist.setThumbnailURL(null);
            return;
        }

        String firstVideoId = playlist.getVideoIds().get(0);
        db.collection("videos").document(firstVideoId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String thumbnailUrl = doc.getString("thumbnailURL");
                        playlist.setThumbnailURL(thumbnailUrl);
                        playlistAdapter.notifyDataSetChanged();
                    }
                });
    }

    private String formatCount(long count) {
        if (count < 1000) return String.valueOf(count);
        if (count < 1000000) return String.format("%.1fK", count / 1000.0);
        return String.format("%.1fM", count / 1000000.0);
    }
}