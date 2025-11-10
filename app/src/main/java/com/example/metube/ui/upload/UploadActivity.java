package com.example.metube.ui.upload;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Log;
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
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.HashSet;

import okhttp3.Call;
//import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

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

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore firestore;

    // Networking
//    private OkHttpClient httpClient;
//    private final ExecutorService executor = Executors.newSingleThreadExecutor();
//    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
//        httpClient = new OkHttpClient();

        btnSelectThumbnail.setOnClickListener(v -> openPicker("image/*", thumbnailPickerLauncher));
        btnSelectVideo.setOnClickListener(v -> openPicker("video/*", videoPickerLauncher));
        btnUploadVideo.setOnClickListener(v -> uploadFiles());
    }

    private void openPicker(String type, ActivityResultLauncher<Intent> launcher) {
//        Intent intent = new Intent(Intent.ACTION_PICK);
//        intent.setType(type);
//        launcher.launch(intent);
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

//        final String uploaderId = mAuth.getCurrentUser().getUid();
//        final String videoId = firestore.collection("videos").document().getId();
//
//        final String videosBucket = getString(R.string.supabase_bucket_videos);
//        final String thumbsBucket = getString(R.string.supabase_bucket_thumbnails);
//        final String supabaseUrl = getString(R.string.supabase_project_url);
//        final String anonKey = getString(R.string.supabase_anon_key);
//
//        final String videoPath = "videos/" + UUID.randomUUID().toString() + getFileExtensionFromUri(videoUri);
//        final String thumbPath = (thumbnailUri != null) ? ("thumbnails/" + UUID.randomUUID().toString() + ".jpg") : null;
//
//        // Upload thumbnail (if present) then video; otherwise only video
//        if (thumbnailUri != null) {
//            uploadUriToSupabase(thumbsBucket, thumbPath, thumbnailUri, supabaseUrl, anonKey, (thumbPublicUrl, thumbErr) -> {
//                if (thumbErr != null) {
//                    showUploadError(new Exception(thumbErr));
//                    return;
//                }
//                uploadUriToSupabase(videosBucket, videoPath, videoUri, supabaseUrl, anonKey, (videoPublicUrl, videoErr) -> {
//                    if (videoErr != null) {
//                        showUploadError(new Exception(videoErr));
//                        return;
//                    }
//                    saveVideoInfoToFirestore(videoId, uploaderId, title, description, thumbPublicUrl, videoPublicUrl);
//                });
//            });
//        } else {
//            uploadUriToSupabase(videosBucket, videoPath, videoUri, supabaseUrl, anonKey, (videoPublicUrl, videoErr) -> {
//                if (videoErr != null) {
//                    showUploadError(new Exception(videoErr));
//                    return;
//                }
//                saveVideoInfoToFirestore(videoId, uploaderId, title, description, "", videoPublicUrl);
//            });
//        }
//    }
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
                saveVideoInfoToFirestore(title, description, thumbnailUrl, videoUrl);
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
                .callback((com.cloudinary.android.callback.UploadCallback) callback)
                .dispatch();
    }

//    private void saveVideoInfoToFirestore(String videoId, String uploaderId, String title, String description, String thumbnailUrl, String videoUrl) {
//        Video video = new Video();
//        video.setVideoID(videoId);
//        video.setUploaderID(uploaderId);
//        video.setTitle(title);
//        video.setDescription(description);
//        video.setTopics(new ArrayList<>());
//        video.setThumbnailURL(thumbnailUrl);
//        video.setVideoURL(videoUrl);
//
//        firestore.collection("videos").document(videoId)
//                .set(video)
//                .addOnSuccessListener(aVoid -> {
//                    mainHandler.post(() -> {
//                        setUploadingState(false);
//                        Toast.makeText(UploadActivity.this, "Upload successful!", Toast.LENGTH_SHORT).show();
//                        finish();
//                    });
//                })
//                .addOnFailureListener(e -> showUploadError(e));
//    }
private void saveVideoInfoToFirestore(String title, String description, String thumbnailUrl, String videoUrl) {
    String uploaderId = mAuth.getCurrentUser().getUid();
    String videoId = firestore.collection("videos").document().getId();

    // Tạo danh sách từ khóa tìm kiếm (giữ nguyên)
    String searchableContent = title.toLowerCase() + " " + description.toLowerCase();
    HashSet<String> keywords = new HashSet<>(Arrays.asList(searchableContent.split("\\s+")));

    Video video = new Video();
    video.setVideoID(videoId);
    video.setUploaderID(uploaderId);
    video.setTitle(title);
    video.setDescription(description);
    video.setTopics(new ArrayList<>());
    video.setThumbnailURL(thumbnailUrl);
    video.setVideoURL(videoUrl);
    video.setSearchKeywords(new ArrayList<>(keywords));
    // createdAt sẽ được tự động thêm bởi @ServerTimestamp

    firestore.collection("videos").document(videoId).set(video)
            .addOnSuccessListener(aVoid -> {
                Toast.makeText(UploadActivity.this, "Upload successful!", Toast.LENGTH_SHORT).show();
                finish();
            })
            .addOnFailureListener(this::showUploadError);
}

    private void setUploadingState(boolean isUploading) {
//        mainHandler.post(() -> {
            progressBar.setVisibility(isUploading ? View.VISIBLE : View.GONE);
            btnUploadVideo.setEnabled(!isUploading);
            btnSelectThumbnail.setEnabled(!isUploading);
            btnSelectVideo.setEnabled(!isUploading);
//        });
    }

    private void showUploadError(Exception e) {
//        Log.e(TAG, "Upload failed", e);
//        setUploadingState(false);
//        mainHandler.post(() -> Toast.makeText(UploadActivity.this, "Upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
        setUploadingState(false);
        Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        Log.e(TAG, "Upload failed", e);
    }

    /**
     * Upload a Uri's bytes to Supabase Storage using OkHttp.
     * This method does not throw checked exceptions; it reports errors via callback.
     *
     * On success: callback receives the public URL (assuming public bucket).
     * On failure: callback receives null and an error message.
     */
