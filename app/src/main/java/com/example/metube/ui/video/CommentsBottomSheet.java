package com.example.metube.ui.video;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.metube.R;
import com.example.metube.model.Comment;
import com.example.metube.model.User;
import com.example.metube.ui.notifications.NotificationHelper;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class CommentsBottomSheet extends BottomSheetDialogFragment {

    private String videoId;
    private String uploaderID; // THÊM BIẾN NÀY
    private String videoThumb;
    private RecyclerView recyclerView;
    private EditText etInput;
    private ImageButton btnSend;
    private CircleImageView ivCurrentUserAvatar;
    private TextView tvTitle;

    private CommentsAdapter adapter;
    private List<Comment> commentsList;
    private FirebaseFirestore firestore;
    private FirebaseAuth auth;

    public static CommentsBottomSheet newInstance(String videoId, String uploaderID, String videoThumb) {
        CommentsBottomSheet fragment = new CommentsBottomSheet();
        Bundle args = new Bundle();
        args.putString("VIDEO_ID", videoId);
        args.putString("UPLOADER_ID", uploaderID); // Lưu uploaderID
        args.putString("VIDEO_THUMB", videoThumb); // Lưu videoThumb
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Use the layout we created in Step 2
        View view = inflater.inflate(R.layout.fragment_comments_bottom_sheet, container, false);

        if (getArguments() != null) {
            videoId = getArguments().getString("VIDEO_ID");
            uploaderID = getArguments().getString("UPLOADER_ID");
            videoThumb = getArguments().getString("VIDEO_THUMB");
        }

        firestore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        initViews(view);
        loadCurrentUserAvatar();
        loadComments();

        return view;
    }

    private void initViews(View view) {
        recyclerView = view.findViewById(R.id.rv_comments);
        etInput = view.findViewById(R.id.et_comment_input);
        btnSend = view.findViewById(R.id.btn_send_comment);
        ivCurrentUserAvatar = view.findViewById(R.id.iv_current_user_avatar);
        tvTitle = view.findViewById(R.id.tv_sheet_title);

        commentsList = new ArrayList<>();
        adapter = new CommentsAdapter(commentsList);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        btnSend.setOnClickListener(v -> postComment());
    }

    private void loadCurrentUserAvatar() {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            firestore.collection("users").document(user.getUid()).get()
                    .addOnSuccessListener(snapshot -> {
                        if (snapshot.exists()) {
                            String url = snapshot.getString("profileURL");
                            if (url != null && !url.isEmpty()) {
                                Glide.with(this).load(url).into(ivCurrentUserAvatar);
                            }
                        }
                    });
        }
    }

    private void loadComments() {
        if (videoId == null) return;

        firestore.collection("comments")
                .whereEqualTo("videoID", videoId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        // NOTE: If this fails, check Logcat for a link to create a Firestore Index!
                        return;
                    }

                    if (value != null) {
                        commentsList.clear();
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            Comment c = doc.toObject(Comment.class);
                            // Ensure ID is set
                            c.setCommentID(doc.getId());
                            commentsList.add(c);
                        }
                        adapter.notifyDataSetChanged();
                        tvTitle.setText("Comments (" + commentsList.size() + ")");
                    }
                });
    }

    private void postComment() {
        String text = etInput.getText().toString().trim();
        FirebaseUser user = auth.getCurrentUser();

        if (text.isEmpty()) return;
        if (user == null) {
            Toast.makeText(getContext(), "Please login to comment", Toast.LENGTH_SHORT).show();
            return;
        }

        // Disable button to prevent double-click
        btnSend.setEnabled(false);

        String newCommentId = firestore.collection("comments").document().getId();
        Comment comment = new Comment(
                newCommentId,
                videoId,
                user.getUid(),
                null,
                text,
                Timestamp.now()
        );

        firestore.collection("comments").document(newCommentId).set(comment)
                .addOnSuccessListener(aVoid -> {
                    etInput.setText("");
                    btnSend.setEnabled(true);

                    // ✅ GỬI THÔNG BÁO CHO CHỦ KÊNH
                    // Lấy tên người comment (nếu không có displayName thì lấy email hoặc "Someone")
                    String senderName = (user.getDisplayName() != null && !user.getDisplayName().isEmpty())
                            ? user.getDisplayName() : "Someone";

                    NotificationHelper.notifyOwnerAboutNewComment(
                            uploaderID,   // ID chủ kênh (nhận từ VideoActivity)
                            videoId,      // ID video hiện tại
                            senderName,   // Tên người gửi comment
                            text,         // Nội dung comment
                            videoThumb    // Ảnh video
                    );

                    Toast.makeText(getContext(), "Comment posted", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    btnSend.setEnabled(true);
                    Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}