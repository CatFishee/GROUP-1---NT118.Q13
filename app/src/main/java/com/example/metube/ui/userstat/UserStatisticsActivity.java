package com.example.metube.ui.userstat; // Hoặc package statistic của bạn

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.metube.R;
import com.example.metube.model.UserWatchStat;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

public class UserStatisticsActivity extends AppCompatActivity {

    private TextView tvTotalTime, tvVideoCount, tvNoTopics, tvPlaylistCount;
    private LinearLayout layoutTopicsContainer;
    private FirebaseFirestore firestore;
    private BarChart barChart;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_statistics);

        firestore = FirebaseFirestore.getInstance();

        // 1. Ánh xạ
        tvTotalTime = findViewById(R.id.tv_total_time);
        tvVideoCount = findViewById(R.id.tv_video_count);
        layoutTopicsContainer = findViewById(R.id.layout_topics_container);
        tvNoTopics = findViewById(R.id.tv_no_topics);
        barChart = findViewById(R.id.barChart);
        tvPlaylistCount = findViewById(R.id.tv_playlist_count);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // 2. Load dữ liệu
        loadStatistics();
    }

    private void loadStatistics() {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // Giả sử bạn lưu document ID trùng với UserID, hoặc bạn query theo userID
        firestore.collection("userWatchStats")
                .whereEqualTo("userID", userId)
                .limit(1)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        UserWatchStat stat = queryDocumentSnapshots.getDocuments().get(0).toObject(UserWatchStat.class);
                        if (stat != null) {
                            displayData(stat);
                        }
                    } else {
                        // Chưa có thống kê -> Hiển thị 0
                        tvTotalTime.setText("0h 0m");
                        tvVideoCount.setText("0");
                    }
                })
                .addOnFailureListener(e -> Log.e("UserStats", "Error loading stats", e));
        loadPlaylistCount(userId);
    }
    private void loadPlaylistCount(String userId) {
        firestore.collection("playlists")
                .whereEqualTo("ownerId", userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    int count = queryDocumentSnapshots.size();
                    tvPlaylistCount.setText(String.valueOf(count));
                })
                .addOnFailureListener(e -> Log.e("UserStats", "Error loading playlist count", e));
    }

    private void displayData(UserWatchStat stat) {
        // 1. Hiển thị tổng thời gian (Convert millis to "10h 30m")
        long millis = stat.getTotalWatchTime();
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        tvTotalTime.setText(String.format("%dh %dm", hours, minutes));

        // 2. Hiển thị số lượng video
        int count = (stat.getVideosWatched() != null) ? stat.getVideosWatched().size() : 0;
        tvVideoCount.setText(String.valueOf(count));

        // 3. Xử lý Topic (Đếm và tìm Top)
        processAndDisplayTopics(stat.getTopicCounts());
        setupBarChart(stat.getDailyWatchTime());
    }

    private void processAndDisplayTopics(Map<String, Long> topicCounts) {
        if (topicCounts == null || topicCounts.isEmpty()) {
            tvNoTopics.setVisibility(View.VISIBLE);
            layoutTopicsContainer.removeAllViews();
            return;
        }

        // Sắp xếp Map theo Value (Số lượng) giảm dần
        List<Map.Entry<String, Long>> sortedList = new ArrayList<>(topicCounts.entrySet());
        Collections.sort(sortedList, (o1, o2) -> o2.getValue().compareTo(o1.getValue()));

        // Hiển thị Top 5
        layoutTopicsContainer.removeAllViews();
        tvNoTopics.setVisibility(View.GONE);

        // Lấy max để tính thanh %
        long maxCount = sortedList.get(0).getValue();

        int limit = Math.min(sortedList.size(), 5);
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, Long> entry = sortedList.get(i);
            // Ép kiểu long về int cho hàm addTopicRow
            addTopicRow(entry.getKey(), entry.getValue().intValue(), (int) maxCount);
        }
    }

    // Hàm tạo giao diện dòng Topic bằng code Java (hoặc inflate layout)
    private void addTopicRow(String topic, int count, int max) {
        // Inflate layout con cho mỗi dòng topic (đơn giản gồm Tên và Progress)
        View view = LayoutInflater.from(this).inflate(R.layout.layout_item_stat_topic, layoutTopicsContainer, false);

        TextView tvName = view.findViewById(R.id.tv_topic_name);
        TextView tvCount = view.findViewById(R.id.tv_topic_count);
        ProgressBar progressBar = view.findViewById(R.id.pb_topic);

        tvName.setText(topic);
        tvCount.setText(formatViewCount(count));

        // Tính % cho progress bar
        int progress = (int) ((count / (float) max) * 100);
        progressBar.setProgress(progress);

        layoutTopicsContainer.addView(view);
    }
    private void setupBarChart(Map<String, Long> dailyData) {
        if (dailyData == null || dailyData.isEmpty()) return;

        // Dùng TreeMap để tự động sắp xếp theo Key (Ngày tháng) tăng dần
        TreeMap<String, Long> sortedMap = new TreeMap<>(dailyData);

        ArrayList<BarEntry> entries = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();

        int index = 0;
        // Lấy 7 ngày gần nhất (hoặc lấy hết tùy bạn)
        // Ở đây mình lấy hết dữ liệu có trong map
        for (Map.Entry<String, Long> entry : sortedMap.entrySet()) {
            String dateKey = entry.getKey(); // "2026-01-03"
            long millis = entry.getValue();
            float minutes = millis / 60000f; // Đổi ra phút để cột không bị quá cao

            entries.add(new BarEntry(index, minutes));

            // Format ngày hiển thị trục X (VD: "03/01")
            String day = dateKey.substring(8, 10);
            String month = dateKey.substring(5, 7);
            String label = day + "/" + month; // Kết quả: "03/01"
            labels.add(label);

            index++;
        }
        // Tạo DataSet
        BarDataSet dataSet = new BarDataSet(entries, "Watch time (minutes)");
        dataSet.setColor(getResources().getColor(R.color.app_main_color)); // Màu xanh chủ đạo
        dataSet.setValueTextColor(Color.BLACK);
        dataSet.setValueTextSize(10f);

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.6f); // Độ rộng cột

        // Cấu hình Chart
        barChart.setData(barData);
        barChart.getDescription().setEnabled(false); // Tắt description label góc phải
        barChart.animateY(1000); // Hiệu ứng mọc lên
        barChart.setFitBars(true);
        barChart.invalidate(); // Refresh

        // Cấu hình trục X (Ngày tháng)
        XAxis xAxis = barChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setDrawGridLines(false);

        // Cấu hình trục Y
        barChart.getAxisRight().setEnabled(false); // Tắt trục phải
        barChart.getAxisLeft().setAxisMinimum(0f); // Bắt đầu từ 0
    }

    private String formatViewCount(int count) {
        if (count == 0) return "0 views"; // Hoặc "No views"
        if (count == 1) return "1 view";  // Số ít: không có 's'

        // Xử lý số lớn (nếu user xem cực nhiều)
        if (count < 1000) return count + " views";
        if (count < 1000000) return String.format(java.util.Locale.US, "%.1fK views", count / 1000.0);
        return String.format(java.util.Locale.US, "%.1fM views", count / 1000000.0);
    }
}