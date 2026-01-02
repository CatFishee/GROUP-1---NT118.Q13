package com.example.metube.ui.video;

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
import com.example.metube.ui.playlist.ChangeThumbnailBottomSheet;
import com.example.metube.ui.playlist.VisibilityBottomSheet;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class EditYourVideoActivity extends AppCompatActivity {

    private String videoId, currentVisibility;
    private boolean allowComments = true; // Biến lưu trạng thái comment

    private ImageView ivCover, ivVisibilityIcon;
    private TextInputEditText etTitle, etDesc;
    private TextView tvVisibility, tvCommentStatus;
    private Button btnSave;
    private Uri selectedImageUri;

    // Launcher chọn ảnh
    private final ActivityResultLauncher<String> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    ivCover.setImageURI(uri);
                }
            }
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            android.util.Log.d("EditVideo", "1. Starting onCreate");
            setContentView(R.layout.activity_edit_your_video);

            android.util.Log.d("EditVideo", "2. Layout inflated");

            // 1. Ánh xạ - THÊM LOG CHO TỪNG VIEW
            try {
                ivCover = findViewById(R.id.iv_cover);
                android.util.Log.d("EditVideo", "3. ivCover found");

                etTitle = findViewById(R.id.et_title);
                android.util.Log.d("EditVideo", "4. etTitle found");

                etDesc = findViewById(R.id.et_description);
                android.util.Log.d("EditVideo", "5. etDesc found");

                tvVisibility = findViewById(R.id.tv_visibility);
                android.util.Log.d("EditVideo", "6. tvVisibility found");

                ivVisibilityIcon = findViewById(R.id.iv_visibility_icon);
                android.util.Log.d("EditVideo", "7. ivVisibilityIcon found");

                tvCommentStatus = findViewById(R.id.tv_comment_status);
                android.util.Log.d("EditVideo", "8. tvCommentStatus found");

                btnSave = findViewById(R.id.btn_save);
                android.util.Log.d("EditVideo", "9. btnSave found");

            } catch (Exception e) {
                android.util.Log.e("EditVideo", "Error finding views: " + e.getMessage());
                e.printStackTrace();
                throw e;
            }

            // 2. Nhận dữ liệu
            android.util.Log.d("EditVideo", "10. Getting intent data");
            Intent intent = getIntent();
            if (intent == null) {
                android.util.Log.e("EditVideo", "Intent is null!");
                Toast.makeText(this, "Error: No data received", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            videoId = intent.getStringExtra("video_id");
            android.util.Log.d("EditVideo", "11. videoId: " + videoId);

            if (videoId == null || videoId.isEmpty()) {
                android.util.Log.e("EditVideo", "videoId is null or empty!");
                Toast.makeText(this, "Error: Invalid video ID", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            String title = intent.getStringExtra("title");
            String desc = intent.getStringExtra("description");
            currentVisibility = intent.getStringExtra("visibility");
            String thumbnail = intent.getStringExtra("thumbnail_url");
            allowComments = intent.getBooleanExtra("allow_comments", true);

            android.util.Log.d("EditVideo", "12. Data received - title: " + title + ", visibility: " + currentVisibility);

            // 3. Fill dữ liệu
            if (title != null) {
                etTitle.setText(title);
            }
            if (desc != null) {
                etDesc.setText(desc);
            }

            if (currentVisibility == null || currentVisibility.isEmpty()) {
                currentVisibility = "Private";
            }
            tvVisibility.setText(currentVisibility);
            updateVisibilityIcon(currentVisibility);

            tvCommentStatus.setText(allowComments ? "On" : "Off");

            android.util.Log.d("EditVideo", "13. Loading thumbnail: " + thumbnail);

            // Load ảnh hiện tại
            if (thumbnail != null && !thumbnail.isEmpty()) {
                Glide.with(this).load(thumbnail).centerCrop().into(ivCover);
            } else {
                loadVideoData();
            }

            android.util.Log.d("EditVideo", "14. Setting up click listeners");

            // 4. Sự kiện nút bấm
            findViewById(R.id.btn_close).setOnClickListener(v -> finish());

            // Edit Thumbnail
            findViewById(R.id.btn_edit_thumbnail).setOnClickListener(v -> {
                ChangeThumbnailBottomSheet sheet = new ChangeThumbnailBottomSheet(new ChangeThumbnailBottomSheet.OnOptionClickListener() {
                    @Override
                    public void onChooseFromLibrary() { pickImageLauncher.launch("image/*"); }
                    @Override
                    public void onTakePhoto() { Toast.makeText(EditYourVideoActivity.this, "Coming soon", Toast.LENGTH_SHORT).show(); }
                });
                sheet.show(getSupportFragmentManager(), "ChangeThumb");
            });

            // Edit Visibility
            findViewById(R.id.layout_visibility).setOnClickListener(v -> {
                VisibilityBottomSheet bottomSheet = new VisibilityBottomSheet(selectedVisibility -> {
                    this.currentVisibility = selectedVisibility;
                    tvVisibility.setText(selectedVisibility);
                    updateVisibilityIcon(selectedVisibility);
                });
                bottomSheet.show(getSupportFragmentManager(), "VisibilitySelector");
            });

            // Edit Comments
            findViewById(R.id.layout_comments).setOnClickListener(v -> {
                CommentsBottomSheet sheet = new CommentsBottomSheet(allowed -> {
                    this.allowComments = allowed;
                    tvCommentStatus.setText(allowed ? "On" : "Off");
                });
                sheet.show(getSupportFragmentManager(), "CommentSelector");
            });

            // SAVE
            btnSave.setOnClickListener(v -> handleSave());

            android.util.Log.d("EditVideo", "15. onCreate completed successfully");

        } catch (Exception e) {
            android.util.Log.e("EditVideo", "CRASH in onCreate: " + e.getMessage());
            e.printStackTrace();
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void updateVisibilityIcon(String visibility) {
        int iconRes = R.drawable.ic_lock;
        if ("Public".equals(visibility)) iconRes = R.drawable.ic_public;
        else if ("Unlisted".equals(visibility)) iconRes = R.drawable.ic_link;
        ivVisibilityIcon.setImageResource(iconRes);
    }

    private void handleSave() {
        String newTitle = etTitle.getText().toString().trim();
        String newDesc = etDesc.getText().toString().trim();

        if (newTitle.isEmpty()) {
            etTitle.setError("Title cannot be empty");
            return;
        }

        btnSave.setEnabled(false);
        btnSave.setText("Saving...");

        Map<String, Object> updates = new HashMap<>();
        updates.put("title", newTitle);
        updates.put("description", newDesc);
        updates.put("visibility", currentVisibility);
        updates.put("allowComments", allowComments); // Lưu thêm cái này

        if (selectedImageUri != null) {
            uploadThumbnailToCloudinary(updates);
        } else {
            updateFirestore(updates);
        }
    }

    private void uploadThumbnailToCloudinary(Map<String, Object> updates) {
        MediaManager.get().upload(selectedImageUri)
                .option("resource_type", "image")
                .callback(new UploadCallback() {
                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        String thumbnailUrl = resultData.get("secure_url").toString();
                        updates.put("thumbnailURL", thumbnailUrl);
                        updateFirestore(updates);
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        Toast.makeText(EditYourVideoActivity.this, "Upload failed", Toast.LENGTH_SHORT).show();
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
        FirebaseFirestore.getInstance().collection("videos").document(videoId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Video updated!", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Update failed", Toast.LENGTH_SHORT).show();
                    btnSave.setEnabled(true);
                    btnSave.setText("Save");
                });
    }

    private void loadVideoData() {
        FirebaseFirestore.getInstance().collection("videos").document(videoId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String thumb = doc.getString("thumbnailURL");
                        if (thumb != null) Glide.with(this).load(thumb).centerCrop().into(ivCover);
                        // Có thể load lại title, desc, comments... nếu Intent bị thiếu
                    }
                });
    }
}