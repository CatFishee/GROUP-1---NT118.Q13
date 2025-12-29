package com.example.metube.ui.playlist;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.example.metube.R;
import com.example.metube.model.Playlist;
import com.example.metube.model.Video;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class PlaylistDetailActivity extends AppCompatActivity {

    private String playlistId;
    private FirebaseFirestore firestore;

    // UI Components
    private View viewGradientBg;
    private ImageView ivCover, btnBack;
    private TextView tvTitle, tvOwner, tvMeta;
    private RecyclerView rvVideos;

    // Adapter riêng cho Playlist (để có icon kéo thả)
    private PlaylistVideoAdapter adapter;
    private List<Video> videoList = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist_detail);

        // Nhận ID
        playlistId = getIntent().getStringExtra("playlist_id");
        if (playlistId == null) {
            Toast.makeText(this, "Error: Playlist ID missing", Toast.LENGTH_SHORT).show();
            finish(); return;
        }

        firestore = FirebaseFirestore.getInstance();
        initViews();

        // Setup RecyclerView
        adapter = new PlaylistVideoAdapter(videoList);
        rvVideos.setLayoutManager(new LinearLayoutManager(this));
        rvVideos.setAdapter(adapter);

        loadPlaylistInfo();
    }

    private void initViews() {
        viewGradientBg = findViewById(R.id.view_gradient_bg);
        ivCover = findViewById(R.id.iv_playlist_cover);
        tvTitle = findViewById(R.id.tv_playlist_title);
        tvOwner = findViewById(R.id.tv_owner_name);
        tvMeta = findViewById(R.id.tv_playlist_meta);
        rvVideos = findViewById(R.id.rv_videos);
        btnBack = findViewById(R.id.btn_back);

        btnBack.setOnClickListener(v -> finish());
    }

    private void loadPlaylistInfo() {
        firestore.collection("playlists").document(playlistId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) return;

                    Playlist playlist = documentSnapshot.toObject(Playlist.class);
                    if (playlist == null) return;

                    // 1. Set thông tin cơ bản
                    tvTitle.setText(playlist.getTitle());

                    // Lấy tên Owner
                    firestore.collection("users").document(playlist.getOwnerId()).get()
                            .addOnSuccessListener(userDoc -> {
                                if (userDoc.exists()) {
                                    tvOwner.setText(userDoc.getString("name"));
                                } else {
                                    tvOwner.setText("Unknown Owner");
                                }
                            });

                    // Set Meta
                    int count = (playlist.getVideoIds() != null) ? playlist.getVideoIds().size() : 0;
                    String visibility = playlist.getVisibility() != null ? playlist.getVisibility() : "Private";
                    // Viết hoa chữ cái đầu
                    String prettyVis = visibility.substring(0, 1).toUpperCase() + visibility.substring(1).toLowerCase();
                    tvMeta.setText(count + " videos • " + prettyVis);

                    // 2. Load Video & Xử lý Ảnh bìa + Màu nền
                    if (playlist.getVideoIds() != null && !playlist.getVideoIds().isEmpty()) {
                        loadVideosInPlaylist(playlist.getVideoIds());
                    } else {
                        // Nếu playlist rỗng -> Set nền mặc định màu xám
                        setDefaultGradient();
                    }
                });
    }

    private void loadVideosInPlaylist(List<String> videoIds) {
        // Lấy video đầu tiên làm ảnh bìa
        String firstVideoId = videoIds.get(0);

        firestore.collection("videos").whereIn(FieldPath.documentId(), videoIds)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    videoList.clear();

                    // Lưu tạm để xử lý logic
                    for (DocumentSnapshot doc : querySnapshot) {
                        Video video = doc.toObject(Video.class);
                        if (video != null) {
                            video.setVideoID(doc.getId());
                            videoList.add(video);

                            // --- LOGIC QUAN TRỌNG: LẤY ẢNH VÀ MÀU ---
                            if (doc.getId().equals(firstVideoId)) {
                                setCoverImageAndColor(video.getThumbnailURL());
                            }
                        }
                    }
                    adapter.notifyDataSetChanged();
                });
    }

    // --- HÀM XỬ LÝ ẢNH BÌA VÀ MÀU NỀN ---
    private void setCoverImageAndColor(String url) {
        // Dùng Glide để lấy Bitmap về (chứ không load thẳng vào ImageView ngay)
        Glide.with(this)
                .asBitmap()
                .load(url)
                .centerCrop()
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                        // 1. Set ảnh vào ImageView
                        ivCover.setImageBitmap(resource);

                        // 2. Dùng Palette trích xuất màu
                        Palette.from(resource).generate(palette -> {
                            // Lấy màu chủ đạo (Dominant), nếu không tìm thấy thì dùng màu xám
                            int defaultColor = Color.parseColor("#666666");
                            int color = palette != null ? palette.getDominantColor(defaultColor) : defaultColor;

                            // Có thể thử các loại màu khác nếu muốn rực rỡ hơn:
                            // int color = palette.getVibrantColor(defaultColor);

                            // 3. Tạo Gradient: Từ màu chủ đạo -> Trắng
                            GradientDrawable gd = new GradientDrawable(
                                    GradientDrawable.Orientation.TOP_BOTTOM,
                                    new int[] {color, ContextCompat.getColor(PlaylistDetailActivity.this, R.color.white)}
                            );
                            // Áp dụng vào view nền
                            viewGradientBg.setBackground(gd);
                        });
                    }
                    @Override public void onLoadCleared(@Nullable Drawable placeholder) {}
                });
    }

    private void setDefaultGradient() {
        int defaultColor = Color.parseColor("#666666");
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {defaultColor, ContextCompat.getColor(this, R.color.white)}
        );
        viewGradientBg.setBackground(gd);
    }
}