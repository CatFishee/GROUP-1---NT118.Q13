package com.example.metube.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.metube.R;
import com.example.metube.model.HistoryItem;
import com.example.metube.model.User;
import com.example.metube.model.Video;
import com.example.metube.ui.history.HistoryActivity; // Giả sử bạn đã tạo Activity này
import com.example.metube.ui.history.HistoryAdapter;
import com.example.metube.ui.history.HistoryPreviewAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import de.hdodenhof.circleimageview.CircleImageView;

public class PersonFragment extends Fragment {

    private static final String TAG = "PersonFragment";

    // --- Khai báo các thành phần Giao diện ---
    private CircleImageView ivAvatar;
    private TextView tvUserName, tvChannelName, btnViewChannel;
    private View btnSwitchAccount, btnShareChannel;
    private TextView btnViewAllHistory;
    private RecyclerView rvHistory;
    // TODO: Khai báo RecyclerView cho Playlists khi bạn làm đến phần đó

    // --- Khai báo Adapter ---
    private HistoryPreviewAdapter historyPreviewAdapter;

    // --- Khai báo các biến dữ liệu ---
    private List<Video> historyVideoList = new ArrayList<>();

    // --- Khai báo các đối tượng Firebase ---
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Gắn layout fragment_person.xml vào Fragment
        return inflater.inflate(R.layout.fragment_person, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Gọi các hàm khởi tạo sau khi layout đã được tạo
        initFirebase();
        initViews(view);
        setupRecyclerViews();
        setupClickListeners();

        // Bắt đầu quá trình tải dữ liệu
        loadData();
    }

    /**
     * Khởi tạo các đối tượng Firebase.
     */
    private void initFirebase() {
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
    }

    /**
     * Ánh xạ các View từ file layout XML vào các biến Java.
     */
    private void initViews(View view) {
        ivAvatar = view.findViewById(R.id.iv_avatar);
        tvUserName = view.findViewById(R.id.tv_user_name);
        tvChannelName = view.findViewById(R.id.tv_channel_name);
        btnViewChannel = view.findViewById(R.id.btn_view_channel);
        btnSwitchAccount = view.findViewById(R.id.btn_switch_account);
        btnShareChannel = view.findViewById(R.id.btn_share_channel);
        btnViewAllHistory = view.findViewById(R.id.btn_view_all_history);
        rvHistory = view.findViewById(R.id.rv_history);
    }

    /**
     * Cài đặt LayoutManager và Adapter cho các RecyclerView.
     */
    private void setupRecyclerViews() {
        // RecyclerView cho Lịch sử xem (bản preview)
        historyPreviewAdapter = new HistoryPreviewAdapter(historyVideoList);
        rvHistory.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        rvHistory.setAdapter(historyPreviewAdapter);
    }

    /**
     * Gắn sự kiện click cho các nút.
     */
    private void setupClickListeners() {
        btnViewAllHistory.setOnClickListener(v -> {
            // isAdded() kiểm tra để đảm bảo Fragment vẫn đang tồn tại trước khi chuyển Activity
            if (isAdded()) {
                startActivity(new Intent(requireContext(), HistoryActivity.class));
            }
        });

        // Gắn sự kiện tạm thời cho các nút chưa có chức năng
        View.OnClickListener notImplementedListener = v ->
                Toast.makeText(getContext(), "Feature not implemented yet", Toast.LENGTH_SHORT).show();
        btnViewChannel.setOnClickListener(notImplementedListener);
        btnSwitchAccount.setOnClickListener(notImplementedListener);
        btnShareChannel.setOnClickListener(notImplementedListener);
    }

    /**
     * Hàm chính để bắt đầu tải tất cả dữ liệu cần thiết cho màn hình.
     */
    private void loadData() {
        loadUserInfo();
        loadHistoryPreview();
        // TODO: Gọi hàm loadPlaylistsPreview() khi bạn làm chức năng đó
    }

