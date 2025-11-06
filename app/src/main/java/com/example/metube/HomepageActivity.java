package com.example.metube;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;
import com.example.metube.R;

public class HomepageActivity extends AppCompatActivity {

    private List<String> topics;
    private LinearLayout topicContainer;
    private View currentlySelectedTopic = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.homepage);

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
}