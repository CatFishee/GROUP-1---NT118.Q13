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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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

    // UI
    private ImageView ivThumbnailPreview;
    private Button btnSelectThumbnail, btnSelectVideo, btnUploadVideo;
    private TextView tvSelectedVideoName;
    private EditText etVideoTitle, etVideoDescription;
    private ProgressBar progressBar;

    // URIs
    private Uri thumbnailUri;
    private Uri videoUri;
    private ChipGroup chipGroupTopics;
    private AutoCompleteTextView acAddTopic;

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore firestore;
    private DatabaseReference realtimeDbRef;

    // Activity result launchers
    private final ActivityResultLauncher<Intent> thumbnailPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    thumbnailUri = result.getData().getData();
                    Glide.with(this).load(thumbnailUri).into(ivThumbnailPreview);
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

        // initialize UI
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
        setupTopicInput();
        ImageView ivClose = findViewById(R.id.iv_close_upload);
        ivClose.setOnClickListener(v -> {
            // Khi nhấn nút "X", kết thúc (đóng) Activity này và quay về màn hình trước đó
            finish();
        });

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        realtimeDbRef = FirebaseDatabase.getInstance().getReference("videoStats");

        btnSelectThumbnail.setOnClickListener(v -> openPicker("image/*", thumbnailPickerLauncher));
        btnSelectVideo.setOnClickListener(v -> openPicker("video/*", videoPickerLauncher));
        btnUploadVideo.setOnClickListener(v -> uploadFiles());
    }

    private void openPicker(String type, ActivityResultLauncher<Intent> launcher) {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType(type);
        if ("image/*".equals(type)) {
            thumbnailPickerLauncher.launch(intent);
        } else {
            videoPickerLauncher.launch(intent);
        }
    }

    private void uploadFiles() {
        String title = etVideoTitle.getText() != null ? etVideoTitle.getText().toString().trim() : "";
        String description = etVideoDescription.getText() != null ? etVideoDescription.getText().toString().trim() : "";

        if (videoUri == null || title.isEmpty()) {
            Toast.makeText(this, "Please select a video and enter a title.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "You must be logged in to upload.", Toast.LENGTH_SHORT).show();
            return;
        }

        setUploadingState(true);

        // If thumbnail not selected, extract first frame (may throw — handled here)
        if (thumbnailUri == null) {
            try {
                thumbnailUri = extractFirstFrameAsJpeg(videoUri);
                if (thumbnailUri != null) Glide.with(this).load(thumbnailUri).into(ivThumbnailPreview);
            } catch (Exception e) {
                Log.w(TAG, "Failed to extract thumbnail from video: " + e.getMessage(), e);
                thumbnailUri = null; // proceed without thumbnail
            }
        }
        uploadThumbnailAndThenVideo(title, description);
    }

    private void uploadThumbnailAndThenVideo(String title, String description) {
        if (thumbnailUri != null) {
            // Trường hợp có thumbnail
            uploadToCloudinary(thumbnailUri, "image", new com.cloudinary.android.callback.UploadCallback() {
                @Override
                public void onSuccess(String requestId, Map resultData) {
                    String thumbnailUrl = resultData.get("secure_url").toString();
                    Log.d(TAG, "Thumbnail uploaded: " + thumbnailUrl);
                    uploadVideo(title, description, thumbnailUrl);
                }
                @Override
                public void onError(String requestId, ErrorInfo error) {
                    showUploadError(new Exception("Thumbnail upload failed: " + error.getDescription()));
                }
                @Override public void onStart(String requestId) {}
                @Override public void onProgress(String requestId, long bytes, long totalBytes) {}
                @Override public void onReschedule(String requestId, ErrorInfo error) {}
            });
        } else {
            // Trường hợp không có thumbnail
            uploadVideo(title, description, ""); // Thumbnail URL là rỗng
        }
    }

    private void uploadVideo(String title, String description, String thumbnailUrl) {
        uploadToCloudinary(videoUri, "video", new UploadCallback() {
            @Override
            public void onSuccess(String requestId, Map resultData) {
                String videoUrl = resultData.get("secure_url").toString();
                Log.d(TAG, "Video uploaded: " + videoUrl);
                long durationMillis = 0;
                if (resultData.containsKey("duration")) {
                    Object durationObj = resultData.get("duration");
                    if (durationObj instanceof Double) {
                        // Đổi từ Giây sang Mili-giây để khớp với TimeUtil
                        durationMillis = (long) ((Double) durationObj * 1000);
                    } else if (durationObj instanceof Integer) {
                        durationMillis = ((Integer) durationObj) * 1000L;
                    }
                }
                saveVideoInfoToFirestore(title, description, thumbnailUrl, videoUrl, durationMillis);
            }
            @Override
            public void onError(String requestId, ErrorInfo error) {
                showUploadError(new Exception("Video upload failed: " + error.getDescription()));
            }
            @Override public void onStart(String requestId) {}
            @Override public void onProgress(String requestId, long bytes, long totalBytes) {}
            @Override public void onReschedule(String requestId, ErrorInfo error) {}
        });
    }

    private void uploadToCloudinary(Uri fileUri, String resourceType, UploadCallback callback) {
        MediaManager.get().upload(fileUri)
                .unsigned(CLOUDINARY_UPLOAD_PRESET)
                .option("resource_type", resourceType)
                .callback(callback)
                .dispatch();
    }
    private void setupTopicInput() {
        String[] suggestedTopics = new String[]{"Gaming", "Music", "VLOG", "Education", "Sports"};

        // Tạo adapter cho AutoCompleteTextView
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, suggestedTopics);
        acAddTopic.setAdapter(adapter);

        // Xử lý khi người dùng nhấn Enter hoặc nút Done
        acAddTopic.setOnEditorActionListener((v, actionId, event) -> {
            boolean handled = false;
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {

                String topic = acAddTopic.getText().toString().trim();

                String sanitizedTopic = topic.replaceAll("[^a-zA-Z0-9 ]", "");

                if (!sanitizedTopic.isEmpty()) {
                    addTopicChip(sanitizedTopic);
                    acAddTopic.setText("");
                }
                handled = true;
            }
            return handled;
        });
    }

    private void addTopicChip(String topicText) {
        Chip chip = new Chip(this);
        chip.setText(topicText);
        chip.setCloseIconVisible(true); // Hiển thị nút 'x' để xóa
        chip.setOnCloseIconClickListener(v -> chipGroupTopics.removeView(chip));
        chipGroupTopics.addView(chip);
    }
    private void saveVideoInfoToFirestore(String title, String description, String thumbnailUrl, String videoUrl, long duration) {
        String uploaderId = mAuth.getCurrentUser().getUid();
        String videoId = firestore.collection("videos").document().getId();

        List<String> topics = new ArrayList<>();
        for (int i = 0; i < chipGroupTopics.getChildCount(); i++) {
            Chip chip = (Chip) chipGroupTopics.getChildAt(i);
            topics.add(chip.getText().toString());
        }

        // Tạo danh sách từ khóa tìm kiếm
        String topicString = String.join(" ", topics);
        String searchableContent = title.toLowerCase() + " " + description.toLowerCase();
        HashSet<String> keywords = new HashSet<>(Arrays.asList(searchableContent.split("\\s+")));

        Video video = new Video();
        video.setVideoID(videoId);
        video.setUploaderID(uploaderId);
        video.setTitle(title);
        video.setDescription(description);
        video.setTopics(topics);
        video.setThumbnailURL(thumbnailUrl);
        video.setVideoURL(videoUrl);
        video.setSearchKeywords(new ArrayList<>(keywords));
        // createdAt sẽ được tự động thêm bởi @ServerTimestamp

        video.setDuration(duration);

        firestore.collection("videos").document(videoId).set(video)
                .addOnSuccessListener(aVoid -> {
                    createVideoStatInRealtimeDB(videoId);
                    Toast.makeText(UploadActivity.this, "Upload successful!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(this::showUploadError);

        firestore.collection("videos").document(videoId).set(video)
                .addOnSuccessListener(aVoid -> {
                    createVideoStatInRealtimeDB(videoId);

                    Toast.makeText(UploadActivity.this, "Upload successful!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(this::showUploadError);
    }

    private void createVideoStatInRealtimeDB(String videoId) {
        DatabaseReference statRef = realtimeDbRef.child(videoId);
        statRef.child("viewCount").setValue(0);
        statRef.child("likeCount").setValue(0);
        statRef.child("dislikeCount").setValue(0);
        statRef.child("createdAt").setValue(System.currentTimeMillis())
                .addOnSuccessListener(unused ->
                        Log.d(TAG, "RealtimeDB VideoStat created for " + videoId))
                .addOnFailureListener(e ->
                        Log.e(TAG, "Failed to create VideoStat in RealtimeDB for " + videoId, e));
    }

    private void setUploadingState(boolean isUploading) {
        progressBar.setVisibility(isUploading ? View.VISIBLE : View.GONE);
        btnUploadVideo.setEnabled(!isUploading);
        btnSelectThumbnail.setEnabled(!isUploading);
        btnSelectVideo.setEnabled(!isUploading);
    }

    private void showUploadError(Exception e) {
        setUploadingState(false);
        Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        Log.e(TAG, "Upload failed", e);
    }

    private String getFileExtensionFromUri(Uri uri) {
        String extension = "";
        try {
            String type = getContentResolver().getType(uri);
            if (type != null) {
                String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(type);
                if (ext != null) extension = "." + ext;
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not get extension: " + e.getMessage());
        }
        return extension;
    }

    private String getFileNameFromUri(Uri uri) {
        if (uri == null) {
            return "Unknown file";
        }
        String result = null;
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    result = cursor.getString(nameIndex);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to get file name from content URI", e);
            }
        }
        if (result == null) {
            result = uri.getPath();
            if (result != null) {
                int cut = result.lastIndexOf('/');
                if (cut != -1) {
                    result = result.substring(cut + 1);
                }
            }
        }
        return result != null ? result : "Unknown file";
    }

    private Uri extractFirstFrameAsJpeg(Uri videoUri) throws Exception {
        if (videoUri == null) throw new IllegalArgumentException("videoUri == null");
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(this, videoUri);
        Bitmap frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST);
        retriever.release();
        if (frame == null) throw new Exception("Could not extract frame from video");

        File cacheFile = new File(getCacheDir(), "thumb_" + UUID.randomUUID().toString() + ".jpg");
        try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
            frame.compress(Bitmap.CompressFormat.JPEG, 80, fos);
            fos.flush();
        }
        return Uri.fromFile(cacheFile);
    }

}
