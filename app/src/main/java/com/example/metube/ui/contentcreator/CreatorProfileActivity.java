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

public class CreatorProfileActivity extends AppCompatActivity {

    private String creatorId;
    private String currentUserId;
    private FirebaseFirestore db;
    private boolean isSubscribed = false;

    // UI
    private TextView tvName, tvHandle, tvStats;
    private Button btnSub;
    private RecyclerView rvVideos;
    private VideoAdapter videoAdapter;

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
        checkSubscriptionStatus(); // Kiểm tra nút Sub
    }

    private void initViews() {
        tvName = findViewById(R.id.tv_channel_name);
        tvHandle = findViewById(R.id.tv_channel_handle);
        tvStats = findViewById(R.id.tv_channel_stats);
        btnSub = findViewById(R.id.btn_subscribe);
        rvVideos = findViewById(R.id.rv_channel_videos);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        rvVideos.setLayoutManager(new LinearLayoutManager(this));
        videoAdapter = new VideoAdapter(this, new ArrayList<>(), new VideoAdapter.OnVideoClickListener() {
            @Override
            public void onVideoClick(Video video) {
                // Logic khi nhấn vào xem video
                if (video != null && video.getVideoID() != null) {
                    Intent intent = new Intent(CreatorProfileActivity.this, VideoActivity.class);
                    intent.putExtra("video_id", video.getVideoID());
                    startActivity(intent);
                }
            }

            @Override
            public void onAvatarClick(String uploaderId) {
                // Vì đang ở chính trang Profile của người này rồi,
                // bạn có thể để trống hoặc hiện một thông báo nhỏ.
                Log.d("CreatorProfile", "Already on this profile");
            }
        });
        rvVideos.setAdapter(videoAdapter);

        // Logic khi bấm nút Subscribe
        btnSub.setOnClickListener(v -> toggleSubscription());
    }

    private void checkSubscriptionStatus() {
        if (currentUserId == null || currentUserId.equals(creatorId)) {
            btnSub.setVisibility(View.GONE); // Ẩn nếu là chính mình hoặc chưa login
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
            // Hủy đăng ký
            db.collection("subscriptions").document(docId)
                    .update("status", "UNSUBSCRIBED")
                    .addOnSuccessListener(aVoid -> {
                        isSubscribed = false;
                        updateSubscribeButtonUI();
                        loadSubscriberCount(); // Cập nhật lại số lượng hiển thị
                    });
        } else {
            // Đăng ký mới
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
            btnSub.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#333333"))); // Màu xám tối
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
                if (avatar != null) Glide.with(this).load(avatar).into((android.widget.ImageView)findViewById(R.id.iv_channel_avatar));

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
                    // Sẽ cập nhật video count ở loadCreatorVideos
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

                    String[] parts = tvStats.getText().toString().split(" • ");
                    if (parts.length > 0) {
                        tvStats.setText(parts[0] + " • " + videos.size() + " videos");
                    }
                });
    }

    private String formatCount(long count) {
        if (count < 1000) return String.valueOf(count);
        if (count < 1000000) return String.format("%.1fK", count / 1000.0);
        return String.format("%.1fM", count / 1000000.0);
    }
}