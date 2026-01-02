package com.example.metube.ui.downloads;

import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.metube.R;
import com.example.metube.model.Video;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DownloadsActivity extends AppCompatActivity {

    private RecyclerView rvDownloads;
    private DownloadsAdapter adapter;
    private TextView tvEmpty;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_downloads);

        rvDownloads = findViewById(R.id.rv_downloads);
        tvEmpty = findViewById(R.id.tv_empty);
        ImageView btnBack = findViewById(R.id.btn_back);

        btnBack.setOnClickListener(v -> finish());

        setupRecyclerView();
        loadDownloadedVideos();
    }

    private void setupRecyclerView() {
        adapter = new DownloadsAdapter();
        rvDownloads.setLayoutManager(new LinearLayoutManager(this));
        rvDownloads.setAdapter(adapter);
    }

    private void loadDownloadedVideos() {
        List<Video> localVideos = new ArrayList<>();

        // Đường dẫn thư mục: Movies/MeTube
        File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "MeTube");

        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles();

            if (files != null) {
                for (File file : files) {
                    // Lọc lấy file .mp4
                    if (file.isFile() && file.getName().endsWith(".mp4")) {
                        Video v = new Video();
                        v.setVideoID("local_" + file.getName()); // ID giả
                        v.setTitle(file.getName().replace(".mp4", "").replace("_", " "));
                        v.setVideoURL(file.getAbsolutePath()); // Đường dẫn tuyệt đối
                        // v.setThumbnailURL(...) -> Không cần, Adapter dùng Glide load từ file path

                        localVideos.add(v);
                    }
                }
            }
        }

        if (localVideos.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            rvDownloads.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            rvDownloads.setVisibility(View.VISIBLE);
            adapter.setData(localVideos);
        }
    }
}