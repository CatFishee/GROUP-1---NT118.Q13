package com.example.metube.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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
import com.example.metube.model.Playlist;
import com.example.metube.model.User;
import com.example.metube.model.Video;
import com.example.metube.ui.history.HistoryActivity; // Giả sử bạn đã tạo Activity này
import com.example.metube.ui.history.HistoryAdapter;
import com.example.metube.ui.history.HistoryMenuBottomSheet;
import com.example.metube.ui.history.HistoryPreviewAdapter;
import com.example.metube.ui.login.SwitchAccountDialog;
import com.example.metube.ui.playlist.CreatePlaylistBottomSheet;
import com.example.metube.ui.playlist.PlaylistsActivity;
import com.example.metube.ui.playlist.PlaylistPreviewAdapter;
import com.example.metube.ui.profile.EditProfileActivity;
import com.example.metube.ui.video.YourVideosActivity;
import com.example.metube.utils.AccountUtil;
import com.example.metube.utils.ShareUtil;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.example.metube.R;
import com.example.metube.ui.userstat.UserStatisticsActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import de.hdodenhof.circleimageview.CircleImageView;

public class PersonFragment extends Fragment implements HistoryMenuBottomSheet.HistoryMenuListener {

    private static final String TAG = "PersonFragment";

    // --- Khai báo các thành phần Giao diện ---
    private CircleImageView ivAvatar;
    private TextView tvUserName, tvChannelName, btnViewChannel, btnViewAllPlaylists;
    private ImageView btnAddPlaylist, ivEditProfile;
    private View btnSwitchAccount, btnShareChannel;
    private TextView btnViewAllHistory, btnDownloads, btnYourVideos, btnStats;
    private RecyclerView rvHistory;
    private User mUser;

    // --- Khai báo Adapter ---
    private HistoryPreviewAdapter historyPreviewAdapter;

