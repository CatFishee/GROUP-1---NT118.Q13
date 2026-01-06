package com.example.metube.ui.profile;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.example.metube.R;
import com.example.metube.model.User;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

public class EditProfileActivity extends AppCompatActivity {

    private static final String TAG = "EditProfileActivity";

    private CircleImageView ivProfilePicture;
    private EditText etDisplayName, edtEmail;
    private MaterialButton btnSave, btnCancel;
    private ImageButton btnBack, btnChangePhoto;
    private ProgressBar progressBar;

    private FirebaseAuth auth;
    private FirebaseFirestore firestore;

    private User currentUser;
    private Uri selectedImageUri;

    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    Glide.with(this)
                            .load(uri)
                            .placeholder(R.drawable.ic_person)
                            .into(ivProfilePicture);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        initFirebase();
        initViews();
        loadCurrentUserData(); // Hàm này sẽ tự đổ dữ liệu vào EditText
        setupClickListeners();
    }

    private void initFirebase() {
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
    }

    private void initViews() {
        ivProfilePicture = findViewById(R.id.iv_profile_picture);
        etDisplayName = findViewById(R.id.et_display_name);
        edtEmail = findViewById(R.id.edt_email);
        btnSave = findViewById(R.id.btn_save);
        btnCancel = findViewById(R.id.btn_cancel);
        btnBack = findViewById(R.id.btn_back);
        btnChangePhoto = findViewById(R.id.btn_change_photo);
        progressBar = findViewById(R.id.progress_bar);

        // THIẾT LẬP EMAIL CHỈ ĐỌC (READ-ONLY)
        edtEmail.setEnabled(false);
        edtEmail.setFocusable(false);
        edtEmail.setAlpha(0.7f); // Làm mờ nhẹ để người dùng biết không sửa được
    }

    private void loadCurrentUserData() {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) {
            finish();
            return;
        }

        showLoading(true);

        firestore.collection("users")
                .document(firebaseUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    showLoading(false);
                    if (documentSnapshot.exists()) {
                        currentUser = documentSnapshot.toObject(User.class);
                        if (currentUser != null) {
                            etDisplayName.setText(currentUser.getName());
                            edtEmail.setText(currentUser.getEmail()); // Đổ email vào ô read-only

                            String photoUrl = currentUser.getProfileURL();
                            if (photoUrl != null && !photoUrl.isEmpty()) {
                                Glide.with(this)
                                        .load(photoUrl)
                                        .placeholder(R.drawable.ic_person)
                                        .into(ivProfilePicture);
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show();
                });
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnCancel.setOnClickListener(v -> finish());
        btnChangePhoto.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
        btnSave.setOnClickListener(v -> saveProfile());
    }

    private void saveProfile() {
        String newName = etDisplayName.getText().toString().trim();

        if (newName.isEmpty()) {
            etDisplayName.setError("Name cannot be empty");
            return;
        }

        showLoading(true);

        // ĐÃ LOẠI BỎ newBio
        if (selectedImageUri != null) {
            uploadImageToCloudinaryAndSave(newName);
        } else {
            updateProfileInFirestore(newName, currentUser.getProfileURL());
        }
    }

    /**
     * Upload ảnh lên Cloudinary và lưu (ĐÃ BỎ BIO)
     */
    private void uploadImageToCloudinaryAndSave(String newName) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) return;

        String publicId = "profile_" + firebaseUser.getUid();

        MediaManager.get().upload(selectedImageUri)
                .option("folder", "profile_pictures")
                .option("public_id", publicId)
                .option("overwrite", true)
                .option("resource_type", "image")
                .callback(new UploadCallback() {
                    @Override public void onStart(String requestId) {}
                    @Override public void onProgress(String requestId, long bytes, long totalBytes) {}

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        runOnUiThread(() -> {
                            String imageUrl = (String) resultData.get("secure_url");
                            updateProfileInFirestore(newName, imageUrl);
                        });
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        runOnUiThread(() -> {
                            showLoading(false);
                            Toast.makeText(EditProfileActivity.this, "Upload failed", Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override public void onReschedule(String requestId, ErrorInfo error) {}
                })
                .dispatch();
    }

    /**
     * Cập nhật thông tin trong Firestore (ĐÃ BỎ BIO)
     */
    private void updateProfileInFirestore(String newName, String photoUrl) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("name", newName);
        updates.put("searchKeywords", User.generateKeywords(newName));

        if (photoUrl != null && !photoUrl.isEmpty()) {
            updates.put("profileURL", photoUrl);
        }

        if (photoUrl != null && !photoUrl.isEmpty()) {
            updates.put("profileURL", photoUrl);
        }

        firestore.collection("users")
                .document(firebaseUser.getUid())
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, "Failed to update profile", Toast.LENGTH_SHORT).show();
                });
    }

    private void showLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        btnSave.setEnabled(!isLoading);
        btnChangePhoto.setEnabled(!isLoading);
    }
}