package com.example.metube.ui.home;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.example.metube.R;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.metube.ui.home.VideoAdapter;
import com.example.metube.model.Video;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import android.content.Intent;
import androidx.fragment.app.Fragment;
import android.widget.ScrollView;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.example.metube.ui.search.SearchActivity;
import com.example.metube.ui.upload.UploadActivity;


public class HomepageActivity extends AppCompatActivity{

    private List<String> topics;
    private LinearLayout topicContainer;
    private View currentlySelectedTopic = null;
    private RecyclerView recyclerViewVideos;
    private VideoAdapter videoAdapter;
    private List<Video> videoList;
    private FirebaseFirestore db;
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

        // ÁNH XẠ TẤT CẢ CÁC VIEW CẦN THIẾT ---
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

        setupBottomNav();
        setupTopBarActions();
        // Mặc định hiển thị Home khi khởi động
        showHomeContent();
    }
    private void setupTopBarActions() {
        if (topBar == null) return;

            // Nút chuông thông báo
        topBar.findViewById(R.id.btnNotifications).setOnClickListener(v -> {
            loadFragment(new NotificationsFragment());
            switchToNotificationsView();
        });

            // Nút quay lại (Back)
        topBar.findViewById(R.id.btnBack).setOnClickListener(v -> {
            showHomeContent();
        });
        View searchButton = topBar.findViewById(R.id.btnSearch);
        if (searchButton != null) {
            searchButton.setOnClickListener(v -> {
                // Khi nhấn nút tìm kiếm, mở SearchActivity
                Intent intent = new Intent(HomepageActivity.this, SearchActivity.class);
                startActivity(intent);
            });
        }
    }
    private void setupBottomNav() {
        View bottomNavView = findViewById(R.id.bottomNav);
        if (bottomNavView == null) return;

        // Ánh xạ các tab và ImageView
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

        // Tạo danh sách để dễ quản lý
        tabImageViews = Arrays.asList(ivHome, ivCreator, ivSubs, ivProfile);
        tabTextViews = Arrays.asList(tvHome, tvCreator, tvSubs, tvProfile);

        // Đặt sự kiện click
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

        tabHome.setOnClickListener(v -> {
            updateTabSelection(ivHome);
            showHomeContent();
        });
        // Mặc định chọn Home
        updateTabSelection(ivHome);

    }

    // Hàm xử lý hiệu ứng filled
    private void updateTabSelection(View selectedImageView) {
        if (selectedImageView == ivHome) {
            ivHome.setImageResource(R.drawable.ic_home_filled);
            tvHome.setTextColor(ContextCompat.getColor(this, R.color.app_main_color));
        } else {
            ivHome.setImageResource(R.drawable.ic_home);
            tvHome.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        }

        // ---- CẬP NHẬT TAB CREATOR ----
        if (selectedImageView == ivCreator) {
            ivCreator.setImageResource(R.drawable.ic_creator_filled);
            tvCreator.setTextColor(ContextCompat.getColor(this, R.color.app_main_color));
        } else {
            ivCreator.setImageResource(R.drawable.ic_creator);
            tvCreator.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        }

        // ---- CẬP NHẬT TAB SUBSCRIPTIONS ----
        if (selectedImageView == ivSubs) {
            ivSubs.setImageResource(R.drawable.ic_subscriptions_filled);
            tvSubs.setTextColor(ContextCompat.getColor(this, R.color.app_main_color));
        } else {
            ivSubs.setImageResource(R.drawable.ic_subscriptions);
            tvSubs.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        }

        // ---- CẬP NHẬT TAB PROFILE ----
        if (selectedImageView == ivProfile) {
            ivProfile.setImageResource(R.drawable.ic_person_filled);
            tvProfile.setTextColor(ContextCompat.getColor(this, R.color.app_main_color));
        } else {
            ivProfile.setImageResource(R.drawable.ic_person);
            tvProfile.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        }
    }

    // Hàm hiển thị nội dung Home
    private void showHomeContent() {
        homeContentContainer.setVisibility(View.VISIBLE);
        fragmentContainer.setVisibility(View.GONE);
        switchToHomeView();
    }

    // Hàm tải một Fragment
    private void loadFragment(Fragment fragment) {
        homeContentContainer.setVisibility(View.GONE);
        fragmentContainer.setVisibility(View.VISIBLE);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }
    // Hàm để chuyển Top Bar sang giao diện "Home"
    private void switchToHomeView() {
        if (homeTitleLayout != null && notificationsTitleLayout != null) {
            homeTitleLayout.setVisibility(View.VISIBLE);
            notificationsTitleLayout.setVisibility(View.GONE);
        }
    }
    // Hàm để chuyển Top Bar sang giao diện "Notifications"
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
        // ... thêm các topic khác ...
        return dummyTopics;
    }
    private void setupTopicFilters(List<String> topicList) {
        // Luôn xóa các nút cũ trước khi thêm mới.
        topicContainer.removeAllViews();
        currentlySelectedTopic = null;

        // Tạo và thêm nút "All" đầu tiên.
        Button allButton = createTopicButton("All");
        allButton.setSelected(true); // Mặc định chọn "All".
        currentlySelectedTopic = allButton;
        topicContainer.addView(allButton);

        // Dùng vòng lặp để tạo các nút còn lại từ danh sách.
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
        });
        return button;
    }
    private void setupRecyclerView() {
        videoList = new ArrayList<>();
        videoAdapter = new VideoAdapter(this, videoList, null);
        recyclerViewVideos.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewVideos.setAdapter(videoAdapter);
    }
    private void fetchVideosFromFirestore() {
        // Truy vấn collection "videos", sắp xếp theo createdAt giảm dần (mới nhất trước)
        db.collection("videos")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    videoList.clear(); // Xóa dữ liệu cũ
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        // Chuyển đổi mỗi document thành một đối tượng Video
                        Video video = document.toObject(Video.class);
                        videoList.add(video);
                    }
                    // Cập nhật adapter với dữ liệu mới
                    videoAdapter.setVideos(videoList);
                })
                .addOnFailureListener(e -> {
                    Log.w("HomePageActivity", "Error getting documents.", e);
                });
    }
}