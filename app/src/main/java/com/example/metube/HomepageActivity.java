package com.example.metube;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;
import com.example.metube.R;

// !! QUAN TRỌNG: Đảm bảo bạn không có dòng "import android.R;" ở đây.

public class HomepageActivity extends AppCompatActivity {

    private List<String> topics;
    private LinearLayout topicContainer;
    private View currentlySelectedTopic = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.homepage);

        // 1. Ánh xạ "khay chứa" các nút từ file XML.
        topicContainer = findViewById(R.id.topicContainer);

        // 2. Tạo dữ liệu mẫu (Sau này bạn sẽ lấy từ database/API).
        topics = new ArrayList<>();
        topics.add("Gaming");
        topics.add("Music");
        topics.add("Live");
        topics.add("Podcast");
        topics.add("Learning");
        topics.add("News");
        topics.add("Sports");
        topics.add("testnhaaaaaaaa");

        // 3. Gọi hàm để bắt đầu vẽ các nút lên giao diện.
        setupTopicFilters(topics);
    }

    /**
     * Hàm này sẽ xóa các nút cũ và tạo lại toàn bộ thanh filter.
     * @param topicList Danh sách các topic cần hiển thị.
     */
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

    /**
     * Hàm trợ giúp, tạo ra một Button hoàn chỉnh với style và sự kiện click.
     * @param text Chữ hiển thị trên nút.
     * @return Một đối tượng Button đã được cấu hình.
     */
    private Button createTopicButton(String text) {

        // THAY ĐỔI QUAN TRỌNG NHẤT:
        // Tạo một Button mới và ra lệnh cho nó sử dụng "bản thiết kế" từ styles.xml
        Button button = new Button(this, null, 0, R.style.Widget_App_TopicButton);
        button.setText(text);

        // VÌ TẤT CẢ CÁC THUỘC TÍNH ĐÃ NẰM TRONG STYLE,
        // CHÚNG TA CÓ THỂ XÓA CÁC DÒNG CODE CẤU HÌNH KÍCH THƯỚC VÀ MÀU SẮC Ở ĐÂY:
        // button.setAllCaps(false); <--- Đã có trong style
        // button.setTextSize(13f); <--- Đã có trong style
        // button.setBackgroundResource(...); <--- Đã có trong style
        // button.setTextColor(...); <--- Đã có trong style
        // button.setPadding(...); <--- Đã có trong style

        // Phần code về vị trí (margin) và sự kiện click vẫn giữ nguyên
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