    // --- Khai báo các biến dữ liệu ---
    private List<Video> historyVideoList = new ArrayList<>();
    private RecyclerView rvPlaylistsPreview;
    private PlaylistPreviewAdapter playlistPreviewAdapter;
    private List<Playlist> playlistPreviews = new ArrayList<>();

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
//        loadData();
        loadUserInfo();
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
        btnDownloads = view.findViewById(R.id.btn_downloads);
        btnYourVideos = view.findViewById(R.id.btn_your_videos);
        btnStats = view.findViewById(R.id.btn_statistics);
        rvHistory = view.findViewById(R.id.rv_history);
        btnAddPlaylist = view.findViewById(R.id.btn_add_playlist);
        btnViewAllPlaylists = view.findViewById(R.id.btn_view_all_playlists);
        rvPlaylistsPreview = view.findViewById(R.id.rv_playlists_preview);
        ivEditProfile = view.findViewById(R.id.iv_edit_profile);
    }

    /**
     * Cài đặt LayoutManager và Adapter cho các RecyclerView.
     */
    private void setupRecyclerViews() {
        // RecyclerView cho Lịch sử xem (bản preview)
        historyPreviewAdapter = new HistoryPreviewAdapter(historyVideoList);
        historyPreviewAdapter.setOnItemMoreClickListener((video, position) -> {
            HistoryMenuBottomSheet bottomSheet = new HistoryMenuBottomSheet(
                    video,
                    video.getHistoryDocId(),
                    position,
                    HistoryMenuBottomSheet.TYPE_HISTORY,  // ✅ THÊM: Loại menu
                    "",                                    // ✅ THÊM: Playlist title (rỗng vì đây là History)
                    this                                   // Listener
            );
            bottomSheet.show(getParentFragmentManager(), "HistoryMenu");
        });
        rvHistory.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        rvHistory.setAdapter(historyPreviewAdapter);
        playlistPreviewAdapter = new PlaylistPreviewAdapter(playlistPreviews);
        rvPlaylistsPreview.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        rvPlaylistsPreview.setAdapter(playlistPreviewAdapter);
    }

    /**
     * Gắn sự kiện click cho các nút.
     */
    private void setupClickListeners() {
        btnViewAllHistory.setOnClickListener(v -> {
            // isAdded() kiểm tra để đảm bảo Fragment vẫn đang tồn tại trước khi chuyển Activity
            if (isAdded()) {
                Intent intent = new Intent(requireContext(), HistoryActivity.class);
                startActivity(intent);
            }
        });
        btnAddPlaylist.setOnClickListener(v -> {
            CreatePlaylistBottomSheet dialog = new CreatePlaylistBottomSheet();
            dialog.setOnPlaylistCreatedListener(() -> {
                loadPlaylistsPreview();
            });
            dialog.show(getParentFragmentManager(), "CreatePlaylistDialog");
        });
        btnViewAllPlaylists.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), PlaylistsActivity.class);
            startActivity(intent);
        });
        btnShareChannel.setOnClickListener(v -> {
            if (mUser != null) {
                ShareUtil.shareChannel(requireContext(), mUser.getName());
            } else {
                Toast.makeText(getContext(), "Loading profile...", Toast.LENGTH_SHORT).show();
            }
        });

        btnSwitchAccount.setOnClickListener(v -> {
            if (mUser != null) {
                SwitchAccountDialog dialog = new SwitchAccountDialog(mUser);
                dialog.show(getParentFragmentManager(), "SwitchAccountDialog");
            }
        });

        // Gắn sự kiện tạm thời cho các nút chưa có chức năng
        btnViewChannel.setOnClickListener(v -> {
            if (mUser != null && mUser.getUserID() != null) {
                // Mở CreatorProfileActivity
                Intent intent = new Intent(requireContext(), com.example.metube.ui.contentcreator.CreatorProfileActivity.class);
                // Truyền ID của chính người dùng hiện tại sang
                intent.putExtra("creator_id", mUser.getUserID());
                startActivity(intent);
            } else {
                Toast.makeText(getContext(), "User data is still loading...", Toast.LENGTH_SHORT).show();
            }
        });

        btnDownloads.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), com.example.metube.ui.downloads.DownloadsActivity.class);
            startActivity(intent);
        });
        btnYourVideos.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), YourVideosActivity.class);
            startActivity(intent);
        });
        btnStats.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), com.example.metube.ui.userstat.UserStatisticsActivity.class);
            startActivity(intent);
        });
        ivEditProfile.setOnClickListener(v -> {
            if (mUser != null) {
                // Giả sử bạn đặt tên activity là EditProfileActivity
                Intent intent = new Intent(requireContext(), EditProfileActivity.class);

                // Bạn nên truyền thông tin user hiện tại sang để đỡ phải load lại bên kia
                intent.putExtra("user_data", mUser);

                startActivity(intent);
            } else {
                Toast.makeText(getContext(), "User data not ready", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Hàm chính để bắt đầu tải tất cả dữ liệu cần thiết cho màn hình.
     */
    private void loadData() {
        loadUserInfo();
        loadHistoryPreview();
        loadPlaylistsPreview();
    }
    private void loadPlaylistsPreview() {
        if (auth.getCurrentUser() == null) return;

        firestore.collection("playlists")
                .whereEqualTo("ownerId", auth.getCurrentUser().getUid())
                .limit(10) // Lấy 10 cái mới nhất
                .get()
                .addOnSuccessListener(snap -> {
                    playlistPreviews.clear();
                    for (DocumentSnapshot doc : snap) {
                        playlistPreviews.add(doc.toObject(Playlist.class));
                    }
                    playlistPreviewAdapter.notifyDataSetChanged();
                });
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
                        mUser = documentSnapshot.toObject(User.class);
                        if (mUser != null) {
                            AccountUtil.saveUserToHistory(requireContext(), mUser);
                        }
                        if (mUser != null) {
                            // 1. Hiển thị tên
                            tvUserName.setText(mUser.getName());

                            // 2. Tạo và hiển thị tên kênh (@username)
                            String channelHandle = "@" + mUser.getName().replaceAll("\\s+", "").toLowerCase();
                            tvChannelName.setText(channelHandle);

                            // 3. Hiển thị ảnh đại diện
                            String avatarUrl = mUser.getProfileURL();
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
            Log.d(TAG, "User not logged in");
            return;
        }
        Log.d(TAG, "Querying history for userID: " + firebaseUser.getUid());

        // B1: Lấy 10 bản ghi lịch sử mới nhất
        firestore.collection("watchHistory")
                .whereEqualTo("userID", firebaseUser.getUid())
                .orderBy("watchedAt", Query.Direction.DESCENDING)
                .limit(10)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Log.d(TAG, "Query successful. Documents found: " + querySnapshot.size());
                    if (querySnapshot.isEmpty()) {
                        historyVideoList.clear();
                        historyPreviewAdapter.notifyDataSetChanged();
                        Log.d(TAG, "History is empty, list cleared.");
                        return;
                    }

                    // Lấy ra danh sách các videoId theo đúng thứ tự đã xem
                    List<String> videoIds = new ArrayList<>();
                    Set<String> uniqueIds = new HashSet<>();
                    Map<String, Long> progressMap = new HashMap<>();
                    Map<String, String> historyIdMap = new HashMap<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        HistoryItem item = doc.toObject(HistoryItem.class);
                        if (item != null && item.getVideoID() != null) {
                            if (!uniqueIds.contains(item.getVideoID())) {
                                uniqueIds.add(item.getVideoID());
                                videoIds.add(item.getVideoID());

                                // Lưu resume position
                                Long pos = doc.getLong("resumePosition");
                                if (pos != null) progressMap.put(item.getVideoID(), pos);

                                // LƯU HISTORY DOC ID
                                historyIdMap.put(item.getVideoID(), doc.getId());
                            }
                        }
                    }
                    if (!videoIds.isEmpty()) {
                        fetchVideosByIdsForPreview(videoIds, progressMap, historyIdMap);
                    } else {
                        // Trường hợp hiếm: Có docs nhưng không lọc được ID nào -> Cũng xóa list
                        historyVideoList.clear();
                        historyPreviewAdapter.notifyDataSetChanged();
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error loading history IDs", e));
    }

    /**
     * Lấy thông tin chi tiết của một danh sách video dựa vào ID của chúng.
     */
    private void fetchVideosByIdsForPreview(List<String> orderedVideoIds, Map<String, Long> progressMap, Map<String, String> historyIdMap) {
        firestore.collection("videos")
                .whereIn(FieldPath.documentId(), orderedVideoIds)
                .get()
                .addOnSuccessListener(videoSnapshots -> {
                    if (!isAdded()) return;

                    Map<String, Video> videoMap = new HashMap<>();
                    List<String> uploaderIds = new ArrayList<>();
                    Set<String> uniqueUploaderIds = new HashSet<>();

                    // Parse Video và gom uploaderID
                    for (DocumentSnapshot doc : videoSnapshots) {
                        Video video = doc.toObject(Video.class);
                        if (video != null) {
                            video.setVideoID(doc.getId()); // Quan trọng: set ID cho object
                            videoMap.put(doc.getId(), video);

                            if (video.getUploaderID() != null && !uniqueUploaderIds.contains(video.getUploaderID())) {
                                uniqueUploaderIds.add(video.getUploaderID());
                                uploaderIds.add(video.getUploaderID());
                            }
                        }
                    }

                    // Sắp xếp lại danh sách video theo đúng thứ tự lịch sử
                    List<Video> sortedVideos = new ArrayList<>();
                    for (String vid : orderedVideoIds) {
                        if (videoMap.containsKey(vid)) {
                            // 1. Lấy object video ra và gán vào biến 'video'
                            Video video = videoMap.get(vid);

                            // 2. Kiểm tra và gán progress
                            if (video != null) {
                                if (progressMap.containsKey(vid)) {
                                    video.setResumePosition(progressMap.get(vid));
                                }
                                if (historyIdMap.containsKey(vid)) video.setHistoryDocId(historyIdMap.get(vid));
                                // 3. Chỉ add vào list 1 lần duy nhất sau khi đã xử lý xong dữ liệu
                                sortedVideos.add(video);
                            }
                        }
                    }

                    // BƯỚC 3: Thay vì hiển thị ngay, hãy đi lấy tên người đăng!
                    if (!uploaderIds.isEmpty()) {
                        fetchUploaderNames(sortedVideos, uploaderIds);
                    } else {
                        // Trường hợp hiếm: Video không có người đăng
                        updateHistoryAdapter(sortedVideos);
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error fetching video details", e));
    }
    private void fetchUploaderNames(List<Video> videos, List<String> uploaderIds) {
        firestore.collection("users")
                .whereIn("userID", uploaderIds)
                .get()
                .addOnSuccessListener(userSnapshots -> {
                    if (!isAdded()) return;

                    Map<String, String> userMap = new HashMap<>();
                    for (DocumentSnapshot doc : userSnapshots) {
                        User user = doc.toObject(User.class);
                        if (user != null) {
                            // Lấy fullName làm tên hiển thị
                            userMap.put(user.getUserID(), user.getName());
                        }
                    }

                    // Gán tên vào từng video (vào trường @Exclude)
                    for (Video video : videos) {
                        String name = userMap.get(video.getUploaderID());
                        if (name != null) {
                            video.setUploaderName(name);
                        } else {
                            video.setUploaderName("Unknown User");
                        }
                    }

                    // Cuối cùng: Cập nhật giao diện
                    updateHistoryAdapter(videos);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error fetching uploader names", e));
    }
    private void updateHistoryAdapter(List<Video> videos) {
        historyVideoList.clear();
        historyVideoList.addAll(videos);
        historyPreviewAdapter.notifyDataSetChanged();
    }
    @Override
    public void onResume() {
        super.onResume();
        // Gọi hàm load history tại đây
        // Để mỗi khi quay lại màn hình này, nó sẽ tự tải lại dữ liệu mới nhất
        loadUserInfo();
        loadHistoryPreview();
        loadPlaylistsPreview();
    }

    @Override
    public void onRemoveFromHistory(String docId, int position) {
        if (docId == null) return;
        firestore.collection("watchHistory").document(docId).delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "Removed from history", Toast.LENGTH_SHORT).show();
                    // Xóa khỏi list hiển thị để UI mượt mà
                    if (position >= 0 && position < historyVideoList.size()) {
                        historyVideoList.remove(position);
                        historyPreviewAdapter.notifyItemRemoved(position);
                    }
                });
    }

    @Override
    public void onSaveToPlaylist(Video video) {
        // Mở bảng chọn playlist
        com.example.metube.ui.playlist.AddToPlaylistBottomSheet sheet =
                new com.example.metube.ui.playlist.AddToPlaylistBottomSheet(video);
        sheet.show(getParentFragmentManager(), "AddToPlaylist");
    }

    @Override
    public void onDownload(Video video) {
        com.example.metube.utils.DownloadUtil.downloadVideo(getContext(), video.getVideoURL(), video.getTitle());
    }

    @Override
    public void onShare(Video video) {
        com.example.metube.utils.ShareUtil.shareVideo(getContext(), video.getVideoURL());
    }
    @Override
    public void onPlayNextInQueue(Video video) {
        // Gọi QueueManager để thêm vào hàng chờ
        com.example.metube.utils.VideoQueueManager.getInstance().playNext(video);

        Toast.makeText(getContext(), "Playing next in queue", Toast.LENGTH_SHORT).show();
    }

}