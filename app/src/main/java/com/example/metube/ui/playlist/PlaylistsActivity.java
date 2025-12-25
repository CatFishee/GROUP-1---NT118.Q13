package com.example.metube.ui.playlist;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.metube.R;
import com.example.metube.model.Playlist;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.List;

public class PlaylistsActivity extends AppCompatActivity {

    private RecyclerView rvPlaylists;
    private PlaylistsAdapter adapter;
    private List<Playlist> playlistList = new ArrayList<>();
    private FirebaseFirestore firestore;
    private Button btnCreateNew;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlists);

        firestore = FirebaseFirestore.getInstance();

        // 1. Ánh xạ
        rvPlaylists = findViewById(R.id.rv_playlists);
        btnCreateNew = findViewById(R.id.btn_create_new);
        ImageView btnBack = findViewById(R.id.btn_back);

        // 2. Setup RecyclerView
        rvPlaylists.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PlaylistsAdapter(playlistList);
        rvPlaylists.setAdapter(adapter);

        // 3. Load Data
        loadUserPlaylists();

        // 4. Sự kiện Click
        btnBack.setOnClickListener(v -> finish());

        // Nút tạo mới ở dưới đáy: Mở lại cái Dialog bạn vừa làm
        btnCreateNew.setOnClickListener(v -> {
            CreatePlaylistBottomSheet dialog = new CreatePlaylistBottomSheet();
            dialog.setOnPlaylistCreatedListener(() -> {
                // Khi tạo xong, tự động tải lại danh sách
                loadUserPlaylists();
            });
            dialog.show(getSupportFragmentManager(), "CreatePlaylistDialog");
        });
    }

    private void loadUserPlaylists() {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        firestore.collection("playlists")
                .whereEqualTo("ownerId", userId)
                // .orderBy("createdAt", Query.Direction.DESCENDING) // Cần tạo index nếu dùng
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    playlistList.clear();
                    for (DocumentSnapshot doc : querySnapshot) {
                        playlistList.add(doc.toObject(Playlist.class));
                    }
                    adapter.notifyDataSetChanged();
                });
    }
}