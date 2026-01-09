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
import com.example.metube.model.Playlist;
import com.example.metube.model.Subscription;
import com.example.metube.model.Video;
import com.example.metube.ui.home.VideoAdapter;
import com.example.metube.ui.video.VideoActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.Timestamp;

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
                // TODO: Navigate to PlaylistDetailActivity
                Toast.makeText(CreatorProfileActivity.this, "Open playlist: " + playlist.getTitle(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onMoreClick(Playlist playlist, View view) {
                showPlaylistMenu(playlist, view);
            }
        });
        rvPlaylists.setAdapter(playlistAdapter);

        btnSub.setOnClickListener(v -> toggleSubscription());
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
                    long count = querySnapshot.size();
                    tvStats.setText(formatCount(count) + " subscribers • 0 videos");
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

                    // Update video count in stats
                    String[] parts = tvStats.getText().toString().split(" • ");
                    if (parts.length > 0) {
                        tvStats.setText(parts[0] + " • " + videos.size() + " videos");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("CreatorProfile", "Error loading videos", e);
                });
    }

    private void loadCreatorPlaylists() {
        boolean isOwnProfile = creatorId.equals(currentUserId);

        Log.d("CreatorProfile", "Loading playlists for creatorId: " + creatorId);
        Log.d("CreatorProfile", "Is own profile: " + isOwnProfile);

        Query query = db.collection("playlists")
                .whereEqualTo("ownerId", creatorId);

        // Nếu không phải profile của mình, chỉ lấy playlist PUBLIC
        if (!isOwnProfile) {
            query = query.whereEqualTo("visibility", "PUBLIC");
            Log.d("CreatorProfile", "Filtering for PUBLIC playlists only");
        } else {
            Log.d("CreatorProfile", "Loading all playlists (own profile)");
        }

        query.orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Log.d("CreatorProfile", "Query successful, documents: " + querySnapshot.size());

                    List<Playlist> playlists = querySnapshot.toObjects(Playlist.class);

                    for (Playlist playlist : playlists) {
                        loadPlaylistThumbnail(playlist);
                    }

                    if (playlists.isEmpty()) {
                        // Hiển thị thông báo khi không có playlist
                        rvPlaylists.setVisibility(View.GONE);
                        tvEmptyPlaylists.setVisibility(View.VISIBLE);

                        if (isOwnProfile) {
                            tvEmptyPlaylists.setText("You don't have any playlists yet");
                        } else {
                            tvEmptyPlaylists.setText("No public playlists available");
                        }
                        Log.d("CreatorProfile", "No playlists found");
                    } else {
                        // Hiển thị danh sách playlist
                        rvPlaylists.setVisibility(View.VISIBLE);
                        tvEmptyPlaylists.setVisibility(View.GONE);
                        playlistAdapter.setPlaylists(playlists);

                        Log.d("CreatorProfile", "Loaded " + playlists.size() + " playlists");
                        for (Playlist p : playlists) {
                            Log.d("CreatorProfile", "  - " + p.getTitle() + " (" + p.getVisibility() + ")");
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("CreatorProfile", "Error loading playlists", e);
                    Log.e("CreatorProfile", "Error message: " + e.getMessage());

                    rvPlaylists.setVisibility(View.GONE);
                    tvEmptyPlaylists.setVisibility(View.VISIBLE);
                    tvEmptyPlaylists.setText("Failed to load playlists: " + e.getMessage());

                    // Check if it's an index error
                    if (e.getMessage() != null && e.getMessage().contains("index")) {
                        Toast.makeText(this, "Missing Firestore index. Check console for link.", Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ✅ Load thumbnail từ video đầu tiên trong playlist
    private void loadPlaylistThumbnail(Playlist playlist) {
        if (playlist.getVideoIds() == null || playlist.getVideoIds().isEmpty()) {
            // Nếu không có video, dùng thumbnail mặc định
            playlist.setThumbnailURL(null);
            return;
        }

        // Lấy video đầu tiên
        String firstVideoId = playlist.getVideoIds().get(0);

        db.collection("videos").document(firstVideoId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String thumbnailUrl = doc.getString("thumbnailURL");
                        playlist.setThumbnailURL(thumbnailUrl);
                        // Refresh adapter để hiển thị thumbnail
                        playlistAdapter.notifyDataSetChanged();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("CreatorProfile", "Failed to load thumbnail for playlist: " + playlist.getTitle(), e);
                });
    }

    // ✅ Hiển thị popup menu cho playlist
    private void showPlaylistMenu(Playlist playlist, View anchorView) {
        androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(this, anchorView);
        popup.inflate(R.menu.playlist_options);

        // Nếu không phải playlist của mình, ẩn option "Edit"
        boolean isOwnPlaylist = creatorId.equals(currentUserId);
        if (!isOwnPlaylist) {
            popup.getMenu().findItem(R.id.menu_edit_playlist).setVisible(false);
        }

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();

            if (id == R.id.menu_play_playlist) {
                playPlaylist(playlist);
                return true;
            } else if (id == R.id.menu_edit_playlist) {
                editPlaylist(playlist);
                return true;
            } else if (id == R.id.menu_delete_playlist) {
                deletePlaylist(playlist);
                return true;
            }

            return false;
        });

        popup.show();
    }

    // ✅ Play playlist
    private void playPlaylist(Playlist playlist) {
        if (playlist.getVideoIds() == null || playlist.getVideoIds().isEmpty()) {
            Toast.makeText(this, "This playlist is empty", Toast.LENGTH_SHORT).show();
            return;
        }

        // Load tất cả videos trong playlist
        List<String> videoIds = playlist.getVideoIds();

        // TODO: Load videos và mở VideoActivity với queue
        Toast.makeText(this, "Playing playlist: " + playlist.getTitle(), Toast.LENGTH_SHORT).show();

        // Implementation: Load videos và chuyển sang VideoActivity
        // Bạn có thể implement chi tiết sau
    }

    // ✅ Edit playlist
    private void editPlaylist(Playlist playlist) {
        Intent intent = new Intent(this, com.example.metube.ui.playlist.EditPlaylistActivity.class);
        intent.putExtra("playlist_id", playlist.getPlaylistId());
        startActivity(intent);
    }

    // ✅ Delete playlist
    private void deletePlaylist(Playlist playlist) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete Playlist")
                .setMessage("Are you sure you want to delete \"" + playlist.getTitle() + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    db.collection("playlists").document(playlist.getPlaylistId())
                            .delete()
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "Playlist deleted", Toast.LENGTH_SHORT).show();
                                loadCreatorPlaylists(); // Refresh list
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Failed to delete playlist", Toast.LENGTH_SHORT).show();
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String formatCount(long count) {
        if (count < 1000) return String.valueOf(count);
        if (count < 1000000) return String.format("%.1fK", count / 1000.0);
        return String.format("%.1fM", count / 1000000.0);
    }
}