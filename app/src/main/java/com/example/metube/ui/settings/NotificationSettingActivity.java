package com.example.metube.ui.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import com.example.metube.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class NotificationSettingActivity extends AppCompatActivity {

    private static final String PREF_NAME = "notification_settings";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_settings);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // Thiết lập từng mục theo yêu cầu của bạn
        setupToggleItem(R.id.item_subscriptions, "Subscriptions",
                "Notify me about activity from the channels I'm subscribed to", "key_sub");

        setupToggleItem(R.id.item_activity_channel, "Activity on my channel",
                "Notify me about comments and other activity on my channel or videos", "key_channel");

        setupToggleItem(R.id.item_activity_comments, "Activity on my comments",
                "Notify me about replies, likes, and other activity on my comments", "key_comments");

        setupToggleItem(R.id.item_mentions, "Mentions",
                "Notify me when others mention my channel", "key_mentions");
    }

    private void setupToggleItem(int viewId, String title, String description, String prefKey) {
        View layout = findViewById(viewId);
        TextView tvTitle = layout.findViewById(R.id.tv_title);
        TextView tvDesc = layout.findViewById(R.id.tv_description);
        SwitchCompat switchBtn = layout.findViewById(R.id.switch_notification);

        tvTitle.setText(title);
        tvDesc.setText(description);

        // Lấy trạng thái đã lưu (mặc định là true)
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        switchBtn.setChecked(prefs.getBoolean(prefKey, true));

        // Lưu trạng thái khi người dùng thay đổi
        switchBtn.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // 1. Lưu local
            prefs.edit().putBoolean(prefKey, isChecked).apply();

            // 2. Cập nhật lên Firestore
            updatePreferenceOnFirestore(prefKey, isChecked);
        });

        // Cho phép click vào toàn bộ dòng để đổi trạng thái switch
        layout.setOnClickListener(v -> switchBtn.setChecked(!switchBtn.isChecked()));
    }
    private void updatePreferenceOnFirestore(String prefKey, boolean isChecked) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        // Lưu vào một map object bên trong document User
        FirebaseFirestore.getInstance().collection("users").document(uid)
                .update("notificationSettings." + prefKey, isChecked)
                .addOnFailureListener(e -> Log.e("NotifySettings", "Failed to sync settings"));
    }
}