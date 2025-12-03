package com.example.metube.ui.history;

import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.metube.R;
import com.example.metube.model.DateHeader;
import com.example.metube.model.HistoryItem;
import com.example.metube.model.User;
import com.example.metube.model.Video;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class HistoryActivity extends AppCompatActivity {

    private static final String TAG = "HistoryActivity";

    private RecyclerView rvHistory;
    private ProgressBar progressBar;
    private HistoryAdapter adapter;
    private List<Object> uiList = new ArrayList<>();
    private FirebaseFirestore firestore;
    private String currentUserId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        rvHistory = findViewById(R.id.rv_history_videos);
//        progressBar = findViewById(R.id.progress_bar); // Thêm ProgressBar vào layout của bạn nếu muốn

        firestore = FirebaseFirestore.getInstance();
        currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        setupRecyclerView();
        loadHistoryData();
    }

    private void setupRecyclerView() {
        adapter = new HistoryAdapter(uiList);
        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        rvHistory.setAdapter(adapter);
    }

    private void loadHistoryData() {
        if (currentUserId == null) {
            Toast.makeText(this, "Please sign in to view history", Toast.LENGTH_SHORT).show();
            return;
        }
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        // BƯỚC 1: Lấy danh sách lịch sử xem (chứa videoId và watchedAt)
        firestore.collection("watchHistory")
                .whereEqualTo("userID", currentUserId)
                .orderBy("watchedAt", Query.Direction.DESCENDING)
                .limit(100) // Giới hạn để tránh quá tải
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                        // TODO: Hiển thị trạng thái trống
                        return;
                    }

                    List<HistoryItem> historyItems = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        historyItems.add(doc.toObject(HistoryItem.class));
                    }
                    // Bắt đầu chuỗi truy vấn lồng nhau
                    fetchVideoDetails(historyItems);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting watch history", e);
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                });
    }

    private void fetchVideoDetails(List<HistoryItem> historyItems) {
        // Lấy ra danh sách các videoId duy nhất để truy vấn hàng loạt
        Set<String> videoIdsSet = new HashSet<>();
        for (HistoryItem item : historyItems) {
            if (item.getVideoID() != null) {
                videoIdsSet.add(item.getVideoID());
            }
        }
        if (videoIdsSet.isEmpty()) {
            if (progressBar != null) progressBar.setVisibility(View.GONE);
            return;
        }
        List<String> videoIds = new ArrayList<>(videoIdsSet);

        // BƯỚC 2: Dùng `whereIn` để lấy thông tin của tất cả video cần thiết trong 1 lần gọi
        firestore.collection("videos").whereIn(FieldPath.documentId(), videoIds)
                .get()
                .addOnSuccessListener(videoSnapshots -> {
                    Map<String, Video> videoMap = new HashMap<>();
                    for (QueryDocumentSnapshot doc : videoSnapshots) {
                        videoMap.put(doc.getId(), doc.toObject(Video.class));
                    }

                    // Gắn thông tin video vào danh sách history
                    for (HistoryItem item : historyItems) {
                        item.setVideo(videoMap.get(item.getVideoID()));
                    }

                    // Bước tiếp theo: Lấy thông tin người đăng
                    fetchUploaderDetails(historyItems);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error getting video details", e));
    }

    private void fetchUploaderDetails(List<HistoryItem> historyItems) {
        // Lấy ra danh sách các uploaderId duy nhất
        Set<String> uploaderIdsSet = new HashSet<>();
        for (HistoryItem item : historyItems) {
            if (item.getVideo() != null && item.getVideo().getUploaderID() != null) {
                uploaderIdsSet.add(item.getVideo().getUploaderID());
            }
        }
        if (uploaderIdsSet.isEmpty()) {
            processAndDisplayHistory(historyItems); // Vẫn xử lý dù không có uploader
            return;
        }
        List<String> uploaderIds = new ArrayList<>(uploaderIdsSet);

        // BƯỚC 3: Dùng `whereIn` để lấy thông tin của tất cả người đăng trong 1 lần gọi
        firestore.collection("users").whereIn("userID", uploaderIds)
                .get()
                .addOnSuccessListener(userSnapshots -> {
                    Map<String, User> userMap = new HashMap<>();
                    for (QueryDocumentSnapshot doc : userSnapshots) {
                        Log.d(TAG, "User Document Data: " + doc.getData());
                        User user = doc.toObject(User.class);
                        if (user.getUserID() != null) {
                            userMap.put(user.getUserID(), user);
                        } else {
                            Log.w(TAG, "Found a user document with null userID!");
                        }
                    }

                    // Gắn thông tin người đăng vào danh sách history
                    for (HistoryItem item : historyItems) {
                        if (item.getVideo() != null) {
                            item.setUploader(userMap.get(item.getVideo().getUploaderID()));
                        }
                    }

                    // BƯỚC 4: Dữ liệu đã đầy đủ, xử lý để hiển thị
                    processAndDisplayHistory(historyItems);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error getting uploader details", e));
    }

    private void processAndDisplayHistory(List<HistoryItem> historyItems) {
        uiList.clear();
        String lastDateHeader = "";

        for (HistoryItem item : historyItems) {
            // Chỉ xử lý những item có đủ dữ liệu
            if (item.getWatchedAt() == null || item.getVideo() == null) continue;

            String currentDateHeader = getFormattedDate(item.getWatchedAt().toDate().getTime());
            if (!currentDateHeader.equals(lastDateHeader)) {
                uiList.add(new DateHeader(currentDateHeader));
                lastDateHeader = currentDateHeader;
            }
            uiList.add(item);
        }

        if (progressBar != null) progressBar.setVisibility(View.GONE);
        adapter.notifyDataSetChanged();
    }

    private String getFormattedDate(long timeInMillis) {
        if (DateUtils.isToday(timeInMillis)) {
            return "Today";
        } else if (isYesterday(timeInMillis)) {
            return "Yesterday";
        } else {
            // Định dạng cho các ngày cũ hơn, ví dụ: "November 25, 2025"
            return new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(timeInMillis);
        }
    }

    private boolean isYesterday(long timeInMillis) {
        Calendar now = Calendar.getInstance();
        Calendar checkedDate = Calendar.getInstance();
        checkedDate.setTimeInMillis(timeInMillis);
        now.add(Calendar.DATE,-1);
        return (now.get(Calendar.YEAR) == checkedDate.get(Calendar.YEAR)
                && now.get(Calendar.DAY_OF_YEAR) == checkedDate.get(Calendar.DAY_OF_YEAR));
    }
}