package com.example.metube.ui.playlist;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.example.metube.R;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class EditPlaylistActivity extends AppCompatActivity {

    private String playlistId, currentVisibility;
    private ImageView ivCover, ivVisibilityIcon;
    private TextInputEditText etTitle, etDesc;
    private TextView tvVisibility;
    private Uri selectedImageUri; // Ảnh mới chọn (nếu có)
    private Button btnSave;

    // Launcher chọn ảnh từ thư viện
    private final ActivityResultLauncher<String> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    ivCover.setImageURI(uri); // Hiển thị tạm
                }
            }
    );

    // Launcher chụp ảnh (cần thêm logic Camera nếu muốn làm full, tạm thời chỉ làm thư viện)

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_playlist);

        // 1. Ánh xạ
        ivCover = findViewById(R.id.iv_cover);
        etTitle = findViewById(R.id.et_title);
        etDesc = findViewById(R.id.et_description);
        tvVisibility = findViewById(R.id.tv_visibility);
        ivVisibilityIcon = findViewById(R.id.iv_visibility_icon);
        btnSave = findViewById(R.id.btn_save);

        // 2. Nhận dữ liệu từ PlaylistDetail
        Intent intent = getIntent();
        playlistId = intent.getStringExtra("playlist_id");
        String title = intent.getStringExtra("title");
        String desc = intent.getStringExtra("description"); // Cần thêm field này vào model Playlist nếu chưa có
        currentVisibility = intent.getStringExtra("visibility");

        // 3. Fill dữ liệu
        etTitle.setText(title);
        if (desc != null) etDesc.setText(desc);

        // Setup Visibility ban đầu
        if (currentVisibility == null) currentVisibility = "Private";
        tvVisibility.setText(currentVisibility);
        updateVisibilityIcon(currentVisibility); // <-- Cập nhật icon ngay lúc đầu

        // 4. LOAD ẢNH THUMBNAIL TỪ FIREBASE
        loadCurrentThumbnail();

        // 4. Sự kiện nút bấm
        findViewById(R.id.btn_close).setOnClickListener(v -> finish());

        // Edit Thumbnail -> Mở BottomSheet
        findViewById(R.id.btn_edit_thumbnail).setOnClickListener(v -> {
            ChangeThumbnailBottomSheet sheet = new ChangeThumbnailBottomSheet(new ChangeThumbnailBottomSheet.OnOptionClickListener() {
                @Override
                public void onChooseFromLibrary() {
                    pickImageLauncher.launch("image/*");
                }
                @Override
                public void onTakePhoto() {
                    Toast.makeText(EditPlaylistActivity.this, "Camera feature coming soon", Toast.LENGTH_SHORT).show();
                    // Nếu muốn làm camera, cần FileProvider và logic phức tạp hơn
                }
            });
            sheet.show(getSupportFragmentManager(), "ChangeThumb");
        });

        // Edit Visibility -> Reuse BottomSheet chọn quyền
        findViewById(R.id.layout_visibility).setOnClickListener(v -> {

            // Gọi BottomSheet mới tạo
            VisibilityBottomSheet bottomSheet = new VisibilityBottomSheet(selectedVisibility -> {

                // Cập nhật biến toàn cục
                this.currentVisibility = selectedVisibility;

                // Cập nhật giao diện (Text)
                tvVisibility.setText(selectedVisibility);

                // (Tùy chọn) Cập nhật Icon khóa/công khai nếu muốn xịn hơn
                updateVisibilityIcon(selectedVisibility);
            });

            bottomSheet.show(getSupportFragmentManager(), "VisibilitySelector");
        });

        // SAVE
        findViewById(R.id.btn_save).setOnClickListener(v -> handleSave());
    }
    private void updateVisibilityIcon(String visibility) {
        int iconRes = R.drawable.ic_lock; // Mặc định Private
        if (visibility.equals("Public")) iconRes = R.drawable.ic_public;
        else if (visibility.equals("Unlisted")) iconRes = R.drawable.ic_link;

        ivVisibilityIcon.setImageResource(iconRes);
    }

    private void handleSave() {
        String newTitle = etTitle.getText().toString().trim();
        String newDesc = etDesc.getText().toString().trim();

        if (newTitle.isEmpty()) {
            etTitle.setError("Title cannot be empty");
            return;
        }

        // Disable nút lưu để tránh bấm nhiều lần
        btnSave.setEnabled(false);
        btnSave.setText("Saving...");

        Map<String, Object> updates = new HashMap<>();
        updates.put("title", newTitle);
        updates.put("description", newDesc);
        updates.put("visibility", currentVisibility);

        // TRƯỜNG HỢP 1: Có chọn ảnh mới -> Upload trước
        if (selectedImageUri != null) {
            uploadThumbnailToCloudinary(updates);
        } else {
            // TRƯỜNG HỢP 2: Không đổi ảnh -> Chỉ update thông tin
            updateFirestore(updates);
        }
    }
    private void uploadThumbnailToCloudinary(Map<String, Object> updates) {
        MediaManager.get().upload(selectedImageUri)
                .option("resource_type", "image")
                .callback(new UploadCallback() {
                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        // Lấy link ảnh từ Cloudinary
                        String thumbnailUrl = resultData.get("secure_url").toString();

                        // Thêm link ảnh vào map update (lưu vào trường customThumbnail)
                        updates.put("thumbnailURL", thumbnailUrl);

                        // Tiến hành update Firestore
                        updateFirestore(updates);
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        Toast.makeText(EditPlaylistActivity.this, "Upload failed: " + error.getDescription(), Toast.LENGTH_SHORT).show();
                        btnSave.setEnabled(true);
                        btnSave.setText("Save");
                    }
                    @Override public void onStart(String requestId) { }
                    @Override public void onProgress(String requestId, long bytes, long totalBytes) { }
                    @Override public void onReschedule(String requestId, ErrorInfo error) { }
                })
                .dispatch();
    }
    private void updateFirestore(Map<String, Object> updates) {
        FirebaseFirestore.getInstance().collection("playlists").document(playlistId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Playlist updated!", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK); // Báo cho màn hình trước biết để reload
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnSave.setEnabled(true);
                    btnSave.setText("Save");
                });
    }
    private void loadCurrentThumbnail() {
        FirebaseFirestore.getInstance()
                .collection("playlists").document(playlistId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {

                        // 1. Kiểm tra xem Playlist có ảnh Custom không?
                        String customThumb = documentSnapshot.getString("thumbnailURL");

                        if (customThumb != null && !customThumb.isEmpty()) {
                            // Nếu có ảnh custom thì load luôn
                            Glide.with(this).load(customThumb).centerCrop().into(ivCover);
                        } else {
                            // 2. Nếu không có, mới lấy từ video đầu tiên
                            java.util.List<String> videoIds = (java.util.List<String>) documentSnapshot.get("videoIds");

                            if (videoIds != null && !videoIds.isEmpty()) {
                                String firstVideoId = videoIds.get(0);
                                FirebaseFirestore.getInstance()
                                        .collection("videos").document(firstVideoId)
                                        .get()
                                        .addOnSuccessListener(videoDoc -> {
                                            if (videoDoc.exists()) {
                                                String thumbUrl = videoDoc.getString("thumbnailURL");
                                                if (thumbUrl != null && !thumbUrl.isEmpty()) {
                                                    Glide.with(this).load(thumbUrl).centerCrop().into(ivCover);
                                                }
                                            }
                                        });
                            } else {
                                ivCover.setImageResource(android.R.color.darker_gray);
                            }
                        }
                    }
                });
    }
}