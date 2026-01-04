package com.example.metube.ui.home;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.content.Intent;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.example.metube.R;
import com.example.metube.model.Subscription;
import com.example.metube.model.Video;
import com.example.metube.ui.settings.SettingsActivity;
import com.example.metube.ui.upload.UploadActivity;
import com.example.metube.ui.search.SearchActivity;
import com.example.metube.ui.video.VideoActivity;
import com.example.metube.utils.NetworkUtil;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

public class HomepageActivity extends AppCompatActivity {

    private List<String> topics;
    private LinearLayout topicContainer;
    private View currentlySelectedTopic = null;
    private RecyclerView recyclerViewVideos;
    private VideoAdapter videoAdapter;
    private List<Video> videoList;
    private List<Video> allVideoList;
    private String currentSelectedTopic = "All";
    private FirebaseFirestore db;
    private ListenerRegistration firestoreListener;
    private FrameLayout fragmentContainer;
    private LinearLayout homeContentContainer;
    private List<ImageView> tabImageViews;
    private List<TextView> tabTextViews;
    private ImageView ivHome, ivCreator, ivSubs, ivProfile;
    private TextView tvHome, tvCreator, tvSubs, tvProfile;
    private View topBar;
    private LinearLayout homeTitleLayout;
    private LinearLayout notificationsTitleLayout;
    private FirebaseAuth auth;
    private FirebaseUser currentUser;
    private List<String> subscribedChannelIDs = new ArrayList<>();
    private Handler autoplayHandler = new Handler(Looper.getMainLooper());
    private Runnable autoplayRunnable;

    private static final String TAG = "HomepageActivity_Debug";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_homepage);

        homeContentContainer = findViewById(R.id.home_content_container);
        fragmentContainer = findViewById(R.id.fragment_container);
        topicContainer = findViewById(R.id.topicContainer);
        recyclerViewVideos = findViewById(R.id.recyclerViewVideos);
        topBar = findViewById(R.id.topBar);

        if (topBar != null) {
            homeTitleLayout = topBar.findViewById(R.id.layout_home_title);
            notificationsTitleLayout = topBar.findViewById(R.id.layout_notifications_title);
        }

        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();

        db = FirebaseFirestore.getInstance();

        setupTopicFilters(new ArrayList<>());
        setupRecyclerView();
        loadSubscribedChannels();
        fetchVideosFromFirestore();
        listenForVideoUpdates();

        setupBottomNav();
        setupTopBarActions();

        setupAutoplay();

        // Default view
        showHomeContent();

        SharedPreferences prefs = getSharedPreferences("theme_prefs", Context.MODE_PRIVATE);
        int themeOption = prefs.getInt("selected_theme", 0);
        switch (themeOption) {
            case 0: AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM); break;
            case 1: AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO); break;
            case 2: AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES); break;
        }
    }

    private void setupRecyclerView() {
        videoList = new ArrayList<>();
        allVideoList = new ArrayList<>();
        videoAdapter = new VideoAdapter(this, videoList, new VideoAdapter.OnVideoClickListener() {
            @Override
            public void onVideoClick(Video video) {
                // Logic mở VideoActivity cũ của bạn
                if (video != null && video.getVideoID() != null) {
                    Intent intent = new Intent(HomepageActivity.this, VideoActivity.class);
                    intent.putExtra("video_id", video.getVideoID());
                    startActivity(intent);
                }
            }

            @Override
            public void onAvatarClick(String uploaderId) {
                // ✅ LOGIC MỚI: Mở Creator Profile
                Intent intent = new Intent(HomepageActivity.this, com.example.metube.ui.contentcreator.CreatorProfileActivity.class);
                intent.putExtra("creator_id", uploaderId);
                startActivity(intent);
            }
        });
        recyclerViewVideos.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewVideos.setAdapter(videoAdapter);
    }

    private void listenForVideoUpdates() {
        if (firestoreListener != null) {
            firestoreListener.remove();
        }

        firestoreListener = db.collection("videos")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((value, e) -> {
                    if (e != null) {
                        Log.w(TAG, "Listen failed.", e);
                        return;
                    }

//                    if (value != null) {
//                        videoList.clear();
//                        for (QueryDocumentSnapshot document : value) {
//                            Video video = document.toObject(Video.class);
//                            videoList.add(video);
//                        }
//                        videoAdapter.setVideos(videoList);
//                        Log.d(TAG, "Data updated. Total videos: " + videoList.size());
//                    }
                    if (value != null) {
                        allVideoList.clear();
                        for (QueryDocumentSnapshot document : value) {
                            Video video = document.toObject(Video.class);
                            allVideoList.add(video);
                        }
                        // ✅ Áp dụng filter sau khi load data
//                        filterVideosByTopic(currentSelectedTopic);
//                        Log.d(TAG, "Data updated. Total videos: " + allVideoList.size());
                        fetchViewCountsAndSort();
                        Log.d(TAG, "Data updated. Total videos: " + allVideoList.size());
                    }
                });
    }

    private void fetchVideosFromFirestore() {
        db.collection("videos")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    allVideoList.clear();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Video video = document.toObject(Video.class);
                        allVideoList.add(video);
                    }
