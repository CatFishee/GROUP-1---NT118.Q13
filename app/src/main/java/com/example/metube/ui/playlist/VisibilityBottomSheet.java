package com.example.metube.ui.playlist;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.metube.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class VisibilityBottomSheet extends BottomSheetDialogFragment {

    // Interface để trả kết quả về (Public/Private/Unlisted)
    public interface OnVisibilitySelectedListener {
        void onVisibilitySelected(String visibility);
    }

    private OnVisibilitySelectedListener listener;

    public VisibilityBottomSheet(OnVisibilitySelectedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Sử dụng lại file layout bạn đã có
        return inflater.inflate(R.layout.layout_bottom_sheet_visibility, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Xử lý sự kiện click
        view.findViewById(R.id.option_public).setOnClickListener(v -> selectVisibility("Public"));
        view.findViewById(R.id.option_unlisted).setOnClickListener(v -> selectVisibility("Unlisted"));
        view.findViewById(R.id.option_private).setOnClickListener(v -> selectVisibility("Private"));
    }

    private void selectVisibility(String visibility) {
        if (listener != null) {
            listener.onVisibilitySelected(visibility);
        }
        dismiss(); // Đóng bảng sau khi chọn
    }
}