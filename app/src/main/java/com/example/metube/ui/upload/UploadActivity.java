package com.example.metube.ui.upload;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.metube.R;
import com.example.metube.model.Video;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.UUID;

public class UploadActivity extends AppCompatActivity {

    private static final String TAG = "UploadActivity";

    // UI elements
    private ImageView ivThumbnailPreview;
    private Button btnSelectThumbnail, btnSelectVideo, btnUploadVideo;
    private TextView tvSelectedVideoName;
    private EditText etVideoTitle, etVideoDescription;
    private ProgressBar progressBar;

    private Uri thumbnailUri;
    private Uri videoUri;

    // Firebase
    private StorageReference storageReference;
    private FirebaseFirestore firestore;
    private FirebaseAuth mAuth;

    // Activity Result Launchers
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
                    tvSelectedVideoName.setText("Video selected");
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);

        // Initialize Firebase
        storageReference = FirebaseStorage.getInstance().getReference();
        firestore = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        // Initialize UI
        ivThumbnailPreview = findViewById(R.id.iv_thumbnail_preview);
        btnSelectThumbnail = findViewById(R.id.btn_select_thumbnail);
        btnSelectVideo = findViewById(R.id.btn_select_video);
        btnUploadVideo = findViewById(R.id.btn_upload_video);
        tvSelectedVideoName = findViewById(R.id.tv_selected_video_name);
        etVideoTitle = findViewById(R.id.et_video_title);
        etVideoDescription = findViewById(R.id.et_video_description);
        progressBar = findViewById(R.id.progress_bar_upload);

        // Set click listeners
        btnSelectThumbnail.setOnClickListener(v -> openPicker("image/*", thumbnailPickerLauncher));
        btnSelectVideo.setOnClickListener(v -> openPicker("video/*", videoPickerLauncher));
        btnUploadVideo.setOnClickListener(v -> uploadFiles());
    }

    private void openPicker(String type, ActivityResultLauncher<Intent> launcher) {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType(type);
        launcher.launch(intent);
    }

    private void uploadFiles() {
        String title = etVideoTitle.getText().toString().trim();
        String description = etVideoDescription.getText().toString().trim();

        if (thumbnailUri == null || videoUri == null || title.isEmpty()) {
            Toast.makeText(this, "Please select thumbnail, video, and enter a title.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "You must be logged in to upload.", Toast.LENGTH_SHORT).show();
            return;
        }
        setUploadingState(true);

        String thumbnailFileName = "thumbnails/" + UUID.randomUUID().toString();
        StorageReference thumbnailRef = storageReference.child(thumbnailFileName);

        thumbnailRef.putFile(thumbnailUri).continueWithTask(task -> {
            if (!task.isSuccessful()) throw task.getException();
            return thumbnailRef.getDownloadUrl();
        }).continueWithTask(task -> {
            Uri thumbnailDownloadUrl = task.getResult();
            if (thumbnailDownloadUrl == null) throw new Exception("Could not get thumbnail download URL");

            String videoFileName = "videos/" + UUID.randomUUID().toString();
            StorageReference videoRef = storageReference.child(videoFileName);

            return videoRef.putFile(videoUri).continueWithTask(videoTask -> {
                if (!videoTask.isSuccessful()) throw videoTask.getException();
                return videoRef.getDownloadUrl();
            }).addOnSuccessListener(videoDownloadUrl -> {
                saveVideoInfoToFirestore(title, description, thumbnailDownloadUrl.toString(), videoDownloadUrl.toString());
            });
        }).addOnFailureListener(this::showUploadError);
    }

    private void saveVideoInfoToFirestore(String title, String description, String thumbnailUrl, String videoUrl) {
        String uploaderId = mAuth.getCurrentUser().getUid();
        String videoId = firestore.collection("videos").document().getId();

        Video video = new Video();
        video.setVideoID(videoId);
        video.setUploaderID(uploaderId);
        video.setTitle(title);
        video.setDescription(description);
        video.setTopics(new ArrayList<>());
        video.setThumbnailURL(thumbnailUrl);
        video.setVideoURL(videoUrl);
        // createdAt will be set by @ServerTimestamp if you have it in your model
        firestore.collection("videos").document(videoId).set(video)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(UploadActivity.this, "Upload successful!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(this::showUploadError);
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
}