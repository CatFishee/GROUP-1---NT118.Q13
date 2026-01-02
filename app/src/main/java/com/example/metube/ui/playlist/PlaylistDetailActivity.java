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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import com.example.metube.ui.history.HistoryMenuBottomSheet;
import com.example.metube.ui.video.MakeShareableDialog;
import com.example.metube.ui.video.VideoActivity;
import com.example.metube.utils.DownloadUtil;
import com.example.metube.utils.VideoQueueManager;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;


public class PlaylistDetailActivity extends AppCompatActivity implements HistoryMenuBottomSheet.HistoryMenuListener{

    private String playlistId;
    private FirebaseFirestore firestore;

    // UI Components
    private View viewGradientBg;
    private ImageView ivCover, btnBack;
    private TextView tvTitle, tvOwner, tvMeta, tvDescription;
    private RecyclerView rvVideos;

    // Adapter riêng cho Playlist (để có icon kéo thả)
    private PlaylistVideoAdapter adapter;
    private List<Video> videoList = new ArrayList<>();
    private Button btnPlayAll, btnShuffle;
    private ImageView btnDownload, btnEdit, btnShare, btnAddVideo;
    private List<Video> originalVideoList = new ArrayList<>();
    private CircleImageView ivOwnerAvatar;
    private String currentVisibility = "Private";
    private final ActivityResultLauncher<Intent> editPlaylistLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    loadPlaylistInfo(); // Reload lại thông tin mới
                }
            }
    );



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
        adapter = new PlaylistVideoAdapter(videoList, new PlaylistVideoAdapter.OnPlaylistVideoActionListener() {
            @Override
            public void onRemoveVideo(Video video, int position) {
                removeVideoFromPlaylist(video.getVideoID(), position);
            }

            @Override
            public void onShareVideo(Video video) {
                com.example.metube.utils.ShareUtil.shareVideo(
                        PlaylistDetailActivity.this,
                        video.getTitle(),
                        video.getVideoURL()
                );
            }
        });
        rvVideos.setLayoutManager(new LinearLayoutManager(this));
        rvVideos.setAdapter(adapter);


        loadPlaylistInfo();
    }
    private void removeVideoFromPlaylist(String videoIdToRemove, int position) {
        if (playlistId == null) return;

        // Dùng FieldValue.arrayRemove để xóa ID khỏi mảng videoIds
        firestore.collection("playlists").document(playlistId)
                .update("videoIds", FieldValue.arrayRemove(videoIdToRemove))
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Removed from playlist", Toast.LENGTH_SHORT).show();

                    // Cập nhật giao diện: Xóa khỏi list và báo Adapter
                    if (position >= 0 && position < videoList.size()) {
                        videoList.remove(position);
                        adapter.notifyItemRemoved(position);
                        adapter.notifyItemRangeChanged(position, videoList.size());

                        // (Tùy chọn) Cập nhật lại số lượng video ở Header nếu muốn
                        // loadPlaylistInfo(); // Hoặc trừ thủ công số đếm
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to remove: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
    @Override
    public void onRemoveFromHistory(String videoId, int position) {
        // Xóa videoId khỏi mảng videoIds của Playlist trên Firestore
        firestore.collection("playlists").document(playlistId)
                .update("videoIds", FieldValue.arrayRemove(videoId))
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Removed from playlist", Toast.LENGTH_SHORT).show();
                    // Cập nhật giao diện
                    videoList.remove(position);
                    adapter.notifyItemRemoved(position);
                });
    }

    private void initViews() {
        viewGradientBg = findViewById(R.id.view_gradient_bg);
        ivCover = findViewById(R.id.iv_playlist_cover);
        tvTitle = findViewById(R.id.tv_playlist_title);
        tvOwner = findViewById(R.id.tv_owner_name);
        tvMeta = findViewById(R.id.tv_playlist_meta);
        tvDescription = findViewById(R.id.tv_playlist_description);
        rvVideos = findViewById(R.id.rv_videos);
        btnBack = findViewById(R.id.btn_back);
        // Ánh xạ nút
        btnPlayAll = findViewById(R.id.btn_play_all);
        btnShuffle = findViewById(R.id.btn_shuffle);
        btnDownload = findViewById(R.id.btn_download);
        btnAddVideo = findViewById(R.id.btn_add_video);
        btnEdit = findViewById(R.id.btn_edit_playlist);
        btnShare = findViewById(R.id.btn_share_playlist);

        ivOwnerAvatar = findViewById(R.id.iv_owner_avatar);
        tvOwner = findViewById(R.id.tv_owner_name);


        btnBack.setOnClickListener(v -> finish());
        btnPlayAll.setOnClickListener(v -> {
            if (!videoList.isEmpty()) {
                // Đẩy list gốc vào Queue
                VideoQueueManager.getInstance().playPlaylist(videoList);

                // Mở màn hình phát, truyền ID của bài đầu tiên
                openVideoActivity(videoList.get(0).getVideoID());
            } else {
                Toast.makeText(this, "Playlist is empty", Toast.LENGTH_SHORT).show();
            }
        });
        btnShuffle.setOnClickListener(v -> {
            if (videoList != null && !videoList.isEmpty()) {
                List<Video> shuffledList = new ArrayList<>(videoList);
                Collections.shuffle(shuffledList);

                // Đẩy list đã xáo trộn vào Queue
                VideoQueueManager.getInstance().playPlaylist(shuffledList);

                // QUAN TRỌNG: Lấy ID của bài đầu tiên trong list ĐÃ XÁO TRỘN
                // Để VideoActivity biết bài nào phát đầu tiên
                openVideoActivity(shuffledList.get(0).getVideoID());

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
        btnShare.setOnClickListener(v -> handleSharePlaylist());
        btnAddVideo.setOnClickListener(v -> {
            // Chuyển sang màn hình tìm kiếm
            Intent intent = new Intent(this, com.example.metube.ui.search.SearchActivity.class);
            // (Tùy chọn) Có thể gửi kèm playlistId nếu bạn muốn màn hình Search biết đang thêm vào playlist nào
            // intent.putExtra("add_to_playlist_id", playlistId);
            startActivity(intent);
        });
        btnEdit.setOnClickListener(v -> {
            Intent intent = new Intent(this, EditPlaylistActivity.class);
            intent.putExtra("playlist_id", playlistId);
            intent.putExtra("title", tvTitle.getText().toString());
            intent.putExtra("visibility", tvMeta.getText().toString().contains("Private") ? "Private" : "Public");
            // intent.putExtra("description", ...);

            // Dùng launcher để khi edit xong quay lại thì reload data
            editPlaylistLauncher.launch(intent);
        });
        findViewById(R.id.btn_sort_playlist).setOnClickListener(this::showSortPopup);
        findViewById(R.id.btn_more_options).setOnClickListener(this::showPlaylistOptionsMenu);
    }
    private void handleSharePlaylist() {
        // Kiểm tra visibility
        if ("Private".equalsIgnoreCase(currentVisibility)) {
            // Nếu Private -> Hiển thị dialog bắt chọn Public hoặc Unlisted
            showMakeShareableDialog();
        } else {
            // Nếu đã Public hoặc Unlisted -> Share ngay
            sharePlaylist();
        }
    }
    private void showMakeShareableDialog() {
        MakeShareableDialog dialog = new MakeShareableDialog(this, visibility -> {
            // Callback khi user chọn Public hoặc Unlisted
            updatePlaylistVisibility(visibility);
        });

        dialog.setTitle("Make playlist shareable");
        dialog.show();
    }
    private void updatePlaylistVisibility(String newVisibility) {
        firestore.collection("playlists").document(playlistId)
                .update("visibility", newVisibility)
                .addOnSuccessListener(aVoid -> {
                    currentVisibility = newVisibility;

                    // Cập nhật UI
                    int count = videoList.size();
                    tvMeta.setText(count + " videos • " + newVisibility);

                    Toast.makeText(this, "Playlist is now " + newVisibility, Toast.LENGTH_SHORT).show();

                    // Share ngay sau khi cập nhật
                    sharePlaylist();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to update visibility: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
    private void sharePlaylist() {
        String link = "https://metube.app/playlist/" + playlistId;
        String title = tvTitle.getText().toString();
        com.example.metube.utils.ShareUtil.sharePlaylist(this, title, link);
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
    private void openVideoActivity(String firstVideoId) {
        Intent intent = new Intent(this, VideoActivity.class);
        // Luôn truyền ID video đầu tiên để VideoActivity không bị lỗi check null
        intent.putExtra("video_id", firstVideoId);
        startActivity(intent);
    }

    private void loadPlaylistInfo() {
        firestore.collection("playlists").document(playlistId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) return;

                    Playlist playlist = documentSnapshot.toObject(Playlist.class);
                    if (playlist == null) return;

                    tvTitle.setText(playlist.getTitle());
                    adapter.updatePlaylistTitle(playlist.getTitle());

                    firestore.collection("users").document(playlist.getOwnerId()).get()
                            .addOnSuccessListener(userDoc -> {
                                if (userDoc.exists()) {
                                    String name = userDoc.getString("name");
                                    tvOwner.setText("by " + name);
                                    String avatarUrl = userDoc.getString("profileURL");
                                    if (avatarUrl != null && !avatarUrl.isEmpty()) {
                                        Glide.with(this).load(avatarUrl).into(ivOwnerAvatar);
                                    }
                                }
                            });

                    int count = (playlist.getVideoIds() != null) ? playlist.getVideoIds().size() : 0;
                    currentVisibility = playlist.getVisibility() != null ? playlist.getVisibility() : "Private";
                    tvMeta.setText(count + " videos • " + currentVisibility);

                    String desc = playlist.getDescription();

                    // Kiểm tra null hoặc rỗng
                    if (desc != null && !desc.trim().isEmpty()) {
                        tvDescription.setText(desc);
                        tvDescription.setVisibility(View.VISIBLE);
                    } else {
                        tvDescription.setVisibility(View.GONE);
                    }

                    // --- LOGIC MỚI: ƯU TIÊN CUSTOM THUMBNAIL ---
                    String customThumb = documentSnapshot.getString("thumbnailURL");

                    if (customThumb != null && !customThumb.isEmpty()) {
                        // 1. Nếu có ảnh custom -> Load ngay
                        setCoverImageAndColor(customThumb);

                        // Vẫn load list video nhưng KHÔNG set lại cover nữa (tham số false)
                        if (playlist.getVideoIds() != null && !playlist.getVideoIds().isEmpty()) {
                            loadVideosInPlaylist(playlist.getVideoIds(), false);
                        }
                    } else {
                        // 2. Nếu không có ảnh custom -> Mới lấy từ video đầu tiên (tham số true)
                        if (playlist.getVideoIds() != null && !playlist.getVideoIds().isEmpty()) {
                            loadVideosInPlaylist(playlist.getVideoIds(), true);
                        } else {
                            setDefaultGradient();
                        }
                    }
                });
    }

    private void loadVideosInPlaylist(List<String> videoIds, boolean shouldSetCover) {
        String firstVideoId = videoIds.get(0);

        firestore.collection("videos").whereIn(FieldPath.documentId(), videoIds)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    videoList.clear();
                    Map<String, Video> tempMap = new HashMap<>();
                    for (DocumentSnapshot doc : querySnapshot) {
                        Video video = doc.toObject(Video.class);
                        if (video != null) {
                            video.setVideoID(doc.getId());
                            tempMap.put(doc.getId(), video);
                        }
                    }

                    for (String id : videoIds) {
                        if (tempMap.containsKey(id)) {
                            Video v = tempMap.get(id);
                            videoList.add(v);

                            // CHỈ SET COVER NẾU ĐƯỢC PHÉP (shouldSetCover = true)
                            // VÀ LÀ VIDEO ĐẦU TIÊN
                            if (shouldSetCover && id.equals(firstVideoId)) {
                                setCoverImageAndColor(v.getThumbnailURL());
                            }
                        }
                    }
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
                    Collections.sort(videoList, (v1, v2) -> {
                        // Xử lý null để tránh lỗi (coi như 0 view)
                        long view1 = v1.getViewCount(); // Đảm bảo getter trả về long primitive hoặc check null
                        long view2 = v2.getViewCount();

                        // So sánh GIẢM DẦN: v2 - v1
                        // Dùng Long.compare để an toàn với số lớn
                        return Long.compare(view2, view1);
                    });
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
    // 2. Hàm hiển thị Menu
    private void showPlaylistOptionsMenu(View view) {
        PopupMenu popup = new PopupMenu(this, view);

        popup.getMenu().add(0, 1, 0, "Delete playlist");

        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                confirmDeletePlaylist();
                return true;
            }
            return false;
        });

        popup.show();
    }
    private void confirmDeletePlaylist() {
        new AlertDialog.Builder(this)
                .setTitle("Delete playlist")
                .setMessage("Are you sure you want to delete this playlist?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    deletePlaylistFromFirestore();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    private void deletePlaylistFromFirestore() {
        if (playlistId == null) return;

        firestore.collection("playlists").document(playlistId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Playlist deleted", Toast.LENGTH_SHORT).show();

                    // Xóa xong thì đóng màn hình này quay về danh sách
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to delete: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
    @Override
    public void onDownload(Video video) {
        com.example.metube.utils.DownloadUtil.downloadVideo(
                this,
                video.getVideoURL(),
                video.getTitle()
        );
        Toast.makeText(this, "Downloading video...", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onShare(Video video) {
        com.example.metube.utils.ShareUtil.shareVideo(
                this,
                video.getTitle(),
                video.getVideoURL()
        );
    }

    @Override
    public void onPlayNextInQueue(Video video) {
        // Thêm video vào hàng chờ
        com.example.metube.utils.VideoQueueManager.getInstance().playNext(video);
        Toast.makeText(this, "Video added to play next", Toast.LENGTH_SHORT).show();
    }
    @Override
    public void onSaveToPlaylist(Video video) {
        // Mở bottom sheet để chọn playlist
        com.example.metube.ui.playlist.AddToPlaylistBottomSheet bottomSheet =
                new com.example.metube.ui.playlist.AddToPlaylistBottomSheet(video);
        bottomSheet.show(getSupportFragmentManager(), "AddToPlaylistBottomSheet");
    }

}