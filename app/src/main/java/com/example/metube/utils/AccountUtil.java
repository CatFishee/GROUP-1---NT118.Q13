package com.example.metube.utils;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.metube.model.User;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class AccountUtil {
    private static final String PREF_NAME = "MeTubeAccounts";
    private static final String KEY_ACCOUNTS = "saved_accounts";

    // Lưu thông tin User vào danh sách lịch sử
    public static void saveUserToHistory(Context context, User user) {
        if (user == null) return;

        List<User> savedAccounts = getSavedAccounts(context);

        // Kiểm tra xem user này đã có trong list chưa (tránh trùng lặp)
        int index = -1;
        for (int i = 0; i < savedAccounts.size(); i++) {
            if (savedAccounts.get(i).getUserID().equals(user.getUserID())) {
                index = i;
                break;
            }
        }

        if (index != -1) {
            // Nếu đã có, cập nhật lại thông tin mới nhất (ví dụ đổi avatar)
            savedAccounts.set(index, user);
        } else {
            // Nếu chưa có, thêm vào mới
            savedAccounts.add(user);
        }

        saveListToPrefs(context, savedAccounts);
    }

    // Lấy danh sách tài khoản đã lưu
    public static List<User> getSavedAccounts(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_ACCOUNTS, null);

        if (json == null) return new ArrayList<>();

        Gson gson = new Gson();
        Type type = new TypeToken<ArrayList<User>>() {}.getType();
        return gson.fromJson(json, type);
    }

    // Xóa tài khoản khỏi danh sách (Dùng cho nút Manage Accounts sau này nếu cần)
    public static void removeAccount(Context context, String userId) {
        List<User> savedAccounts = getSavedAccounts(context);
        savedAccounts.removeIf(user -> user.getUserID().equals(userId));
        saveListToPrefs(context, savedAccounts);
    }

    // Helper: Ghi list xuống bộ nhớ
    private static void saveListToPrefs(Context context, List<User> list) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        Gson gson = new Gson();
        String json = gson.toJson(list);
        editor.putString(KEY_ACCOUNTS, json);
        editor.apply();
    }
}