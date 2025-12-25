package com.example.metube.ui.playlist;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.metube.R;
import com.example.metube.model.Playlist;
import com.example.metube.model.Video;
import com.example.metube.ui.playlist.CreatePlaylistBottomSheet;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class AddToPlaylistBottomSheet extends BottomSheetDialogFragment {

    private Video video;
    private RecyclerView rvPlaylists;
    private AddToPlaylistAdapter adapter;
    private List<Playlist> playlistList = new ArrayList<>();

    public AddToPlaylistBottomSheet(Video video) {
        this.video = video;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.layout_bottom_sheet_add_to_playlist, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvPlaylists = view.findViewById(R.id.rv_playlists);
        rvPlaylists.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new AddToPlaylistAdapter(playlistList, video, () -> dismiss());
        rvPlaylists.setAdapter(adapter);

        loadPlaylists();

        // Xử lý nút "+ New playlist"
        view.findViewById(R.id.btn_new_playlist).setOnClickListener(v -> {
            // Mở dialog tạo playlist mới (tái sử dụng cái cũ của bạn)
            CreatePlaylistBottomSheet createDialog = new CreatePlaylistBottomSheet();
            createDialog.setOnPlaylistCreatedListener(() -> {
                // Khi tạo xong thì load lại danh sách playlist để hiện cái mới
                loadPlaylists();
            });
            createDialog.show(getParentFragmentManager(), "CreatePlaylist");
        });
    }

    private void loadPlaylists() {
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore.getInstance().collection("playlists")
                .whereEqualTo("ownerId", userId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    playlistList.clear();
                    for (DocumentSnapshot doc : querySnapshot) {
                        Playlist p = doc.toObject(Playlist.class);
                        if (p != null) {
                            // --- QUAN TRỌNG: GÁN ID TỪ DOCUMENT VÀO OBJECT ---
                            // Để đảm bảo code update không bị update nhầm chỗ hoặc null
                            p.setPlaylistId(doc.getId());

                            // Đảm bảo list video không bị null để tránh lỗi ở Adapter
                            if (p.getVideoIds() == null) {
                                p.setVideoIds(new ArrayList<>());
                            }

                            playlistList.add(p);
                        }
                    }
                    adapter.notifyDataSetChanged();
                });
    }
}