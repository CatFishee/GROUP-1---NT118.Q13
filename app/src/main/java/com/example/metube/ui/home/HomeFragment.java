package com.example.metube.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import java.util.List;

import com.example.metube.R;

public class HomeFragment extends Fragment {

    // Giả sử bạn có biến này trong class của mình
    private List<String> topics;
    private LinearLayout topicContainer;
    private View currentlySelectedTopic = null; // Biến để lưu nút đang được chọn

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate layout cho Fragment này
        return inflater.inflate(R.layout.homepage, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Lấy container từ layout đã include, dùng "view.findViewById"
        topicContainer = view.findViewById(R.id.topicContainer);

        // --- GIẢ LẬP DỮ LIỆU ---
        // Ban đầu, topics có thể là null hoặc rỗng
        // topics = new ArrayList<>();
        // topics.add("Gaming");
        // topics.add("Music");
        // ...

        // Gọi hàm để tạo các nút
        setupTopicFilters(topics);
    }

    private void setupTopicFilters(List<String> topicList) {
        // Luôn xóa các view cũ trước khi thêm mới
        topicContainer.removeAllViews();
        currentlySelectedTopic = null;

        // 1. Luôn thêm nút "All"
        Button allButton = createTopicButton("All");
        allButton.setSelected(true); // Mặc định chọn nút "All"
        currentlySelectedTopic = allButton;
        topicContainer.addView(allButton);

        // 2. Kiểm tra nếu danh sách topic không rỗng thì mới thêm
        if (topicList != null && !topicList.isEmpty()) {
            for (String topic : topicList) {
                Button topicButton = createTopicButton(topic);
                topicContainer.addView(topicButton);
            }
        }
    }

    // Hàm trợ giúp để tạo một nút theo style đã định nghĩa
    private Button createTopicButton(String text) {
        // Dùng requireContext() để lấy Context trong Fragment
        Button button = new Button(requireContext(), null, 0, com.google.android.material.R.style.Widget_MaterialComponents_Button_TextButton);
        button.setText(text);
        button.setAllCaps(false);
        button.setBackgroundResource(R.drawable.selector_topic_button_background);
        button.setTextColor(getResources().getColorStateList(R.color.selector_topic_button_text, requireActivity().getTheme()));
        button.setPadding(40, 0, 40, 0); // Padding ngang

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 16, 0); // Khoảng cách giữa các nút
        button.setLayoutParams(params);

        // Xử lý sự kiện click
        button.setOnClickListener(v -> {
            if (currentlySelectedTopic != null) {
                currentlySelectedTopic.setSelected(false); // Bỏ chọn nút cũ
            }
            v.setSelected(true); // Chọn nút mới được click
            currentlySelectedTopic = v; // Cập nhật nút đang được chọn

            // TODO: Thêm logic filter dữ liệu của bạn ở đây
        });

        return button;
    }
}