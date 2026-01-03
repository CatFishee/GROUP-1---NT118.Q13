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
    public static final String PREFS_SETTINGS = "settings_prefs";
    public static final String KEY_PLAYBACK_MODE = "playback_feeds_mode";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_general_settings);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        refreshUI();
        // Khi click vào Appearance
        findViewById(R.id.item_appearance).setOnClickListener(v -> showAppearanceDialog());

        findViewById(R.id.item_playback_feeds).setOnClickListener(v -> showPlaybackDialog());

    }
    private void refreshUI() {
        SharedPreferences prefs = getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE);

        // 1. Xử lý hiển thị cho Playback in feeds
        int playbackMode = prefs.getInt(KEY_PLAYBACK_MODE, 0);
        String[] playbackOptions = {"Always on", "Wi-Fi only", "Off"};
        updateItemDisplay(R.id.item_playback_feeds, "Playback in feeds", playbackOptions[playbackMode]);

        // 2. Xử lý hiển thị cho Appearance (Thêm cái này để người dùng biết mình đang chọn theme gì)
        int themeMode = prefs.getInt(KEY_THEME, 0);
        String[] themeOptions = {"Use device theme", "Light theme", "Dark theme"};
        updateItemDisplay(R.id.item_appearance, "Appearance", themeOptions[themeMode]);
    }
    // Hàm helper để gán Title và Subtitle cho một dòng setting
    private void updateItemDisplay(int viewId, String title, String subtitle) {
        View layout = findViewById(viewId);
        if (layout != null) {
            ((TextView) layout.findViewById(R.id.tv_title)).setText(title);
            ((TextView) layout.findViewById(R.id.tv_subtitle)).setText(subtitle);
        }
    }
    private void showPlaybackDialog() {
        String[] options = {"Always on", "Wi-Fi only", "Off"};
        SharedPreferences prefs = getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE);
        int checkedItem = prefs.getInt(KEY_PLAYBACK_MODE, 0);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Playback in feeds");

        builder.setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
            // Lưu vào máy
            prefs.edit().putInt(KEY_PLAYBACK_MODE, which).apply();

            // Cập nhật lại chữ trên màn hình Settings
            refreshUI();

            dialog.dismiss();
        });

        builder.setNegativeButton("CANCEL", null);
        builder.show();
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