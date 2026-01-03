package com.example.metube.ui.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.example.metube.R;

public class GeneralSettingsActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "theme_prefs";
    private static final String KEY_THEME = "selected_theme";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_general_settings);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // 1. Setup Appearance
        setupGeneralItem(R.id.item_appearance,
                "Appearance",
                "Choose your light or dark theme preference");

        // 2. Setup Playback in feeds
        setupGeneralItem(R.id.item_playback_feeds,
                "Playback in feeds",
                "Choose whether videos play as you browse");

        // Clicks
        // Khi click vào Appearance
        findViewById(R.id.item_appearance).setOnClickListener(v -> showAppearanceDialog());

        findViewById(R.id.item_playback_feeds).setOnClickListener(v -> {
            // Logic bật tắt tự động phát video ngoài trang chủ
            Toast.makeText(this, "Playback in feeds settings", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupGeneralItem(int viewId, String title, String subtitle) {
        View layout = findViewById(viewId);
        TextView tvTitle = layout.findViewById(R.id.tv_title);
        TextView tvSubtitle = layout.findViewById(R.id.tv_subtitle);

        tvTitle.setText(title);
        tvSubtitle.setText(subtitle);
    }
    private void showAppearanceDialog() {
        // 1. Danh sách các lựa chọn
        String[] options = {"Use device theme", "Light theme", "Dark theme"};

        // 2. Lấy vị trí đã chọn trước đó (mặc định là 0 - Use device theme)
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int checkedItem = prefs.getInt(KEY_THEME, 0);

        // 3. Khởi tạo AlertDialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Appearance");

        // Thiết lập danh sách chọn (Radio Buttons)
        builder.setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
            // Lưu lựa chọn vào SharedPreferences
            prefs.edit().putInt(KEY_THEME, which).apply();

            // Áp dụng theme ngay lập tức
            applyAppTheme(which);

            // Đóng dialog sau khi chọn
            dialog.dismiss();
        });

        // Nút Cancel
        builder.setNegativeButton("CANCEL", (dialog, which) -> dialog.dismiss());

        // Hiển thị Dialog
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void applyAppTheme(int themeOption) {
        switch (themeOption) {
            case 0: // Use device theme
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
            case 1: // Light theme
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case 2: // Dark theme
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
        }
        // Lưu ý: Hàm này sẽ tự động khởi động lại Activity để áp dụng giao diện mới
    }

}