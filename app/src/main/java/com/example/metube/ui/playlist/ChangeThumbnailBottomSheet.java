package com.example.metube.ui.playlist;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.example.metube.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class ChangeThumbnailBottomSheet extends BottomSheetDialogFragment {

    public interface OnOptionClickListener {
        void onChooseFromLibrary();
        void onTakePhoto();
    }

    private OnOptionClickListener listener;

    public ChangeThumbnailBottomSheet(OnOptionClickListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.layout_bottom_sheet_change_thumbnail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.btn_choose_library).setOnClickListener(v -> {
            listener.onChooseFromLibrary();
            dismiss();
        });

        view.findViewById(R.id.btn_take_photo).setOnClickListener(v -> {
            listener.onTakePhoto();
            dismiss();
        });
    }
}