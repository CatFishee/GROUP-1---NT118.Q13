package com.example.metube.ui.history;

import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.PopupMenu;
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
import com.example.metube.ui.playlist.AddToPlaylistBottomSheet;
import com.example.metube.ui.playlist.PlaylistDetailActivity;
import com.example.metube.ui.video.VideoActivity;
import com.example.metube.utils.ShareUtil;
import com.example.metube.utils.VideoQueueManager;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class HistoryActivity extends AppCompatActivity implements HistoryMenuBottomSheet.HistoryMenuListener{

    private static final String TAG = "HistoryActivity";

    private RecyclerView rvHistory;
    private ProgressBar progressBar;
    private HistoryAdapter adapter;
    private List<Object> uiList = new ArrayList<>();
    private FirebaseFirestore firestore;
    private String currentUserId;
    private ChipGroup chipGroupFilters;
    private List<Object> allHistoryItems = new ArrayList<>(); // Danh sách gốc chưa lọc
    private String currentFilter = "All";
    private View layoutPausedBanner;
    private Button btnTurnOn;
    private android.widget.EditText etSearch;
    private String currentSearchText = ""; // Lưu từ khóa tìm kiếm hiện tại
    private com.google.firebase.firestore.ListenerRegistration historyStatusListener;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        rvHistory = findViewById(R.id.rv_history_videos);
//        progressBar = findViewById(R.id.progress_bar); // Thêm ProgressBar vào layout của bạn nếu muốn

        firestore = FirebaseFirestore.getInstance();
        currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

        ImageView btnOptions = findViewById(R.id.btn_history_options);
        btnOptions.setOnClickListener(this::showHistoryOptionsMenu);

        layoutPausedBanner = findViewById(R.id.layout_paused_banner);
        btnTurnOn = findViewById(R.id.btn_turn_on_history);
        etSearch = findViewById(R.id.et_search_history);

        btnTurnOn.setOnClickListener(v -> {
            // Bật lại lịch sử (isPaused = false)
            updateHistoryPauseState(false);
        });
        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Cập nhật từ khóa tìm kiếm và lọc lại danh sách
                currentSearchText = s.toString().toLowerCase().trim();
                filterHistory();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        chipGroupFilters = findViewById(R.id.chip_group_filters);
        setupRecyclerView();
        if (adapter != null) {
            adapter.setOnItemMoreClickListener((historyItem, position) -> {
                HistoryMenuBottomSheet bottomSheet = new HistoryMenuBottomSheet(
                        historyItem.getVideo(),
                        historyItem.getDocumentId(),
                        position,
                        HistoryMenuBottomSheet.TYPE_HISTORY,  // ← Thêm type
                        "",                                    // ← Playlist title (rỗng vì đây là History)
                        this
                );
                bottomSheet.show(getSupportFragmentManager(), "HistoryMenu");
            });
        }
        loadHistoryData();
        android.widget.ImageButton btnBack = findViewById(R.id.btn_back);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

    }
    // Thay thế hàm filterHistoryByTopic cũ bằng hàm này
    private void filterHistory() {
        uiList.clear();
        String lastDateHeader = "";
        List<Object> filteredList = new ArrayList<>();

        for (Object item : allHistoryItems) {
            if (item instanceof HistoryItem) {
                HistoryItem historyItem = (HistoryItem) item;
                Video video = historyItem.getVideo();

                // Nếu video null thì bỏ qua
                if (video == null) continue;

                // --- ĐIỀU KIỆN 1: LỌC THEO TOPIC ---
                boolean matchTopic = false;
                if (currentFilter.equals("All")) {
                    matchTopic = true;
                } else {
                    if (video.getTopics() != null) {
                        for (String videoTopic : video.getTopics()) {
                            if (videoTopic.equalsIgnoreCase(currentFilter)) {
                                matchTopic = true;
                                break;
                            }
                        }
                    }
                }

                // --- ĐIỀU KIỆN 2: LỌC THEO TỪ KHÓA SEARCH ---
                boolean matchSearch = false;
                if (currentSearchText.isEmpty()) {
                    matchSearch = true; // Không nhập gì thì coi như đúng
                } else {
                    String title = video.getTitle() != null ? video.getTitle().toLowerCase() : "";
                    String desc = video.getDescription() != null ? video.getDescription().toLowerCase() : "";

                    // Kiểm tra xem Title hoặc Description có chứa từ khóa không
                    if (title.contains(currentSearchText) || desc.contains(currentSearchText)) {
                        matchSearch = true;
                    }
                }

                // CHỈ THÊM VÀO LIST NẾU THỎA MÃN CẢ 2 ĐIỀU KIỆN
                if (matchTopic && matchSearch) {
                    filteredList.add(item);
                }
            }
        }

        // --- XỬ LÝ LẠI DATE HEADER CHO DANH SÁCH ĐÃ LỌC ---
        for (Object item : filteredList) {
            if (item instanceof HistoryItem) {
                // Tính toán lại Header ngày tháng vì danh sách đã bị cắt bớt
                String currentDateHeader = getFormattedDate(((HistoryItem) item).getWatchedAt().toDate().getTime());

                if (!currentDateHeader.equals(lastDateHeader)) {
                    uiList.add(new DateHeader(currentDateHeader));
                    lastDateHeader = currentDateHeader;
                }
                uiList.add(item);
            }
        }

        adapter.notifyDataSetChanged();

        // Log kiểm tra
        Log.d(TAG, "Filtering: Topic=" + currentFilter + ", Search='" + currentSearchText + "'. Items found: " + (uiList.size() - filteredList.size())); // Trừ đi số lượng header
    }
    private void startListeningToHistoryStatus() {
        if (currentUserId == null) return;

        // Dùng addSnapshotListener thay vì get()
        historyStatusListener = firestore.collection("users").document(currentUserId)
                .addSnapshotListener((documentSnapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Listen failed.", e);
                        return;
                    }

                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null) {
                            // Tự động hiện/ẩn banner khi dữ liệu thay đổi
                            if (user.isHistoryPaused()) {
                                layoutPausedBanner.setVisibility(View.VISIBLE);
                            } else {
                                layoutPausedBanner.setVisibility(View.GONE);
                            }
                        }
                    }
                });
    }
    @Override
    protected void onStart() {
        super.onStart();
        startListeningToHistoryStatus();
    }

    // Hủy đăng ký khi thoát màn hình để không tốn pin
    @Override
    protected void onStop() {
        super.onStop();
        if (historyStatusListener != null) {
            historyStatusListener.remove();
        }
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
                    uiList.clear();
                    allHistoryItems.clear();
                    if (querySnapshot.isEmpty()) {
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                        adapter.notifyDataSetChanged(); // Cập nhật để xóa trắng màn hình nếu rỗng
                        return;
                    }

                    List<HistoryItem> historyItems = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        HistoryItem item = doc.toObject(HistoryItem.class);
                        item.setDocumentId(doc.getId());

                        historyItems.add(item);
                    }
                    // Bắt đầu chuỗi truy vấn lồng nhau
                    fetchVideoDetails(historyItems);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting watch history", e);
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                });
    }

    private void showHistoryOptionsMenu(View view) {
        PopupMenu popup = new PopupMenu(this, view);
        // Tự tạo menu bằng code java cho nhanh, đỡ phải tạo file xml menu
        popup.getMenu().add(0, 1, 0, "Pause watch history");
        popup.getMenu().add(0, 2, 0, "Clear all watch history");
        popup.getMenu().add(0, 3, 0, "Manage all history");

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    showPauseConfirmDialog();
                    return true;
                case 2:
                    showClearHistoryDialog();
                    return true;
                case 3:
                    // Mở màn hình Manage (làm sau)
                    Toast.makeText(this, "Opening Manage History...", Toast.LENGTH_SHORT).show();
                    return true;
            }
            return false;
        });
        popup.show();
    }
    private void showClearHistoryDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Clear watch history?")
                .setMessage("Your Metube watch history will be cleared from all Metube apps on all devices.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear watch history", (dialog, which) -> {
                    clearAllHistory();
                })
                .show();
    }

    private void clearAllHistory() {
        if (currentUserId == null) return;

        // Hiện loading để người dùng biết đang xử lý
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        // 1. Lấy toàn bộ lịch sử (Không giới hạn limit để xóa sạch)
        firestore.collection("watchHistory")
                .whereEqualTo("userID", currentUserId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        Toast.makeText(this, "History is already empty", Toast.LENGTH_SHORT).show();
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                        return;
                    }

                    // 2. Chuẩn bị Batch
                    com.google.firebase.firestore.WriteBatch batch = firestore.batch();
                    List<DocumentSnapshot> documents = querySnapshot.getDocuments();

                    // Firestore giới hạn batch 500 items. Ta chỉ xóa 500 cái đầu tiên.
                    // Nếu nhiều hơn 500, ta sẽ gọi hàm đệ quy để xóa tiếp.
                    int batchSize = 0;
                    for (DocumentSnapshot doc : documents) {
                        batch.delete(doc.getReference());
                        batchSize++;

                        // Nếu đủ 490 cái (trừa hao) thì dừng gom, đem đi xóa
                        if (batchSize >= 490) break;
                    }

                    final int deletedCount = batchSize;
                    final int totalCount = documents.size();

                    // 3. Thực thi Xóa
                    batch.commit().addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Deleted batch of " + deletedCount + " items.");

                        // Nếu tổng số item lớn hơn số vừa xóa -> Vẫn còn -> Gọi lại hàm để xóa tiếp
                        if (totalCount > deletedCount) {
                            clearAllHistory(); // Đệ quy: Xóa tiếp đợt sau
                        } else {
                            // Đã xóa hết sạch sành sanh
                            Toast.makeText(this, "All history cleared!", Toast.LENGTH_SHORT).show();

                            // --- CẬP NHẬT GIAO DIỆN ---
                            uiList.clear();           // Xóa list hiển thị
                            allHistoryItems.clear();  // QUAN TRỌNG: Xóa cả list gốc (để filter không bị lỗi)

                            if (adapter != null) {
                                adapter.notifyDataSetChanged();
                            }
                            setupTopicFilters(new ArrayList<>());

                            if (progressBar != null) progressBar.setVisibility(View.GONE);
                        }
                    }).addOnFailureListener(e -> {
                        Log.e(TAG, "Clear history failed: ", e);
                        Toast.makeText(this, "Failed to clear history", Toast.LENGTH_SHORT).show();
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                    });

                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching history for delete: ", e);
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                });
    }
    private void showPauseConfirmDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Pause watch history?")
                .setMessage("Pausing Metube watch history can make it harder to find videos you watched.") // Copy text dài dòng kia nếu muốn
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Pause", (dialog, which) -> {
                    // Update lên Firestore
                    updateHistoryPauseState(true);
                })
                .show();
    }
    private void updateHistoryPauseState(boolean isPaused) {
        if (currentUserId == null) return;

        firestore.collection("users").document(currentUserId)
                .update("isHistoryPaused", isPaused)
                .addOnSuccessListener(aVoid -> {
                    String msg = isPaused ? "Watch history paused" : "Watch history turned on";
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

                    // Cập nhật giao diện Banner ngay lập tức
                    if (isPaused) {
                        layoutPausedBanner.setVisibility(View.VISIBLE);
                    } else {
                        layoutPausedBanner.setVisibility(View.GONE);
                        // Khi bật lại, có thể load lại data mới nhất nếu muốn
                        loadHistoryData();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to update settings", Toast.LENGTH_SHORT).show();
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
        Set<String> topicsSet = new HashSet<>(); // Dùng Set để tránh topic trùng lặp

        for (HistoryItem item : historyItems) {
            // Chỉ xử lý những item có đủ dữ liệu
            if (item.getWatchedAt() == null || item.getVideo() == null) continue;

            allHistoryItems.add(item);

            // Trích xuất topics từ mỗi video
            if (item.getVideo().getTopics() != null) {
                for (String topic : item.getVideo().getTopics()) {
                    // Chuẩn hóa topic (viết hoa chữ cái đầu) để gom nhóm
                    String capitalizedTopic = topic.substring(0, 1).toUpperCase() + topic.substring(1).toLowerCase();
                    topicsSet.add(capitalizedTopic);
                }
            }

            String currentDateHeader = getFormattedDate(item.getWatchedAt().toDate().getTime());
            if (!currentDateHeader.equals(lastDateHeader)) {
                uiList.add(new DateHeader(currentDateHeader));
                lastDateHeader = currentDateHeader;
            }
            uiList.add(item);
        }
        setupTopicFilters(new ArrayList<>(topicsSet));
        filterHistoryByTopic(currentFilter);
        if (progressBar != null) progressBar.setVisibility(View.GONE);
        adapter.notifyDataSetChanged();
    }

    private void setupTopicFilters(List<String> topicList) {
        chipGroupFilters.removeAllViews();

        // Luôn thêm chip "All"
        Chip allChip = createTopicChip("All");
//        allChip.setChecked(true);
//        chipGroupFilters.addView(allChip);
        allChip.setId(View.generateViewId());
        chipGroupFilters.addView(allChip);

        // Sắp xếp danh sách topic theo alphabet
//        Collections.sort(topicList);
//
//        // Thêm các chip còn lại
//        if (topicList != null && !topicList.isEmpty()) {
//            for (String topic : topicList) {
//                chipGroupFilters.addView(createTopicChip(topic));
//            }
//        }
        Collections.sort(topicList);
        if (topicList != null && !topicList.isEmpty()) {
            for (String topic : topicList) {
                Chip chip = createTopicChip(topic);
                chip.setId(View.generateViewId()); // Tạo ID ngẫu nhiên
                chipGroupFilters.addView(chip);
            }
        }

        // 3. Đặt mặc định chọn "All" sau khi đã add vào Group
        // (Sử dụng post để đảm bảo UI đã render xong)
        chipGroupFilters.post(() -> {
            chipGroupFilters.check(allChip.getId());
        });
    }

    private Chip createTopicChip(String text) {
        // ✅ SỬA Ở ĐÂY: Sử dụng constructor thứ ba của Chip để truyền style vào
//        Chip chip = new Chip(this, null, R.id.item_chip_filter);
        Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_chip_filter, chipGroupFilters, false);

        // Đặt các thuộc tính cần thay đổi động
        chip.setText(text);
        chip.setId(View.generateViewId());
//        chip.setCheckable(true);

        // Các thuộc tính trong style (màu sắc, font chữ...) sẽ được tự động áp dụng.
        // Chúng ta không cần gọi setStyle() nữa.

        // Gắn sự kiện click
//        chip.setOnClickListener(v -> {
//            // Đảm bảo chỉ chip được nhấn là được check
//            chipGroupFilters.check(chip.getId());
//            filterHistoryByTopic(text);
//        });
        chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Chỉ lọc khi chip được chọn (tránh trường hợp bỏ chọn chip cũ cũng kích hoạt filter)
                currentFilter = text; // Cập nhật biến global
                filterHistory();
            }
        });
        return chip;
    }
    private void filterHistoryByTopic(String topic) {
        currentFilter = topic;
        uiList.clear();
        String lastDateHeader = "";
        List<Object> filteredList = new ArrayList<>();

        if (topic.equals("All")) {
            // Nếu là "All", lấy tất cả từ danh sách gốc
            filteredList.addAll(allHistoryItems);
        } else {
            // Lọc theo topic
            for (Object item : allHistoryItems) {
                if (item instanceof HistoryItem) {
                    Video video = ((HistoryItem) item).getVideo();
                    if (video != null && video.getTopics() != null) {
                        for (String videoTopic : video.getTopics()) {
                            if (videoTopic.equalsIgnoreCase(topic)) {
                                filteredList.add(item);
                                break; // Đã tìm thấy, chuyển sang video tiếp theo
                            }
                        }
                    }
                }
            }
        }
        // Thêm lại các Date Header vào danh sách đã lọc
        for (Object item : filteredList) {
            if (item instanceof HistoryItem) {
                String currentDateHeader = getFormattedDate(((HistoryItem) item).getWatchedAt().toDate().getTime());
                if (!currentDateHeader.equals(lastDateHeader)) {
                    uiList.add(new DateHeader(currentDateHeader));
                    lastDateHeader = currentDateHeader;
                }
                uiList.add(item);
            }
        }
        adapter.notifyDataSetChanged();
        Log.d(TAG, "Filtered history for topic '" + topic + "'. Items found: " + uiList.size());
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
    @Override
    public void onRemoveFromHistory(String docId, int position) {
        if (docId == null) return;

        // Xóa trên Firestore
        firestore.collection("watchHistory").document(docId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Removed from watch history", Toast.LENGTH_SHORT).show();

                    // Xóa trên giao diện ngay lập tức
                    if (adapter != null) {
                        adapter.removeItem(position);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to remove", Toast.LENGTH_SHORT).show();
                });
    }
    @Override
    public void onSaveToPlaylist(Video video) {
        AddToPlaylistBottomSheet bottomSheet = new AddToPlaylistBottomSheet(video);
        bottomSheet.show(getSupportFragmentManager(), "AddToPlaylistBottomSheet");
    }

    @Override
    public void onDownload(Video video) {
        com.example.metube.utils.DownloadUtil.downloadVideo(
                this,
                video.getVideoURL(),
                video.getTitle()
        );
    }

    @Override
    public void onShare(Video video) {
        if (video != null && video.getVideoURL() != null) {
            // SỬA: Dùng HistoryActivity.this thay vì PlaylistDetailActivity.this
            ShareUtil.shareVideo(HistoryActivity.this, video.getVideoURL());
        }
    }
    @Override
    public void onPlayNextInQueue(Video video) {
        // 1. Thêm video vào Queue Manager (vị trí current + 1)
        VideoQueueManager.getInstance().playNext(video);

        // 2. Tự động set video này làm video hiện tại để phát ngay
        int newPosition = VideoQueueManager.getInstance().getCurrentPosition() + 1;
        VideoQueueManager.getInstance().setCurrentPosition(newPosition);

        Toast.makeText(this, "Playing now: " + video.getTitle(), Toast.LENGTH_SHORT).show();

        if (video.getVideoID() != null) {
            Intent intent = new Intent(this, VideoActivity.class);
            intent.putExtra("video_id", video.getVideoID());

            // QUAN TRỌNG: Dùng cờ này để Reset VideoActivity nếu nó đang chạy nền
            // Điều này ép buộc onCreate chạy lại -> nhận Intent mới -> phát video mới
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

            startActivity(intent);
        }
    }
}