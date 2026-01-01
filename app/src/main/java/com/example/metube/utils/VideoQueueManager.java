package com.example.metube.utils;

import com.example.metube.model.Video;
import java.util.ArrayList;
import java.util.List;

public class VideoQueueManager {
    private static VideoQueueManager instance;

    // Danh sách hàng chờ
    private List<Video> queue = new ArrayList<>();
    // Vị trí bài đang phát
    private int currentPosition = 0;

    private VideoQueueManager() {}

    public static synchronized VideoQueueManager getInstance() {
        if (instance == null) {
            instance = new VideoQueueManager();
        }
        return instance;
    }

    // 1. Phát ngay lập tức (Xóa queue cũ, tạo queue mới chỉ có 1 bài)
    public void playNow(Video video) {
        queue.clear();
        queue.add(video);
        currentPosition = 0;
    }

    // 2. Phát tiếp theo (Chèn vào ngay sau bài đang phát)
    public void playNext(Video video) {
        if (queue.isEmpty()) {
            playNow(video);
        } else {
            queue.add(currentPosition + 1, video);
        }
    }

    // 3. Thêm vào cuối hàng chờ
    public void addToQueue(Video video) {
        queue.add(video);
    }

    // 4. Phát cả một Playlist (Ví dụ bấm nút Play All)
    public void playPlaylist(List<Video> videos) {
        queue.clear();
        queue.addAll(videos);
        currentPosition = 0;
    }

    public List<Video> getQueue() { return queue; }
    public int getCurrentPosition() { return currentPosition; }
    public void setCurrentPosition(int pos) { this.currentPosition = pos; }
}