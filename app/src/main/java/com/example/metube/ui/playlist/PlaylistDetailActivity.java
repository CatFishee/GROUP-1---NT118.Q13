package com.example.metube.ui.playlist;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.widget.PopupMenu;
import java.util.Collections;
import java.util.Comparator;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.example.metube.R;
import com.example.metube.model.Playlist;
import com.example.metube.model.Video;
import com.example.metube.ui.video.VideoActivity;
import com.example.metube.utils.DownloadUtil;
import com.example.metube.utils.VideoQueueManager;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private Button btnPlayAll, btnShuffle;
    private ImageView btnDownload;
    private List<Video> originalVideoList = new ArrayList<>();


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
        // Ánh xạ nút
        btnPlayAll = findViewById(R.id.btn_play_all);
        btnShuffle = findViewById(R.id.btn_shuffle);
        btnDownload = findViewById(R.id.btn_download);


        btnBack.setOnClickListener(v -> finish());
        btnPlayAll.setOnClickListener(v -> {
            if (!videoList.isEmpty()) {
                // Đẩy toàn bộ list vào Queue
                VideoQueueManager.getInstance().playPlaylist(videoList);

                // Mở màn hình Video (nó sẽ tự play bài đầu tiên trong queue)
                openVideoActivity();
            } else {
                Toast.makeText(this, "Playlist is empty", Toast.LENGTH_SHORT).show();
            }
        });
        btnShuffle.setOnClickListener(v -> {
            if (videoList != null && !videoList.isEmpty()) {
                // 1. Tạo bản sao của danh sách (để không làm xáo trộn giao diện danh sách bên dưới)
                List<Video> shuffledList = new ArrayList<>(videoList);

                // 2. Xáo trộn ngẫu nhiên
                Collections.shuffle(shuffledList);

                // 3. Đưa vào hàng chờ (Queue)
                com.example.metube.utils.VideoQueueManager.getInstance().playPlaylist(shuffledList);

                // 4. Mở màn hình phát
                Intent intent = new Intent(this, com.example.metube.ui.video.VideoActivity.class);
                startActivity(intent);

                Toast.makeText(this, "Shuffling playlist...", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Playlist is empty", Toast.LENGTH_SHORT).show();
            }
        });
        btnDownload.setOnClickListener(v -> {
            if (videoList == null || videoList.isEmpty()) {
                Toast.makeText(this, "Playlist is empty", Toast.LENGTH_SHORT).show();
                return;
            }

            // Hiển thị hộp thoại xác nhận trước khi tải hàng loạt
            showDownloadConfirmationDialog();
        });
        findViewById(R.id.btn_sort_playlist).setOnClickListener(this::showSortPopup);
    }
    private void showDownloadConfirmationDialog() {
        int count = videoList.size();

        new AlertDialog.Builder(this)
                .setTitle("Download Playlist")
                .setMessage("Do you want to download " + count + " videos from this playlist?")
                .setPositiveButton("Download", (dialog, which) -> {
                    // Bắt đầu tải từng video
                    startDownloadAll();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startDownloadAll() {
        Toast.makeText(this, "Starting download...", Toast.LENGTH_SHORT).show();

        for (Video video : videoList) {
            // Gọi hàm tiện ích DownloadUtil mà bạn đã viết
            DownloadUtil.downloadVideo(
                    this,
                    video.getVideoURL(),
                    video.getTitle()
            );
        }
    }
    private void openVideoActivity() {
        Intent intent = new Intent(this, VideoActivity.class);
        // Không cần putExtra video_id vì VideoActivity sẽ tự check QueueManager trước
        startActivity(intent);
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
        String firstVideoId = videoIds.get(0);

        firestore.collection("videos").whereIn(FieldPath.documentId(), videoIds)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    videoList.clear();

                    // Tạo map tạm để sắp xếp đúng thứ tự videoIds (Date added)
                    Map<String, Video> tempMap = new HashMap<>();
                    for (DocumentSnapshot doc : querySnapshot) {
                        Video video = doc.toObject(Video.class);
                        if (video != null) {
                            video.setVideoID(doc.getId());
                            tempMap.put(doc.getId(), video);
                        }
                    }

                    // Loop theo thứ tự trong mảng videoIds của Playlist
                    for (String id : videoIds) {
                        if (tempMap.containsKey(id)) {
                            Video v = tempMap.get(id);
                            videoList.add(v);

                            // Load ảnh bìa
                            if (id.equals(firstVideoId)) {
                                setCoverImageAndColor(v.getThumbnailURL());
                            }
                        }
                    }

                    // LƯU LẠI LIST GỐC (Backup)
                    originalVideoList = new ArrayList<>(videoList);

                    adapter.notifyDataSetChanged();
                });
    }
    private void showSortPopup(View v) {
        PopupMenu popup = new PopupMenu(this, v);

        // Thêm các lựa chọn y hệt ảnh
        popup.getMenu().add(0, 0, 0, "Manual");
        popup.getMenu().add(0, 1, 1, "Date added (newest)");
        popup.getMenu().add(0, 2, 2, "Date added (oldest)");
        popup.getMenu().add(0, 3, 3, "Most popular");
        popup.getMenu().add(0, 4, 4, "Date published (newest)");
        popup.getMenu().add(0, 5, 5, "Date published (oldest)");

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 0: // Manual (Khôi phục thứ tự gốc)
                    videoList.clear();
                    videoList.addAll(originalVideoList);
                    break;

                case 1: // Date added (newest) -> Đảo ngược list gốc
                    videoList.clear();
                    videoList.addAll(originalVideoList);
                    Collections.reverse(videoList);
                    break;

                case 2: // Date added (oldest) -> Giống Manual
                    videoList.clear();
                    videoList.addAll(originalVideoList);
                    break;

                case 3: // Most popular (Nhiều view nhất lên đầu)
                    Collections.sort(videoList, (v1, v2) ->
                            Long.compare(v2.getViewCount(), v1.getViewCount())
                    );
                    break;

                case 4: // Date published (newest) -> Ngày tạo mới nhất
                    Collections.sort(videoList, (v1, v2) -> {
                        if (v1.getCreatedAt() == null || v2.getCreatedAt() == null) return 0;
                        return v2.getCreatedAt().compareTo(v1.getCreatedAt());
                    });
                    break;

                case 5: // Date published (oldest) -> Ngày tạo cũ nhất
                    Collections.sort(videoList, (v1, v2) -> {
                        if (v1.getCreatedAt() == null || v2.getCreatedAt() == null) return 0;
                        return v1.getCreatedAt().compareTo(v2.getCreatedAt());
                    });
                    break;
            }
            adapter.notifyDataSetChanged();
            return true;
        });

        popup.show();
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