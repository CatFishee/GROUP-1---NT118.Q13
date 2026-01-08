package com.example.metube.ui.video;

import static java.security.AccessController.getContext;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.metube.R;
import com.example.metube.model.Video;
import com.example.metube.ui.playlist.AddToPlaylistBottomSheet;
import com.example.metube.ui.upload.UploadActivity; // Màn hình upload của bạn
import com.example.metube.utils.DownloadUtil;
import com.example.metube.utils.ShareUtil;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class YourVideosActivity extends AppCompatActivity implements YourVideoMenuBottomSheet.YourVideoMenuListener {

    private RecyclerView rvYourVideos;
    private LinearLayout layoutEmptyState;
    private Button btnCreate;
    private YourVideosAdapter adapter; // Tái sử dụng HistoryAdapter vì layout giống hệt
    private List<Video> videoList = new ArrayList<>();
    private List<Video> allVideosList = new ArrayList<>();
    private ImageView btnSort, btnBack;
    private com.google.android.material.chip.Chip chipVideos, chipClip;
    private String currentFilter = "Video";
    private final ActivityResultLauncher<Intent> editVideoLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    // Reload lại danh sách video sau khi edit xong
                    loadUserVideos();
                }
            }
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_your_videos);

        // 1. Ánh xạ
        rvYourVideos = findViewById(R.id.rv_your_videos);
        layoutEmptyState = findViewById(R.id.layout_empty_state);
        btnCreate = findViewById(R.id.btn_create_video);
        btnSort = findViewById(R.id.btn_sort);
        btnBack = findViewById(R.id.btn_back);

        chipVideos = findViewById(R.id.chip_videos);
        chipClip = findViewById(R.id.chip_clip);

        // 2. Setup RecyclerView
        adapter = new YourVideosAdapter(videoList); // Khởi tạo adapter mới
        rvYourVideos.setLayoutManager(new LinearLayoutManager(this));
        adapter.setOnItemMoreClickListener(video -> {
            YourVideoMenuBottomSheet bottomSheet = new YourVideoMenuBottomSheet(video, this);
            bottomSheet.show(getSupportFragmentManager(), "YourVideoMenu");
        });
        rvYourVideos.setAdapter(adapter);



        // 3. Sự kiện Click
        btnBack.setOnClickListener(v -> finish());

        btnCreate.setOnClickListener(v -> {
            // Chuyển sang màn hình Upload
            Intent intent = new Intent(this, UploadActivity.class);
            startActivity(intent);
        });
        btnSort.setOnClickListener(this::showSortMenu);

        setupChipFilters();

        // 4. Load Data
        loadUserVideos();
    }
    private void setupChipFilters() {
        chipVideos.setOnClickListener(v -> {
            currentFilter = "Video";
            filterVideos();
        });

        chipVideos.setOnClickListener(v -> {
            currentFilter = "Video";
            chipVideos.setChecked(true); // Làm nổi bật chip đang chọn
            chipClip.setChecked(false);
            filterVideos();
        });
    }
    private void filterVideos() {
        if (allVideosList == null) return;
        videoList.clear();

        for (Video video : allVideosList) {
            if (video == null) continue; // Chống NullPointerException

            long durationMs = video.getDuration();

            // 🛠 SỬA TẠI ĐÂY: Clip là video dưới 60 giây (60,000 ms)
            boolean isClip = durationMs > 0 && durationMs < 60000;
            boolean isVideo = durationMs >= 60000 || durationMs <= 0; // Video dài hoặc chưa có dữ liệu duration

            if (currentFilter.equals("Video") && isVideo) {
                videoList.add(video);
            } else if (currentFilter.equals("Clip") && isClip) {
                videoList.add(video);
            }
        }

        // Cập nhật giao diện
        runOnUiThread(() -> {
            if (videoList.isEmpty()) {
                rvYourVideos.setVisibility(View.GONE);
                layoutEmptyState.setVisibility(View.VISIBLE);
            } else {
                rvYourVideos.setVisibility(View.VISIBLE);
                layoutEmptyState.setVisibility(View.GONE);
            }
            adapter.notifyDataSetChanged();
        });
    }
    private void showSortMenu(View v) {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(this, v);
        popup.getMenu().add(0, 0, 0, "Most recent (Default)");
        popup.getMenu().add(0, 1, 1, "Most viewed");

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 0: // Most recent
                    sortVideosByDate();
                    return true;
                case 1: // Most viewed
                    sortVideosByViews();
                    return true;
            }
            return false;
        });
        popup.show();
    }
    private void sortVideosByDate() {
        Collections.sort(videoList, (v1, v2) -> {
            if (v1.getCreatedAt() == null || v2.getCreatedAt() == null) return 0;
            return v2.getCreatedAt().compareTo(v1.getCreatedAt()); // Giảm dần (Mới nhất)
        });
        adapter.notifyDataSetChanged();
    }

    private void sortVideosByViews() {
        Collections.sort(videoList, (v1, v2) -> {
            // So sánh View (Giảm dần)
            return Long.compare(v2.getViewCount(), v1.getViewCount());
        });
        adapter.notifyDataSetChanged();
        Toast.makeText(this, "Sorted by most viewed", Toast.LENGTH_SHORT).show();
    }


    private void loadUserVideos() {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        FirebaseFirestore.getInstance().collection("videos")
                .whereEqualTo("uploaderID", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    allVideosList.clear();

                    if (querySnapshot.isEmpty()) {
                        rvYourVideos.setVisibility(View.GONE);
                        layoutEmptyState.setVisibility(View.VISIBLE);
                    } else {
                        rvYourVideos.setVisibility(View.VISIBLE);
                        layoutEmptyState.setVisibility(View.GONE);

                        for (DocumentSnapshot doc : querySnapshot) {
                            Video video = doc.toObject(Video.class);
                            if (video != null) {
                                video.setVideoID(doc.getId());

                                allVideosList.add(video);
                            }
                        }
                        filterVideos();
                        // --- QUAN TRỌNG: SAU KHI CÓ LIST, ĐI LẤY VIEW COUNT TỪ REALTIME ---
                        fetchRealtimeViews();

                        adapter.notifyDataSetChanged();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error loading videos", Toast.LENGTH_SHORT).show();
                });
    }
    private void fetchRealtimeViews() {
        if (allVideosList.isEmpty()) return; // Kiểm tra allVideosList

        for (Video video : videoList) {
            FirebaseDatabase.getInstance().getReference("videostat")
                    .child(video.getVideoID())
                    .child("viewCount")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (snapshot.exists()) {
                                Object val = snapshot.getValue();
                                long views = 0;
                                if (val instanceof Long) views = (Long) val;
                                else if (val instanceof Integer) views = ((Integer) val).longValue();

                                // Cập nhật vào object trong list
                                video.setViewCount(views);

                                // (Tùy chọn) notify để cập nhật ngay lập tức nếu cần thiết
                                // adapter.notifyDataSetChanged();
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) { }
                    });
        }
    }

    // Khi upload xong quay lại thì reload list
    @Override
    protected void onResume() {
        super.onResume();
        loadUserVideos();
    }
    @Override
    public void onSaveToPlaylist(Video video) {
        // Gọi Bottom Sheet chọn Playlist (Tái sử dụng)
        AddToPlaylistBottomSheet sheet = new AddToPlaylistBottomSheet(video);
        sheet.show(getSupportFragmentManager(), "AddToPlaylist");
    }

    @Override
    public void onShareVideo(Video video) {
        // Kiểm tra nếu video là Private
        String visibility = video.getVisibility();
        if (visibility == null || visibility.isEmpty()) {
            visibility = "Private";
        }

        if ("Private".equalsIgnoreCase(visibility)) {
            MakeShareableDialog dialog = new MakeShareableDialog(this, newVisibility -> {
                updateVideoVisibility(video, newVisibility, () -> {
                    // ✅ SỬA: Dùng this thay vì getContext()
                    ShareUtil.shareVideo(this, video.getVideoURL());
                });
            });
            dialog.show();
        } else {
            // ✅ SỬA: Dùng this thay vì getContext()
            ShareUtil.shareVideo(this, video.getVideoURL());
        }
    }

    @Override
    public void onEditVideo(Video video) {
        Intent intent = new Intent(this, EditYourVideoActivity.class);
        intent.putExtra("video_id", video.getVideoID());
        intent.putExtra("title", video.getTitle());
        intent.putExtra("description", video.getDescription());
        intent.putExtra("visibility", video.getVisibility());
        intent.putExtra("thumbnail_url", video.getThumbnailURL());
        intent.putExtra("allow_comments", video.isAllowComments());

        editVideoLauncher.launch(intent);
    }

    @Override
    public void onSaveToDevice(Video video) {
        // Gọi DownloadUtil (Tái sử dụng)
        DownloadUtil.downloadVideo(this, video.getVideoURL(), video.getTitle());
    }

    @Override
    public void onDeleteVideo(Video video) {
        // Hiện hộp thoại xác nhận xóa
        new AlertDialog.Builder(this)
                .setTitle("Delete video")
                .setMessage("Delete this video permanently?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    deleteVideoFromFirestore(video);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    // Hàm xóa video trên Firestore
    private void deleteVideoFromFirestore(Video video) {
        FirebaseFirestore.getInstance().collection("videos")
                .document(video.getVideoID())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Video deleted", Toast.LENGTH_SHORT).show();

                    // Cập nhật lại list trên giao diện
                    videoList.remove(video);
                    adapter.notifyDataSetChanged();

                    // Kiểm tra nếu list trống thì hiện layout empty
                    if (videoList.isEmpty()) {
                        rvYourVideos.setVisibility(View.GONE);
                        layoutEmptyState.setVisibility(View.VISIBLE);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to delete: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
    private void updateVideoVisibility(Video video, String newVisibility, Runnable onSuccess) {
        FirebaseFirestore.getInstance().collection("videos")
                .document(video.getVideoID())
                .update("visibility", newVisibility)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Video is now " + newVisibility, Toast.LENGTH_SHORT).show();
                    video.setVisibility(newVisibility); // Update local
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to update visibility", Toast.LENGTH_SHORT).show();
                });
    }
}