//    private void uploadUriToSupabase(String bucket,
//                                     String pathInBucket,
//                                     Uri fileUri,
//                                     String supabaseUrl,
//                                     String anonKey,
//                                     UploadCallback callback) {
//        if (fileUri == null) {
//            callback.onComplete(null, "fileUri == null");
//            return;
//        }
//        final String endpoint = String.format(Locale.US, "%s/storage/v1/object/%s/%s", supabaseUrl, bucket, pathInBucket);
//
//        String mime = getContentResolver().getType(fileUri);
//        if (mime == null) mime = "application/octet-stream";
//        final MediaType mediaType = MediaType.parse(mime);
//
//        // Read bytes off main thread
//        String finalMime = mime;
//        executor.execute(() -> {
//            try (InputStream in = getContentResolver().openInputStream(fileUri);
//                 BufferedInputStream bis = (in != null) ? new BufferedInputStream(in) : null;
//                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
//
//                if (bis == null) {
//                    mainHandler.post(() -> callback.onComplete(null, "Could not open input stream for upload"));
//                    return;
//                }
//
//                byte[] buffer = new byte[8192];
//                int read;
//                while ((read = bis.read(buffer)) != -1) {
//                    baos.write(buffer, 0, read);
//                }
//                byte[] bytes = baos.toByteArray();
//
//                RequestBody body = RequestBody.create(bytes, mediaType);
//
//                Request request = new Request.Builder()
//                        .url(endpoint)
//                        .header("Authorization", "Bearer " + anonKey)
//                        .header("Content-Type", finalMime)
//                        .header("x-upsert", "true")
//                        .post(body) // Supabase accepts POST for uploading object content
//                        .build();
//
//                httpClient.newCall(request).enqueue(new Callback() {
//                    @Override
//                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
//                        mainHandler.post(() -> callback.onComplete(null, e.getMessage()));
//                    }
//
//                    @Override
//                    public void onResponse(@NonNull Call call, @NonNull Response response) {
//                        try {
//                            if (!response.isSuccessful()) {
//                                String bodyStr = "";
//                                try {
//                                    bodyStr = response.body() != null ? response.body().string() : "";
//                                } catch (IOException ioe) {
//                                    // ignore reading body error
//                                }
//                                final String msg = "Upload failed: " + response.code() + " " + response.message() + " " + bodyStr;
//                                Log.e(TAG, msg);
//                                mainHandler.post(() -> callback.onComplete(null, msg));
//                                return;
//                            }
//                            final String publicUrl = String.format(Locale.US, "%s/storage/v1/object/public/%s/%s", supabaseUrl, bucket, pathInBucket);
//                            mainHandler.post(() -> callback.onComplete(publicUrl, null));
//                        } finally {
//                            response.close();
//                        }
//                    }
//                });
//
//            } catch (Exception e) {
//                Log.e(TAG, "Reading file failed", e);
//                mainHandler.post(() -> callback.onComplete(null, e.getMessage()));
//            }
//        });
//    }

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
        // Kiểm tra xem đây có phải là một "địa chỉ tham chiếu" không
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            // Sử dụng ContentResolver để hỏi hệ điều hành
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    // Lấy tên file từ cột DISPLAY_NAME
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

    /**
     * Extract the first frame from videoUri and write to cache as JPEG, returning a Uri to the cached file.
     * This method runs on the calling thread; we call it before uploads and handle exceptions there.
     */
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
//        executor.shutdownNow();
    }

//    private interface UploadCallback {
//        void onComplete(String publicUrl, String error);
//    }
}
