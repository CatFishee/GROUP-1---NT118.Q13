package com.example.metube.ui.playlist;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.metube.R;
import com.example.metube.model.Playlist;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
public class CreatePlaylistBottomSheet extends DialogFragment {
    private TextInputEditText etTitle;
    private TextView tvVisibility;
    private Button btnCreate, btnCancel;
    private LinearLayout layoutVisibilitySelector;

    // Mặc định là Private
    private String currentVisibility = "Private";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.layout_bottom_sheet_create_playlist, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        etTitle = view.findViewById(R.id.et_playlist_title);
        tvVisibility = view.findViewById(R.id.tv_selected_visibility);
        btnCreate = view.findViewById(R.id.btn_create);
        btnCancel = view.findViewById(R.id.btn_cancel);
        layoutVisibilitySelector = view.findViewById(R.id.layout_visibility_selector);

        // 1. Logic nút Cancel
        btnCancel.setOnClickListener(v -> dismiss());

        // 2. Logic nhập text -> Enable nút Create
        etTitle.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Chỉ cho bấm Create nếu đã nhập tiêu đề
                btnCreate.setEnabled(s.toString().trim().length() > 0);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // 3. Logic chọn Visibility (Mở BottomSheet thứ 2)
        layoutVisibilitySelector.setOnClickListener(v -> showVisibilitySelector());

        // 4. Logic nút Create -> Lưu lên Firestore
        btnCreate.setOnClickListener(v -> createPlaylist());
    }
    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            Window window = getDialog().getWindow();

            // Set chiều ngang là MATCH_PARENT (để margin trong XML có tác dụng)
            // Set chiều dọc là WRAP_CONTENT (để ôm vừa nội dung)
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

            // Set nền cửa sổ trong suốt (để thấy bo góc của file drawable)
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private void showVisibilitySelector() {
        // Tạo BottomSheetDialog thứ 2 thủ công để dễ kiểm soát
        BottomSheetDialog visibilityDialog = new BottomSheetDialog(requireContext());
        View sheetView = LayoutInflater.from(getContext()).inflate(R.layout.layout_bottom_sheet_visibility, null);
        visibilityDialog.setContentView(sheetView);

        // Xử lý sự kiện chọn từng dòng
        sheetView.findViewById(R.id.option_public).setOnClickListener(v -> {
            updateVisibility("Public");
            visibilityDialog.dismiss();
        });

        sheetView.findViewById(R.id.option_unlisted).setOnClickListener(v -> {
            updateVisibility("Unlisted");
            visibilityDialog.dismiss();
        });

        sheetView.findViewById(R.id.option_private).setOnClickListener(v -> {
            updateVisibility("Private");
            visibilityDialog.dismiss();
        });

        visibilityDialog.show();
    }

    private void updateVisibility(String visibility) {
        this.currentVisibility = visibility;
        tvVisibility.setText(visibility);
    }

    private void createPlaylist() {
        String title = etTitle.getText().toString().trim();
        if (title.isEmpty()) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // Tạo ID mới
        String playlistId = db.collection("playlists").document().getId();

        // Tạo Object Playlist
        Playlist newPlaylist = new Playlist(
                playlistId,
                userId,
                title,
                currentVisibility.toUpperCase(), // Lưu dạng PUBLIC, PRIVATE...
                new ArrayList<>() // List video rỗng ban đầu
        );

        // Lưu vào Firestore
        db.collection("playlists").document(playlistId).set(newPlaylist)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "Playlist created", Toast.LENGTH_SHORT).show();
                    dismiss(); // Đóng dialog
                    // TODO: Refresh danh sách playlist ở PersonFragment nếu cần
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Error creating playlist", Toast.LENGTH_SHORT).show();
                });
    }

}
