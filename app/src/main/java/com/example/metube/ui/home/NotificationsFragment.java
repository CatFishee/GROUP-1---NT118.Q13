package com.example.metube.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;
import androidx.fragment.app.Fragment;
import com.example.metube.R;

public class NotificationsFragment extends Fragment {
    private View currentlySelectedButton = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notifications, container, false);
    }
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Tìm đến "khay chứa"
        LinearLayout filterContainer = view.findViewById(R.id.filter_container);

        // Dùng "nhà máy" để tạo 2 nút
        Button btnAll = createFilterButton("All");
        Button btnMentions = createFilterButton("Mentions");

        // Thêm 2 nút vào "khay"
        filterContainer.addView(btnAll);
        filterContainer.addView(btnMentions);

        // Mặc định chọn nút "All" khi vào màn hình
        btnAll.setSelected(true);

        currentlySelectedButton = btnAll;
    }


    private Button createFilterButton(String text) {
        // Sử dụng requireContext() vì đây là Fragment
        Button button = new Button(requireContext(), null, 0, R.style.Widget_App_TopicButton);
        button.setText(text);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMarginEnd(20);
        button.setLayoutParams(params);

        // Logic click y hệt của bạn
        button.setOnClickListener(v -> {
            if (currentlySelectedButton != null) {
                currentlySelectedButton.setSelected(false);
            }
            v.setSelected(true);
            currentlySelectedButton = v;
        });

        return button;
    }
}
