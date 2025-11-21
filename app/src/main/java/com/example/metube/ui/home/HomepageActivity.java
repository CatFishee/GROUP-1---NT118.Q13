package com.example.metube.ui.home;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
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
import com.example.metube.ui.upload.UploadActivity;
import com.example.metube.ui.search.SearchActivity;
import com.example.metube.ui.video.VideoActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
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
    private List<Video> allVideoList; // ✅ Lưu toàn bộ video
    private String currentSelectedTopic = "All"; // ✅ Lưu topic đang chọn
    private FirebaseFirestore db;
    private ListenerRegistration firestoreListener;
    private FrameLayout fragmentContainer;
    private ScrollView homeContentContainer;
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

        // Default view
        showHomeContent();
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
    }

    private void setupBottomNav() {
        View bottomNavView = findViewById(R.id.bottomNav);
        if (bottomNavView == null) return;

        View tabHome = bottomNavView.findViewById(R.id.tabHome);
        View tabCreator = bottomNavView.findViewById(R.id.tabCreator);
        View tabUpload = bottomNavView.findViewById(R.id.tabUpload);
        View tabSubs = bottomNavView.findViewById(R.id.tabSubs);
        View tabProfile = bottomNavView.findViewById(R.id.tabProfile);

        ivHome = bottomNavView.findViewById(R.id.iv_home);
        tvHome = bottomNavView.findViewById(R.id.tv_home);
        ivCreator = bottomNavView.findViewById(R.id.iv_creator);
        tvCreator = bottomNavView.findViewById(R.id.tv_creator);
        ivSubs = bottomNavView.findViewById(R.id.iv_subs);
        tvSubs = bottomNavView.findViewById(R.id.tv_subs);
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
            loadFragment(new SubscriptionsFragment());
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
        if (selectedImageView == ivHome) {
            ivHome.setImageResource(R.drawable.ic_home_filled);
            tvHome.setTextColor(ContextCompat.getColor(this, R.color.app_main_color));
        } else {
            ivHome.setImageResource(R.drawable.ic_home);
            tvHome.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        }

        if (selectedImageView == ivCreator) {
            ivCreator.setImageResource(R.drawable.ic_creator_filled);
            tvCreator.setTextColor(ContextCompat.getColor(this, R.color.app_main_color));
        } else {
            ivCreator.setImageResource(R.drawable.ic_creator);
            tvCreator.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        }

        if (selectedImageView == ivSubs) {
            ivSubs.setImageResource(R.drawable.ic_subscriptions_filled);
            tvSubs.setTextColor(ContextCompat.getColor(this, R.color.app_main_color));
        } else {
            ivSubs.setImageResource(R.drawable.ic_subscriptions);
            tvSubs.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        }

        if (selectedImageView == ivProfile) {
            ivProfile.setImageResource(R.drawable.ic_person_filled);
            tvProfile.setTextColor(ContextCompat.getColor(this, R.color.app_main_color));
        } else {
            ivProfile.setImageResource(R.drawable.ic_person);
            tvProfile.setTextColor(ContextCompat.getColor(this, android.R.color.black));
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
        return button;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (firestoreListener != null) firestoreListener.remove();
    }
}
