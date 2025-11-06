package com.example.metube.ui.home;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;
import com.example.metube.R;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.metube.ui.home.VideoAdapter;
import com.example.metube.model.Video;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;


public class HomepageActivity extends AppCompatActivity {

    private List<String> topics;
    private LinearLayout topicContainer;
    private View currentlySelectedTopic = null;

    private RecyclerView recyclerViewVideos;
    private VideoAdapter videoAdapter;
    private List<Video> videoList;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_homepage);

        topicContainer = findViewById(R.id.topicContainer);

        topics = new ArrayList<>();
        topics.add("Gaming");
        topics.add("Music");
        topics.add("Live");
        topics.add("Podcast");
        topics.add("Learning");
        topics.add("News");
        topics.add("Sports");
        topics.add("testnhaaaaaaaa");

        setupTopicFilters(topics);
        setupRecyclerView();
        fetchVideosFromFirestore();
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
        recyclerViewVideos = findViewById(R.id.recyclerViewVideos);
        videoList = new ArrayList<>();

        // Khởi tạo adapter với context và một danh sách rỗng
        videoAdapter = new VideoAdapter(this, videoList);

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