    /**
     * Lấy thông tin của người dùng đang đăng nhập từ Firestore và hiển thị lên giao diện.
     */
    private void loadUserInfo() {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) {
            tvUserName.setText("Sign In");
            tvChannelName.setVisibility(View.GONE);
            btnViewChannel.setVisibility(View.GONE);
            return;
        }

        firestore.collection("users").document(firebaseUser.getUid()).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (isAdded() && documentSnapshot.exists()) {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null) {
                            // 1. Hiển thị tên
                            tvUserName.setText(user.getName());

                            // 2. Tạo và hiển thị tên kênh (@username)
                            String channelHandle = "@" + user.getName().replaceAll("\\s+", "").toLowerCase();
                            tvChannelName.setText(channelHandle);

                            // 3. Hiển thị ảnh đại diện
                            String avatarUrl = user.getProfileURL();
                            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                                Glide.with(this)
                                        .load(avatarUrl)
                                        .placeholder(R.drawable.ic_person_placeholder) // Cần tạo ảnh này
                                        .into(ivAvatar);
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error fetching user info", e));
    }

    /**
     * Tải một danh sách ngắn các video đã xem gần đây để hiển thị preview.
     */
    private void loadHistoryPreview() {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) {
            Log.d(TAG, "❌ User not logged in");
            return;
        }
        Log.d(TAG, "🔍 Querying history for userID: " + firebaseUser.getUid());

        // B1: Lấy 10 bản ghi lịch sử mới nhất
        firestore.collection("watchHistory")
                .whereEqualTo("userID", firebaseUser.getUid())
                .orderBy("watchedAt", Query.Direction.DESCENDING)
                .limit(10)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Log.d(TAG, "✅ Query successful. Documents found: " + querySnapshot.size());
                    if (querySnapshot.isEmpty()) {
                        Log.d(TAG, "📭 No history items in database");
                        return;
                    }

                    // Lấy ra danh sách các videoId theo đúng thứ tự đã xem
                    List<String> videoIds = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Log.d(TAG, "📄 Document ID: " + doc.getId());
                        Log.d(TAG, "📄 Document data: " + doc.getData());

                        HistoryItem item = doc.toObject(HistoryItem.class);

                        Log.d(TAG, "   - Parsed videoID: " + item.getVideoID());
                        Log.d(TAG, "   - Parsed userID: " + item.getUserID());
                        Log.d(TAG, "   - Parsed watchedAt: " + item.getWatchedAt());

                        if (item.getVideoID() != null) {
                            videoIds.add(item.getVideoID());
                        }
                        else {
                            Log.w(TAG, "⚠️ videoID is NULL for document: " + doc.getId());
                        }
                    }
                    Log.d(TAG, "🎬 Total videoIds to fetch: " + videoIds.size());
                    if (!videoIds.isEmpty()) {
                        // B2: Dùng danh sách videoId để lấy thông tin chi tiết của các video đó
                        fetchVideosByIdsForPreview(videoIds);
                    } else {
                        Log.w(TAG, "❌ No valid videoIds found");
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error fetching history preview", e));
    }

    /**
     * Lấy thông tin chi tiết của một danh sách video dựa vào ID của chúng.
     */
    private void fetchVideosByIdsForPreview(List<String> orderedVideoIds) {
        firestore.collection("videos").whereIn(FieldPath.documentId(), orderedVideoIds)
                .get()
                .addOnSuccessListener(videoSnapshots -> {
                    if (isAdded()) {
                        // Tạo một bản đồ để tra cứu video theo ID
                        Map<String, Video> videoMap = new HashMap<>();
                        for (Video video : videoSnapshots.toObjects(Video.class)) {
                            videoMap.put(video.getVideoID(), video);
                        }

                        // Sắp xếp lại danh sách video theo đúng thứ tự đã xem ban đầu
                        historyVideoList.clear();
                        for (String videoId : orderedVideoIds) {
                            if (videoMap.containsKey(videoId)) {
                                historyVideoList.add(videoMap.get(videoId));
                            }
                        }

                        // Báo cho adapter biết dữ liệu đã thay đổi để cập nhật RecyclerView
                        historyPreviewAdapter.notifyDataSetChanged();
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error fetching videos by IDs", e));
    }
}