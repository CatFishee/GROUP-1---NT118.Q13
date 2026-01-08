package com.example.metube.ui.playlist;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.metube.R;
import com.example.metube.model.Playlist;
import com.example.metube.ui.playlist.CreatePlaylistBottomSheet;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PlaylistsActivity extends AppCompatActivity {

    private RecyclerView rvPlaylists;
    private PlaylistsAdapter adapter;

    // Danh sách gốc (chứa tất cả playlist tải về)
    private List<Playlist> allPlaylists = new ArrayList<>();
    // Danh sách hiển thị (đã qua lọc và sắp xếp)
    private List<Playlist> displayedPlaylists = new ArrayList<>();

    private FirebaseFirestore firestore;
    private Button btnCreateNew;
    private TextView tvSortLabel;
    private ChipGroup chipGroupFilters;

    // 0: Recently Added, 1: Recently Played, 2: A-Z, 3: Z-A
    private int currentSortType = 0;
    private String currentTopicFilter = "All"; // Lưu lại topic đang lọc

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlists);

        firestore = FirebaseFirestore.getInstance();

        // 1. Ánh xạ View
        rvPlaylists = findViewById(R.id.rv_playlists);
        btnCreateNew = findViewById(R.id.btn_create_new);
        ImageView btnBack = findViewById(R.id.btn_back);
        tvSortLabel = findViewById(R.id.tv_sort_label);
        chipGroupFilters = findViewById(R.id.chip_group_filters);

        // 2. Setup RecyclerView
        rvPlaylists.setLayoutManager(new LinearLayoutManager(this));
        // Adapter liên kết với displayedPlaylists
        adapter = new PlaylistsAdapter(displayedPlaylists);
        rvPlaylists.setAdapter(adapter);

        // 3. Load Data
        loadUserPlaylists();

        // 4. Sự kiện Click
        btnBack.setOnClickListener(v -> finish());

        // Nút sort
        findViewById(R.id.btn_sort).setOnClickListener(this::showSortMenu);

        // Nút tạo mới
        btnCreateNew.setOnClickListener(v -> {
            CreatePlaylistBottomSheet dialog = new CreatePlaylistBottomSheet();
            dialog.setOnPlaylistCreatedListener(() -> {
                // Khi tạo xong, tải lại danh sách
                loadUserPlaylists();
            });
            dialog.show(getSupportFragmentManager(), "CreatePlaylistDialog");
        });
        adapter.setOnPlaylistMoreClickListener((playlist, view) -> {
            showPlaylistOptionsMenu(playlist, view);
        });

        // 5. Logic Filter Chips
        chipGroupFilters.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chip_music) {
                currentTopicFilter = "Music";
            } else if (checkedId == R.id.chip_gaming) { // Đảm bảo bạn có chip id này trong XML
                currentTopicFilter = "Gaming";
            } else {
                // Mặc định All (hoặc khi chọn Owned/Playlists)
                currentTopicFilter = "All";
            }
            // Gọi hàm lọc
            applyFilterAndSort();
        });
    }

    // --- HÀM LOAD DỮ LIỆU TỪ FIREBASE ---
    private void loadUserPlaylists() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        firestore.collection("playlists")
                .whereEqualTo("ownerId", currentUser.getUid())
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    allPlaylists.clear();
                    Set<String> allUniqueTopics = new HashSet<>();

                    for (DocumentSnapshot doc : querySnapshot) {
                        Playlist p = doc.toObject(Playlist.class);
                        if (p != null) {
                            p.setPlaylistId(doc.getId());
                            allPlaylists.add(p);
                            // Gom topic để tạo chip
                            if (p.getContainedTopics() != null) {
                                allUniqueTopics.addAll(p.getContainedTopics());
                            }
                        }
                    }

                    // Sau khi tải xong, áp dụng bộ lọc và sắp xếp để hiển thị
                    updateTopicsFromPlaylists();
                    applyFilterAndSort();
                });
    }

    // --- HÀM TRUNG TÂM: LỌC VÀ SẮP XẾP ---
    // Hàm này chịu trách nhiệm đổ dữ liệu từ allPlaylists sang displayedPlaylists
    private void applyFilterAndSort() {
        displayedPlaylists.clear();

        // 1. LỌC (FILTER)
        for (Playlist p : allPlaylists) {
            // 1. Kiểm tra lọc Topic
            boolean matchesTopic = false;
            if (currentTopicFilter.equals("All")) {
                matchesTopic = true;
            } else if (p.getContainedTopics() != null) {
                for (String topic : p.getContainedTopics()) {
                    if (topic.equalsIgnoreCase(currentTopicFilter)) {
                        matchesTopic = true;
                        break;
                    }
                }
            }

            if (matchesTopic) {
                displayedPlaylists.add(p);
            }
        }

        // 2. SẮP XẾP (SORT) - Dựa trên displayedPlaylists vừa lọc được
        switch (currentSortType) {
            case 0: // Recently Added
            case 1: // Recently Played (Tạm dùng logic giống Added)
                Collections.sort(displayedPlaylists, (p1, p2) -> {
                    if (p1.getCreatedAt() == null && p2.getCreatedAt() == null) return 0;

                    // 2. Nếu p1 null -> p1 nhỏ hơn (đẩy xuống cuối)
                    if (p1.getCreatedAt() == null) return 1;

                    // 3. Nếu p2 null -> p2 nhỏ hơn
                    if (p2.getCreatedAt() == null) return -1;

                    // 4. Cả 2 đều có dữ liệu -> So sánh bình thường
                    return p2.getCreatedAt().compareTo(p1.getCreatedAt()); // Mới nhất lên đầu
                });
                break;

            case 2: // A-Z
                Collections.sort(displayedPlaylists, (p1, p2) ->
                        p1.getTitle().compareToIgnoreCase(p2.getTitle())
                );
                break;

            case 3: // Z-A
                Collections.sort(displayedPlaylists, (p1, p2) ->
                        p2.getTitle().compareToIgnoreCase(p1.getTitle())
                );
                break;
        }

        // 3. CẬP NHẬT GIAO DIỆN
        adapter.notifyDataSetChanged();
    }
    private void updateTopicsFromPlaylists() {
        Set<String> uniqueTopics = new HashSet<>();

        // Duyệt qua tất cả playlist để lấy các topic bên trong trường containedTopics
        for (Playlist p : allPlaylists) {
            if (p.getContainedTopics() != null && !p.getContainedTopics().isEmpty()) {
                uniqueTopics.addAll(p.getContainedTopics());
            }
        }

        // Chuyển Set thành List và sắp xếp theo alphabet
        List<String> sortedTopics = new ArrayList<>(uniqueTopics);
        Collections.sort(sortedTopics);

        // Gọi hàm tạo Chip giao diện
        setupDynamicFilterChips(sortedTopics);
    }
    private void setupDynamicFilterChips(List<String> topicList) {
        chipGroupFilters.removeAllViews();

        // 1. Luôn tạo Chip "All" đầu tiên
        Chip allChip = createFilterChip("All");
        allChip.setId(View.generateViewId());
        allChip.setChecked(true); // Mặc định chọn All
        chipGroupFilters.addView(allChip);

        // 2. Tạo các Chip cho từng Topic động
        for (String topic : topicList) {
            Chip chip = createFilterChip(topic);
            chip.setId(View.generateViewId());
            chipGroupFilters.addView(chip);
        }
    }
    private Chip createFilterChip(String text) {
        Chip chip = new Chip(this);
        chip.setText(text);
        chip.setCheckable(true);
        chip.setClickable(true);

        // Gán màu sắc từ các selector bạn đã tạo trong XML
        chip.setTextColor(ContextCompat.getColorStateList(this, R.color.chip_text_color_selector));
        chip.setChipBackgroundColor(ContextCompat.getColorStateList(this, R.color.chip_background_color_selector));
        chip.setChipStrokeWidth(0f); // Bỏ viền cho giống YouTube

        // Sự kiện khi người dùng nhấn chọn Chip
        chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                currentTopicFilter = text; // Cập nhật filter hiện tại (All, Gaming, Music...)
                applyFilterAndSort();      // Gọi hàm lọc lại danh sách hiển thị
            }
        });

        return chip;
    }


    // --- MENU SẮP XẾP ---
    private void showSortMenu(View v) {
        PopupMenu popup = new PopupMenu(this, v);
        popup.getMenu().add(0, 0, 0, "Recently added");
        popup.getMenu().add(0, 1, 1, "Recently played");
        popup.getMenu().add(0, 2, 2, "A-Z");
        popup.getMenu().add(0, 3, 3, "Z-A");

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            currentSortType = id; // Lưu lại kiểu sort

            switch (id) {
                case 0: tvSortLabel.setText("Recently added"); break;
                case 1: tvSortLabel.setText("Recently played"); break;
                case 2: tvSortLabel.setText("A-Z"); break;
                case 3: tvSortLabel.setText("Z-A"); break;
            }

            // Gọi hàm trung tâm để sắp xếp lại danh sách đang hiển thị
            applyFilterAndSort();
            return true;
        });
        popup.show();
    }
    @Override
    protected void onResume() {
        super.onResume();
        // Gọi lại hàm load dữ liệu mỗi khi màn hình này xuất hiện
        // Để cập nhật các thay đổi (Thumbnail mới, Tên mới...)
        loadUserPlaylists();
    }
    private void showPlaylistOptionsMenu(Playlist playlist, View view) {
        PopupMenu popup = new PopupMenu(this, view);
        popup.getMenu().add(0, 0, 0, "Edit playlist");
        popup.getMenu().add(0, 1, 1, "Delete playlist");

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 0: // Edit
                    Intent intent = new Intent(this, EditPlaylistActivity.class);
                    intent.putExtra("playlist_id", playlist.getPlaylistId());
                    intent.putExtra("title", playlist.getTitle());
                    intent.putExtra("visibility", playlist.getVisibility());
                    startActivity(intent);
                    return true;

                case 1: // Delete
                    confirmDeletePlaylist(playlist);
                    return true;
            }
            return false;
        });
        popup.show();
    }

    private void confirmDeletePlaylist(Playlist playlist) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete Playlist")
                .setMessage("Are you sure you want to delete \"" + playlist.getTitle() + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    deletePlaylist(playlist);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deletePlaylist(Playlist playlist) {
        firestore.collection("playlists").document(playlist.getPlaylistId())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Playlist deleted", Toast.LENGTH_SHORT).show();
                    // Tải lại dữ liệu để cập nhật danh sách
                    loadUserPlaylists();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}