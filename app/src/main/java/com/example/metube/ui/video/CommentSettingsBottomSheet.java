package com.example.metube.ui.video;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.metube.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class CommentSettingsBottomSheet extends BottomSheetDialogFragment {

    public interface OnCommentStatusListener {
        void onStatusSelected(boolean allowComments);
    }
    private OnCommentStatusListener listener;

    public CommentSettingsBottomSheet(OnCommentStatusListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Bạn có thể tạo 1 file layout mới có 2 dòng: "Allow all comments" và "Disable comments"
        // Hoặc tái sử dụng layout visibility nếu lười (nhưng nên tạo riêng cho chuẩn text)
        return inflater.inflate(R.layout.layout_bottom_sheet_comments, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.option_allow).setOnClickListener(v -> select(true));
        view.findViewById(R.id.option_disable).setOnClickListener(v -> select(false));
    }

    private void select(boolean allow) {
        if (listener != null) listener.onStatusSelected(allow);
        dismiss();
    }
}