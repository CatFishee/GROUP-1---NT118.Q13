package com.example.metube.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.metube.R;
import com.example.metube.model.User;
import com.example.metube.ui.login.SwitchAccountDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class SettingsActivity extends AppCompatActivity {
    private User currentUserModel;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        setupUI();

        fetchCurrentUser();

        setClickListeners();

    }
    private void setupUI() {
        // Thiết lập từng mục
        setupItem(R.id.item_general, R.drawable.ic_settings, "General");
        setupItem(R.id.item_switch_account, R.drawable.ic_switch_account, "Switch account");
        setupItem(R.id.item_languages, R.drawable.ic_language, "Languages");
        setupItem(R.id.item_notifications, R.drawable.ic_notifications, "Notifications");
        setupItem(R.id.item_playback, R.drawable.ic_play, "Playback");
        setupItem(R.id.item_captions, R.drawable.ic_closed_caption, "Captions");
        setupItem(R.id.item_privacy, R.drawable.ic_privacy, "Privacy");
    }

    private void setupItem(int viewId, int iconRes, String title) {
        View layout = findViewById(viewId);
        if (layout != null) {
            ImageView ivIcon = layout.findViewById(R.id.iv_icon);
            TextView tvTitle = layout.findViewById(R.id.tv_title);
            ivIcon.setImageResource(iconRes);
            tvTitle.setText(title);
        }
    }
    private void fetchCurrentUser() {
        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser != null) {
            // Lấy dữ liệu chi tiết từ Firestore (Name, Email, ProfileURL...)
            db.collection("users").document(firebaseUser.getUid())
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            currentUserModel = documentSnapshot.toObject(User.class);
                        }
                    })
                    .addOnFailureListener(e -> Log.e("Settings", "Error fetching user", e));
        }
    }

    private void setClickListeners() {
        // --- QUAN TRỌNG: SWITCH ACCOUNT ---
        findViewById(R.id.item_switch_account).setOnClickListener(v -> {
            if (currentUserModel != null) {
                // Hiển thị Dialog của bạn
                SwitchAccountDialog dialog = new SwitchAccountDialog(currentUserModel);
                dialog.show(getSupportFragmentManager(), "SwitchAccountDialog");
            } else {
                Toast.makeText(this, "Loading user data, please wait...", Toast.LENGTH_SHORT).show();
            }
        });

        // Các mục khác (Bạn có thể thêm Activity/Logic tương ứng)
        findViewById(R.id.item_general).setOnClickListener(v -> {
            Intent intent = new Intent(SettingsActivity.this, GeneralSettingsActivity.class);
            startActivity(intent);
        });
        findViewById(R.id.item_languages).setOnClickListener(v -> showToast("Languages"));
        findViewById(R.id.item_notifications).setOnClickListener(v -> showToast("Notifications"));
        findViewById(R.id.item_playback).setOnClickListener(v -> showToast("Playback"));
        findViewById(R.id.item_captions).setOnClickListener(v -> showToast("Captions"));
        findViewById(R.id.item_privacy).setOnClickListener(v -> showToast("Privacy"));
    }
    private void showToast(String message) {
        Toast.makeText(this, message + " settings coming soon!", Toast.LENGTH_SHORT).show();
    }
}