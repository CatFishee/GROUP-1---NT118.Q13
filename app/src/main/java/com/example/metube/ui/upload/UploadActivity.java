package com.example.metube.ui.upload;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.example.metube.R;
import com.example.metube.model.Video;
import com.example.metube.ui.notifications.NotificationHelper;
import com.example.metube.ui.playlist.VisibilityBottomSheet;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabel;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import java.util.stream.Collectors;

public class UploadActivity extends AppCompatActivity {

    private static final String TAG = "UploadActivity";
    private static final String CLOUDINARY_UPLOAD_PRESET = "metube";

    private ImageView ivThumbnailPreview;
    private Button btnSelectThumbnail, btnSelectVideo, btnUploadVideo;
    private TextView tvSelectedVideoName, tvVisibilityStatus;
    private EditText etVideoTitle, etVideoDescription;
    private ProgressBar progressBar;
    private String currentVisibility = "Public";
    private ImageView ivVisibilityIcon;
    private LinearLayout layoutSelectVisibility;
    private Uri thumbnailUri, videoUri;
    private ChipGroup chipGroupTopics;
    private AutoCompleteTextView acAddTopic;

    private FirebaseAuth mAuth;
    private FirebaseFirestore firestore;

    private final ActivityResultLauncher<Intent> thumbnailPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    thumbnailUri = result.getData().getData();
                    Glide.with(this).load(thumbnailUri).into(ivThumbnailPreview);
                    // AI Quét ảnh ngay khi chọn
                    checkImageContentAI(thumbnailUri);
                }
            }
    );

    private final ActivityResultLauncher<Intent> videoPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    videoUri = result.getData().getData();
                    tvSelectedVideoName.setText(getFileNameFromUri(videoUri));
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);
        initViews();
        mAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
    }

    private void initViews() {
        ivThumbnailPreview = findViewById(R.id.iv_thumbnail_preview);
        btnSelectThumbnail = findViewById(R.id.btn_select_thumbnail);
        btnSelectVideo = findViewById(R.id.btn_select_video);
        btnUploadVideo = findViewById(R.id.btn_upload_video);
        tvSelectedVideoName = findViewById(R.id.tv_selected_video_name);
        etVideoTitle = findViewById(R.id.et_video_title);
        etVideoDescription = findViewById(R.id.et_video_description);
        progressBar = findViewById(R.id.progress_bar_upload);
        chipGroupTopics = findViewById(R.id.chip_group_topics);
        acAddTopic = findViewById(R.id.actv_add_topic);
        tvVisibilityStatus = findViewById(R.id.tv_visibility_status);
        ivVisibilityIcon = findViewById(R.id.iv_visibility_icon);
        layoutSelectVisibility = findViewById(R.id.layout_select_visibility);

        setupTopicInput();
        findViewById(R.id.iv_close_upload).setOnClickListener(v -> finish());
        btnSelectThumbnail.setOnClickListener(v -> openPicker("image/*", thumbnailPickerLauncher));
        btnSelectVideo.setOnClickListener(v -> openPicker("video/*", videoPickerLauncher));
        btnUploadVideo.setOnClickListener(v -> uploadFiles());

        layoutSelectVisibility.setOnClickListener(v -> {
            new VisibilityBottomSheet(visibility -> {
                currentVisibility = visibility;
                tvVisibilityStatus.setText(visibility);
                switch (visibility) {
                    case "Public": ivVisibilityIcon.setImageResource(R.drawable.ic_public); break;
                    case "Unlisted": ivVisibilityIcon.setImageResource(R.drawable.ic_link); break;
                    case "Private": ivVisibilityIcon.setImageResource(R.drawable.ic_lock); break;
                }
            }).show(getSupportFragmentManager(), "VisibilityBottomSheet");
        });
    }

    // Thay đổi cách mở picker
    private void openPicker(String type, ActivityResultLauncher<Intent> launcher) {
        if ("image/*".equals(type)) {
            // Dùng launcher trực tiếp với string type
            thumbnailPickerLauncher.launch(new Intent(Intent.ACTION_GET_CONTENT).setType(type));
        } else {
            videoPickerLauncher.launch(new Intent(Intent.ACTION_GET_CONTENT).setType(type));
        }
    }

    private void uploadFiles() {
        String title = etVideoTitle.getText().toString().trim();
        if (videoUri == null || title.isEmpty()) {
            Toast.makeText(this, "Please select a video and title.", Toast.LENGTH_SHORT).show();
            return;
        }

        // BẮT ĐẦU QUÉT AI CHO VIDEO TRƯỚC KHI LÊN CLOUDINARY
        checkVideoContentAI(videoUri, () -> {
            setUploadingState(true);
            if (thumbnailUri == null) {
                try {
                    thumbnailUri = extractFirstFrameAsJpeg(videoUri);
                } catch (Exception e) { thumbnailUri = null; }
            }
            uploadThumbnailAndThenVideo(title, etVideoDescription.getText().toString().trim());
        });
    }

    private void checkImageContentAI(Uri uri) {
        try {
            InputImage image = InputImage.fromFilePath(this, uri);
            ImageLabeler labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS);

            labeler.process(image)
                    .addOnSuccessListener(labels -> {
                        List<String> found = new ArrayList<>();
                        for (ImageLabel l : labels) {
                            String text = l.getText().toLowerCase();
                            // confidence > 0.6 để tránh nhận diện nhầm
                            if (l.getConfidence() > 0.6 && (text.contains("cat") || text.contains("dog") || text.contains("frog") || text.contains("animal"))) {
                                found.add(text);
                            }
                        }
                        if (!found.isEmpty()) {
                            showSimpleAIWarning(found, null);
                        }
                        labeler.close();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "AI Thumbnail Error: " + e.getMessage());
                        // Không crash, cho phép tiếp tục nếu AI lỗi
                    });
        } catch (Exception e) {
            Log.e(TAG, "InputImage Error: " + e.getMessage());
        }
    }

    private void checkVideoContentAI(Uri uri, Runnable onSuccessAction) {
        setUploadingState(true);
        Toast.makeText(this, "AI đang kiểm duyệt video...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            ImageLabeler labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS);
            try {
                retriever.setDataSource(this, uri);
                long duration = Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) * 1000;
                long[] checkPoints = {duration / 10, duration / 2, (duration * 9) / 10};
                List<String> found = new ArrayList<>();

                for (long timeUs : checkPoints) {
                    Bitmap frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    if (frame != null) {
                        InputImage image = InputImage.fromBitmap(frame, 0);
                        List<ImageLabel> labels = Tasks.await(labeler.process(image));
                        for (ImageLabel l : labels) {
                            String t = l.getText().toLowerCase();
                            if (l.getConfidence() > 0.6 && (t.contains("cat") || t.contains("dog") || t.contains("frog") || t.contains("animal"))) {
                                if (!found.contains(t)) found.add(t);
                            }
                        }
                    }
                }
                runOnUiThread(() -> {
                    if (!found.isEmpty()) {
                        setUploadingState(false);
                        showSimpleAIWarning(found, onSuccessAction);
                    } else {
                        onSuccessAction.run();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(onSuccessAction);
            } finally {
                try { retriever.release(); } catch (IOException e) {}
                labeler.close();
            }
        }).start();
    }

    private void showSimpleAIWarning(List<String> animals, Runnable onSuccessAction) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("AI Content Warning!")
                .setMessage("AI phát hiện nội dung không phù hợp: " + String.join(", ", animals) +
                        ".\n\nBạn có muốn tiếp tục đăng không?")
                .setCancelable(false)
                .setPositiveButton("Vẫn đăng", (d, w) -> {
                    if (onSuccessAction != null) onSuccessAction.run();
                })
                .setNegativeButton("Hủy & Xóa file", (d, w) -> {
                    // 1. Reset dữ liệu Thumbnail
                    thumbnailUri = null;
                    ivThumbnailPreview.setImageResource(android.R.color.transparent); // Xóa hình trên giao diện
                    // Nếu bạn có icon mặc định thì dùng: ivThumbnailPreview.setImageResource(R.drawable.ic_add_photo);

                    // 2. Nếu AI phát hiện con mèo TRONG VIDEO (khi onSuccessAction != null)
                    // thì chúng ta cũng xóa luôn video đã chọn để bắt user chọn video khác
                    if (onSuccessAction != null) {
                        videoUri = null;
                        tvSelectedVideoName.setText("No video selected");
                    }

                    // 3. Tắt trạng thái loading (nếu đang bật)
                    setUploadingState(false);

                    Toast.makeText(this, "Đã hủy và xóa file vi phạm.", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void uploadThumbnailAndThenVideo(String title, String description) {
        uploadToCloudinary(thumbnailUri, "image", new UploadCallback() {
            @Override
            public void onSuccess(String requestId, Map resultData) {
                uploadVideo(title, description, resultData.get("secure_url").toString());
            }
            @Override public void onError(String requestId, ErrorInfo error) { showUploadError(new Exception(error.getDescription())); }
            @Override public void onStart(String requestId) {}
            @Override public void onProgress(String requestId, long b, long t) {}
            @Override public void onReschedule(String requestId, ErrorInfo e) {}
        });
    }

    private void uploadVideo(String title, String description, String thumbnailUrl) {
        uploadToCloudinary(videoUri, "video", new UploadCallback() {
            @Override
            public void onSuccess(String requestId, Map resultData) {
                long duration = 0;
                if (resultData.containsKey("duration")) {
                    duration = (long) (Double.parseDouble(resultData.get("duration").toString()) * 1000);
                }
                saveVideoInfoToFirestore(title, description, thumbnailUrl, resultData.get("secure_url").toString(), duration);
            }
            @Override public void onError(String requestId, ErrorInfo error) { showUploadError(new Exception(error.getDescription())); }
            @Override public void onStart(String requestId) {}
            @Override public void onProgress(String requestId, long b, long t) {}
            @Override public void onReschedule(String requestId, ErrorInfo e) {}
        });
    }

    private void uploadToCloudinary(Uri uri, String type, UploadCallback callback) {
        if (uri == null) return;
        MediaManager.get().upload(uri).unsigned(CLOUDINARY_UPLOAD_PRESET).option("resource_type", type).callback(callback).dispatch();
    }

    private void saveVideoInfoToFirestore(String title, String description, String thumb, String url, long duration) {
        String uploaderId = mAuth.getCurrentUser().getUid();
        String videoId = firestore.collection("videos").document().getId();
        Video video = new Video();
        video.setVideoID(videoId);
        video.setUploaderID(uploaderId);
        video.setTitle(title);
        video.setDescription(description);
        video.setThumbnailURL(thumb);
        video.setVideoURL(url);
        video.setDuration(duration);
        video.setVisibility(currentVisibility);

        firestore.collection("videos").document(videoId).set(video)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Upload successful!", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void setUploadingState(boolean isUploading) {
        progressBar.setVisibility(isUploading ? View.VISIBLE : View.GONE);
        btnUploadVideo.setEnabled(!isUploading);
    }

    private void showUploadError(Exception e) {
        setUploadingState(false);
        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }

    private String getFileNameFromUri(Uri uri) {
        if (uri == null) return "Unknown file";
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    // SỬA TẠI ĐÂY: Lưu index vào biến và kiểm tra >= 0
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            }
        }
        return result != null ? result : uri.getPath();
    }

    private Uri extractFirstFrameAsJpeg(Uri videoUri) throws Exception {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(this, videoUri);
        Bitmap frame = retriever.getFrameAtTime(0);
        retriever.release();
        File cacheFile = new File(getCacheDir(), "thumb_" + UUID.randomUUID().toString() + ".jpg");
        try (FileOutputStream fos = new FileOutputStream(cacheFile)) { frame.compress(Bitmap.CompressFormat.JPEG, 80, fos); }
        return Uri.fromFile(cacheFile);
    }

    private void setupTopicInput() {
        acAddTopic.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, new String[]{"Gaming", "Music", "VLOG"}));
    }
}