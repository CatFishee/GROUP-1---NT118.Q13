package com.example.metube.ui.home;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.example.metube.R;
import com.example.metube.model.Video;
import com.example.metube.ui.settings.SettingsActivity;
import com.example.metube.ui.upload.UploadActivity;
import com.example.metube.ui.search.SearchActivity;
import com.example.metube.ui.video.VideoActivity;
import com.example.metube.utils.NetworkUtil;
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
//    private ScrollView homeContentContainer;
    private LinearLayout homeContentContainer;
    private List<ImageView> tabImageViews;
    private List<TextView> tabTextViews;
    private ImageView ivHome, ivCreator, ivSubs, ivProfile;
    private TextView tvHome, tvCreator, tvSubs, tvProfile;
    private View topBar;
    private LinearLayout homeTitleLayout;
    private LinearLayout notificationsTitleLayout;

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

        db = FirebaseFirestore.getInstance();

        setupTopicFilters(createDummyTopics());
        setupRecyclerView();
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
        videoAdapter = new VideoAdapter(this, videoList, video -> {
            if (video != null && video.getVideoID() != null) {
                Intent intent = new Intent(HomepageActivity.this, VideoActivity.class);
                intent.putExtra("video_id", video.getVideoID());
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
        filterVideosByTopic(currentSelectedTopic);
    }

    private void filterVideosByTopic(String topic) {
        currentSelectedTopic = topic;
        videoList.clear();

        if (topic.equals("All")) {
            // Hiển thị tất cả video
            videoList.addAll(allVideoList);
        } else {
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

    private List<String> createDummyTopics() {
        List<String> dummyTopics = new ArrayList<>();
        dummyTopics.add("Gaming");
        dummyTopics.add("Music");
        return dummyTopics;
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
//        if (firestoreListener != null) firestoreListener.remove();
        if (videoAdapter != null) videoAdapter.releasePlayer();
    }
    @Override
    protected void onResume() {
        super.onResume();
        if (videoAdapter != null) {
            boolean canAutoplay = shouldAutoplay();
            videoAdapter.setAutoplayEnabled(canAutoplay);

            // Nếu được phép phát, hãy thử phát video đang hiển thị ngay lập tức
            if (canAutoplay) {
                recyclerViewVideos.postDelayed(() -> {
                    if (videoList != null && !videoList.isEmpty()) {
                        videoAdapter.playVideoAt(0);
                    }
                }, 800); // Tăng delay lên một chút để chắc chắn RV đã load xong
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
                    LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                    if (layoutManager == null) return;

                    int firstVisible = layoutManager.findFirstVisibleItemPosition();
                    int lastVisible = layoutManager.findLastVisibleItemPosition();

                    int targetPos = -1;

                    // TRƯỜNG HỢP ĐẶC BIỆT 1: ĐANG Ở ĐỈNH (Hiện video đầu tiên)
                    if (firstVisible == 0) {
                        targetPos = 0;
                    }
                    // TRƯỜNG HỢP ĐẶC BIỆT 2: ĐANG Ở ĐÁY (Hiện video cuối cùng)
                    else if (lastVisible == videoList.size() - 1) {
                        targetPos = lastVisible;
                    }
                    // TRƯỜNG HỢP Ở GIỮA: Tìm item gần tâm màn hình nhất
                    else {
                        float closestToCenter = Float.MAX_VALUE;
                        int screenCenter = recyclerView.getHeight() / 2;

                        for (int i = firstVisible; i <= lastVisible; i++) {
                            View itemView = layoutManager.findViewByPosition(i);
                            if (itemView == null) continue;

                            int itemCenter = (itemView.getTop() + itemView.getBottom()) / 2;
                            int distance = Math.abs(screenCenter - itemCenter);

                            if (distance < closestToCenter) {
                                closestToCenter = distance;
                                targetPos = i;
                            }
                        }
                    }

                    if (targetPos != -1 && shouldAutoplay()) {
                        videoAdapter.playVideoAt(targetPos);
                    }
                }
            }
        });
    }

    private boolean shouldAutoplay() {
        SharedPreferences prefs = getSharedPreferences("settings_prefs", MODE_PRIVATE);
        int mode = prefs.getInt("playback_feeds_mode", 0); // 0: Always, 1: Wifi, 2: Off

        if (mode == 0) return true;
        if (mode == 1) return NetworkUtil.isWifiConnected(this);
        return false;
    }
}
