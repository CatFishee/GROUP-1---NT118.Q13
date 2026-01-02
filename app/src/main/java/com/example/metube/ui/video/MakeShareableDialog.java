package com.example.metube.ui.video;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.metube.R;

public class MakeShareableDialog extends Dialog {

    public interface OnVisibilitySelectedListener {
        void onVisibilitySelected(String visibility);
    }

    private OnVisibilitySelectedListener listener;
    private RadioButton radioPublic, radioUnlisted;
    private TextView btnConfirm, btnCancel;
    private String selectedVisibility = null;
    private TextView tvTitle;

    public MakeShareableDialog(@NonNull Context context, OnVisibilitySelectedListener listener) {
        super(context);
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_make_shareable);


        // Làm nền dialog trong suốt để bo góc hiển thị đẹp
        if (getWindow() != null) {
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // Ánh xạ views
        radioPublic = findViewById(R.id.radio_public);
        radioUnlisted = findViewById(R.id.radio_unlisted);
        btnConfirm = findViewById(R.id.btn_confirm);
        btnCancel = findViewById(R.id.btn_cancel);
        tvTitle = findViewById(R.id.tv_dialog_title);

        LinearLayout optionPublic = findViewById(R.id.option_public);
        LinearLayout optionUnlisted = findViewById(R.id.option_unlisted);

        // Click vào option Public
        optionPublic.setOnClickListener(v -> selectOption("Public"));

        // Click vào option Unlisted
        optionUnlisted.setOnClickListener(v -> selectOption("Unlisted"));

        // Nút Cancel
        btnCancel.setOnClickListener(v -> dismiss());

        // Nút Confirm
        btnConfirm.setOnClickListener(v -> {
            if (selectedVisibility != null && listener != null) {
                listener.onVisibilitySelected(selectedVisibility);
            }
            dismiss();
        });

        // Không cho dismiss khi click ra ngoài
        setCancelable(false);
    }
    public void setTitle(String title) {
        if (tvTitle != null) {
            tvTitle.setText(title);
        }
    }
    private void selectOption(String visibility) {
        selectedVisibility = visibility;

        // Update radio buttons
        radioPublic.setChecked("Public".equals(visibility));
        radioUnlisted.setChecked("Unlisted".equals(visibility));

        // Enable nút Confirm và đổi màu
        btnConfirm.setEnabled(true);
        btnConfirm.setTextColor(getContext().getResources().getColor(R.color.app_main_color));
    }
}