//                    filterVideosByTopic(currentSelectedTopic);
                    fetchViewCountsAndSort();
                })
                .addOnFailureListener(e -> Log.w(TAG, "Error getting documents.", e));
    }
    private void fetchViewCountsAndSort() {
        if (allVideoList.isEmpty()) {
            filterVideosByTopic(currentSelectedTopic);
            return;
        }

        DatabaseReference videoStatRef = FirebaseDatabase.getInstance().getReference("videostat");
        AtomicInteger pendingRequests = new AtomicInteger(allVideoList.size());

        for (Video video : allVideoList) {
            if (video.getVideoID() == null) {
                if (pendingRequests.decrementAndGet() == 0) {
                    sortAndFilterVideos();
                }
                continue;
            }

            videoStatRef.child(video.getVideoID()).child("viewCount")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            Long views = snapshot.getValue(Long.class);
                            video.setViewCount(views != null ? views : 0L);
                            Log.d(TAG, "Fetched viewCount for '" + video.getTitle() + "': " + video.getViewCount());

                            if (pendingRequests.decrementAndGet() == 0) {
                                sortAndFilterVideos();
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Log.e(TAG, "Failed to fetch viewCount for " + video.getVideoID(), error.toException());
                            video.setViewCount(0L);

                            if (pendingRequests.decrementAndGet() == 0) {
                                sortAndFilterVideos();
                            }
                        }
                    });
        }
    }
    private void sortAndFilterVideos() {
        // Sort theo viewCount từ cao xuống thấp
        Collections.sort(allVideoList, new Comparator<Video>() {
            @Override
            public int compare(Video v1, Video v2) {
                return Long.compare(v2.getViewCount(), v1.getViewCount());
            }
        });

        Log.d(TAG, "Videos sorted by viewCount:");
        for (int i = 0; i < Math.min(5, allVideoList.size()); i++) {
            Video v = allVideoList.get(i);
            Log.d(TAG, "  " + (i+1) + ". " + v.getTitle() + " - Views: " + v.getViewCount());
        }
        updateTopicsFromVideos();
        filterVideosByTopic(currentSelectedTopic);
    }
    private void loadSubscribedChannels() {
        if (currentUser == null) {
            Log.d(TAG, "User not logged in - cannot load subscriptions");
            return;
        }

        db.collection("subscriptions")
                .whereEqualTo("viewerID", currentUser.getUid())
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "❌ Lỗi load subscriptions", error);
                        return;
                    }

                    subscribedChannelIDs.clear();

                    if (querySnapshot != null) {
                        for (QueryDocumentSnapshot doc : querySnapshot) {
                            // Lấy status dưới dạng String
                            String statusStr = doc.getString("status");
                            // ✅ SỬA: uploaderID thay vì channelID
                            String uploaderID = doc.getString("uploaderID");

                            Log.d(TAG, "📄 Doc: " + doc.getId() +
                                    " | uploaderID: " + uploaderID +
                                    " | status: " + statusStr);

                            // ✅ CHỈ LẤY SUBSCRIBED hoặc MEMBERSHIP
                            if (uploaderID != null &&
                                    (statusStr != null &&
                                            (statusStr.equals("SUBSCRIBED") ||
                                                    statusStr.equals("MEMBERSHIP")))) {
                                subscribedChannelIDs.add(uploaderID);
                                Log.d(TAG, "✅ Thêm uploaderID: " + uploaderID);
                            }
                        }
                    }

                    Log.d(TAG, "✅ Tổng số channels đã subscribe: " + subscribedChannelIDs.size());
                    Log.d(TAG, "📋 Danh sách: " + subscribedChannelIDs);

                    // Nếu đang ở tab "Subscribed", refresh ngay
                    if (currentSelectedTopic.equals("Subscribed")) {
                        filterVideosByTopic("Subscribed");
                    }
                });
    }
    private void updateTopicsFromVideos() {
        Set<String> uniqueTopics = new HashSet<>();

        // Duyệt qua tất cả videos và lấy topics
        for (Video video : allVideoList) {
            if (video.getTopics() != null && !video.getTopics().isEmpty()) {
                uniqueTopics.addAll(video.getTopics());
            }
        }

        // Chuyển Set thành List và sort theo alphabet
        List<String> topicList = new ArrayList<>(uniqueTopics);
        Collections.sort(topicList);

        if (currentUser != null) {
            topicList.add(0, "Subscribed");
        }


        Log.d(TAG, "✅ Found " + topicList.size() + " unique topics: " + topicList);

        // Cập nhật UI
        setupTopicFilters(topicList);
    }

    private void filterVideosByTopic(String topic) {
        currentSelectedTopic = topic;
        videoList.clear();

        if (topic.equals("All")) {
            // Hiển thị tất cả video
            videoList.addAll(allVideoList);
        }
        else if (topic.equals("Subscribed")) {
            if (currentUser == null) {
                Toast.makeText(this, "Vui lòng đăng nhập để xem video đã subscribe",
                        Toast.LENGTH_SHORT).show();
                Log.d(TAG, "❌ Chưa đăng nhập");
            }
            else if (subscribedChannelIDs.isEmpty()) {
                Toast.makeText(this, "Bạn chưa subscribe channel nào",
                        Toast.LENGTH_SHORT).show();
                Log.d(TAG, "❌ Danh sách subscribe trống");
            }
            else {
                // ✅ LỌC VIDEO
                Log.d(TAG, "🔍 Bắt đầu lọc trong " + allVideoList.size() + " videos");
                Log.d(TAG, "📋 Subscribed channels: " + subscribedChannelIDs);

                int count = 0;
                for (Video video : allVideoList) {
                    String uploaderID = video.getUploaderID();

                    if (uploaderID != null && subscribedChannelIDs.contains(uploaderID)) {
                        videoList.add(video);
                        count++;
                        Log.d(TAG, "✅ Video #" + count + ": " + video.getTitle() +
                                " (uploader: " + uploaderID + ")");
                    }
                }

                Log.d(TAG, "✅ Tìm thấy " + count + " videos từ subscribed channels");
            }
        }
        else {
            // Lọc video theo topics (List<String>)
            for (Video video : allVideoList) {
                if (video.getTopics() != null && !video.getTopics().isEmpty()) {
                    // Check xem topic có trong list topics không (không phân biệt hoa thường)
                    for (String videoTopic : video.getTopics()) {
                        if (videoTopic.equalsIgnoreCase(topic)) {
                            videoList.add(video);
                            break; // Tìm thấy rồi thì break, tránh add trùng
                        }
                    }
                }
            }
        }
        videoAdapter.setVideos(videoList);
        videoAdapter.notifyDataSetChanged();
        Log.d(TAG, "Filtered videos for topic '" + topic + "': " + videoList.size());
    }

    private void setupTopBarActions() {
        if (topBar == null) return;

        topBar.findViewById(R.id.btnNotifications).setOnClickListener(v -> {
            loadFragment(new NotificationsFragment());
            switchToNotificationsView();
        });

        topBar.findViewById(R.id.btnBack).setOnClickListener(v -> showHomeContent());

        View searchButton = topBar.findViewById(R.id.btnSearch);
        if (searchButton != null) {
            searchButton.setOnClickListener(v -> {
                Intent intent = new Intent(HomepageActivity.this, SearchActivity.class);
                startActivity(intent);
            });
        }
        View settingsButton = topBar.findViewById(R.id.btnSettings);
        if (settingsButton != null) {
            settingsButton.setOnClickListener(v -> {
                Intent intent = new Intent(HomepageActivity.this, SettingsActivity.class);
                startActivity(intent);
            });
        }
    }

    private void setupBottomNav() {
        View bottomNavView = findViewById(R.id.bottomNav);
        if (bottomNavView == null) return;

        View tabHome = bottomNavView.findViewById(R.id.tabHome);
        View tabCreator = bottomNavView.findViewById(R.id.tabCreator);
        View tabUpload = bottomNavView.findViewById(R.id.tabUpload);
        View tabSubs = bottomNavView.findViewById(R.id.tabWatch);
        View tabProfile = bottomNavView.findViewById(R.id.tabProfile);

        ivHome = bottomNavView.findViewById(R.id.iv_home);
        tvHome = bottomNavView.findViewById(R.id.tv_home);
        ivCreator = bottomNavView.findViewById(R.id.iv_creator);
        tvCreator = bottomNavView.findViewById(R.id.tv_creator);
        ivSubs = bottomNavView.findViewById(R.id.iv_watch);
        tvSubs = bottomNavView.findViewById(R.id.tv_watch);
        ivProfile = bottomNavView.findViewById(R.id.iv_profile);
        tvProfile = bottomNavView.findViewById(R.id.tv_profile);

        tabImageViews = Arrays.asList(ivHome, ivCreator, ivSubs, ivProfile);
        tabTextViews = Arrays.asList(tvHome, tvCreator, tvSubs, tvProfile);

        tabHome.setOnClickListener(v -> {
            updateTabSelection(ivHome);
            showHomeContent();
        });
        tabCreator.setOnClickListener(v -> {
            updateTabSelection(ivCreator);
            loadFragment(new CreatorFragment());
        });
        tabSubs.setOnClickListener(v -> {
            updateTabSelection(ivSubs);
            loadFragment(new WatchTogetherFragment());
        });
        tabProfile.setOnClickListener(v -> {
            updateTabSelection(ivProfile);
            loadFragment(new PersonFragment());
        });

        if (tabUpload != null) {
            tabUpload.setOnClickListener(v ->
                    startActivity(new Intent(this, UploadActivity.class)));
        }

        updateTabSelection(ivHome);
    }

    private void updateTabSelection(View selectedImageView) {
        // 1. Reset tất cả về trạng thái chưa chọn
        ivHome.setSelected(false);
        tvHome.setSelected(false);
        ivHome.setImageResource(R.drawable.ic_home);

        ivCreator.setSelected(false);
        tvCreator.setSelected(false);
        ivCreator.setImageResource(R.drawable.ic_creator);

        ivSubs.setSelected(false);
        tvSubs.setSelected(false);
        ivSubs.setImageResource(R.drawable.ic_subscriptions);

        ivProfile.setSelected(false);
        tvProfile.setSelected(false);
        ivProfile.setImageResource(R.drawable.ic_person);

        if (selectedImageView == ivHome) {
            ivHome.setSelected(true);
            tvHome.setSelected(true);
            ivHome.setImageResource(R.drawable.ic_home_filled);
        } else if (selectedImageView == ivCreator) {
            ivCreator.setSelected(true);
            tvCreator.setSelected(true);
            ivCreator.setImageResource(R.drawable.ic_creator_filled);
        } else if (selectedImageView == ivSubs) {
            ivSubs.setSelected(true);
            tvSubs.setSelected(true);
            ivSubs.setImageResource(R.drawable.ic_subscriptions_filled);
        } else if (selectedImageView == ivProfile) {
            ivProfile.setSelected(true);
            tvProfile.setSelected(true);
            ivProfile.setImageResource(R.drawable.ic_person_filled);
        }
    }

    private void showHomeContent() {
        homeContentContainer.setVisibility(View.VISIBLE);
        fragmentContainer.setVisibility(View.GONE);
        switchToHomeView();
    }

    private void loadFragment(androidx.fragment.app.Fragment fragment) {
        homeContentContainer.setVisibility(View.GONE);
        fragmentContainer.setVisibility(View.VISIBLE);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    private void switchToHomeView() {
        if (homeTitleLayout != null && notificationsTitleLayout != null) {
            homeTitleLayout.setVisibility(View.VISIBLE);
            notificationsTitleLayout.setVisibility(View.GONE);
        }
    }

    private void switchToNotificationsView() {
        if (homeTitleLayout != null && notificationsTitleLayout != null) {
            homeTitleLayout.setVisibility(View.GONE);
            notificationsTitleLayout.setVisibility(View.VISIBLE);
        }
    }

    private void setupTopicFilters(List<String> topicList) {
        topicContainer.setBackgroundResource(R.color.bg_main);
        topicContainer.removeAllViews();
        currentlySelectedTopic = null;

        Button allButton = createTopicButton("All");
        allButton.setSelected(true);
        currentlySelectedTopic = allButton;
        topicContainer.addView(allButton);

        if (topicList != null && !topicList.isEmpty()) {
            for (String topic : topicList) {
                Button topicButton = createTopicButton(topic);
                topicContainer.addView(topicButton);
            }
        }
    }

    private Button createTopicButton(String text) {
        Button button = new Button(this, null, 0, R.style.Widget_App_TopicButton);
        button.setText(text);

        // Gán bộ Selector cho màu chữ và màu nền
        button.setTextColor(ContextCompat.getColorStateList(this, R.color.selector_topic_button_text));
        button.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.selector_topic_button_background));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMarginEnd(20);
        button.setLayoutParams(params);
        button.setOnClickListener(v -> {
            if (currentlySelectedTopic != null) {
                currentlySelectedTopic.setSelected(false);
            }
            v.setSelected(true);
            currentlySelectedTopic = v;


            filterVideosByTopic(text);
        });

        // Mặc định nút All được chọn
        if (text.equals("All")) {
            button.setSelected(true);
            currentlySelectedTopic = button;
        }
        return button;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (autoplayHandler != null && autoplayRunnable != null) {
            autoplayHandler.removeCallbacks(autoplayRunnable);
        }

        if (videoAdapter != null) {
            videoAdapter.releasePlayer();
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        if (currentUser != null) {
            loadSubscribedChannels();
        }
        if (videoAdapter != null) {
            boolean canAutoplay = shouldAutoplay();
            videoAdapter.setAutoplayEnabled(canAutoplay);

            // Nếu được phép phát, hãy thử phát video đang hiển thị ngay lập tức
            if (canAutoplay) {
                // ✅ Play ngay khi vào màn hình
                scheduleAutoplay(300); // 300ms delay cho RecyclerView render xong
            }
        }
    }
    @Override
    protected void onPause() {
        super.onPause();
        if (videoAdapter != null) videoAdapter.stopVideo();
    }

    private void setupAutoplay() {
        recyclerViewVideos.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);

                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    // ✅ Scroll dừng → Play ngay với delay nhỏ
                    scheduleAutoplay(200); // 200ms delay
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                // ✅ Đang scroll bằng tay → Throttle để tránh gọi quá nhiều
                if (recyclerView.getScrollState() == RecyclerView.SCROLL_STATE_DRAGGING) {
                    scheduleAutoplay(500); // 500ms throttle khi đang scroll
                }
            }
        });
    }
    private void scheduleAutoplay(long delayMs) {
        // Cancel job cũ nếu có
        if (autoplayRunnable != null) {
            autoplayHandler.removeCallbacks(autoplayRunnable);
        }

        // Tạo job mới
        autoplayRunnable = () -> playMostVisibleVideo();

        // Chạy sau delay
        autoplayHandler.postDelayed(autoplayRunnable, delayMs);
    }
    private void playMostVisibleVideo() {
        if (!shouldAutoplay()) return;

        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerViewVideos.getLayoutManager();
        if (layoutManager == null) return;

        int firstVisible = layoutManager.findFirstVisibleItemPosition();
        int lastVisible = layoutManager.findLastVisibleItemPosition();

        if (firstVisible == RecyclerView.NO_POSITION) return;

        int targetPos = -1;
        float maxVisiblePercentage = 0f;

        // ✅ Duyệt qua các item đang hiển thị
        for (int i = firstVisible; i <= lastVisible; i++) {
            View itemView = layoutManager.findViewByPosition(i);
            if (itemView == null) continue;

            // Tính % hiển thị của video này
            float visiblePercentage = getVisiblePercentage(itemView, recyclerViewVideos);

            // Lấy video có % hiển thị cao nhất
            if (visiblePercentage > maxVisiblePercentage) {
                maxVisiblePercentage = visiblePercentage;
                targetPos = i;
            }
        }

        // ✅ Chỉ phát nếu video chiếm >= 60% màn hình (tăng từ 50% để chính xác hơn)
        if (targetPos != -1 && maxVisiblePercentage >= 60f) {
            Log.d(TAG, "▶️ Autoplay video #" + targetPos + " (" + maxVisiblePercentage + "% visible)");
            videoAdapter.playVideoAt(targetPos);
        }
    }
    private float getVisiblePercentage(View itemView, RecyclerView recyclerView) {
        int itemTop = itemView.getTop();
        int itemBottom = itemView.getBottom();
        int itemHeight = itemView.getHeight();

        int recyclerTop = recyclerView.getPaddingTop();
        int recyclerBottom = recyclerView.getHeight() - recyclerView.getPaddingBottom();

        // Tính phần visible của item
        int visibleTop = Math.max(itemTop, recyclerTop);
        int visibleBottom = Math.min(itemBottom, recyclerBottom);
        int visibleHeight = Math.max(0, visibleBottom - visibleTop);

        // Trả về % (0-100)
        return itemHeight > 0 ? (visibleHeight * 100f / itemHeight) : 0f;
    }

    private boolean shouldAutoplay() {
        SharedPreferences prefs = getSharedPreferences("settings_prefs", MODE_PRIVATE);
        int mode = prefs.getInt("playback_feeds_mode", 0); // 0: Always, 1: Wifi, 2: Off

        if (mode == 0) return true;
        if (mode == 1) return NetworkUtil.isWifiConnected(this);
        return false;
    }
}
