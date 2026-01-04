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

    // ✅ HÀM KIỂM TRA VIDEO ĐÃ TỒN TẠI CHƯA
    private int findVideoPosition(String videoId) {
        if (videoId == null) return -1;
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).getVideoID().equals(videoId)) {
                return i;
            }
        }
        return -1;
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
            return;
        }

        // ✅ KIỂM TRA TRÙNG: Nếu video đã có trong queue, xóa vị trí cũ
        int existingPos = findVideoPosition(video.getVideoID());
        if (existingPos != -1) {
            // Nếu video đang ở vị trí sẽ chèn vào thì bỏ qua
            if (existingPos == currentPosition + 1) {
                return;
            }
            queue.remove(existingPos);
            // Điều chỉnh currentPosition nếu cần
            if (existingPos <= currentPosition) {
                currentPosition--;
            }
        }

        queue.add(currentPosition + 1, video);
    }

    // 3. Thêm vào cuối hàng chờ
    public void addToQueue(Video video) {
        // ✅ KIỂM TRA TRÙNG: Nếu đã có thì không thêm nữa
        if (findVideoPosition(video.getVideoID()) != -1) {
            return; // Video đã có trong queue rồi
        }
        queue.add(video);
    }

    // 4. Phát cả một Playlist (Ví dụ bấm nút Play All)
    public void playPlaylist(List<Video> videos) {
        queue.clear();
        // ✅ Lọc trùng lặp khi thêm playlist
        for (Video video : videos) {
            if (findVideoPosition(video.getVideoID()) == -1) {
                queue.add(video);
            }
        }
        currentPosition = 0;
    }

    // 5. Thêm video (dùng khi click xem video)
    public void addVideo(Video video) {
        // ✅ KIỂM TRA TRÙNG trước khi thêm
        if (findVideoPosition(video.getVideoID()) == -1) {
            queue.add(video);
        }
    }

    // 6. Thêm video vào cuối (alias của addToQueue)
    public void addVideoToBottom(Video video) {
        addToQueue(video);
    }

    // ✅ HÀM MỚI: Xóa video khỏi queue
    public void removeVideo(int position) {
        if (position >= 0 && position < queue.size()) {
            queue.remove(position);

            // Điều chỉnh currentPosition
            if (position < currentPosition) {
                currentPosition--;
            } else if (position == currentPosition) {
                // Nếu xóa video đang phát, reset về -1
                currentPosition = -1;
            }
        }
    }

    // ✅ HÀM MỚI: Xóa video theo videoId
    public boolean removeVideoById(String videoId) {
        int position = findVideoPosition(videoId);
        if (position != -1) {
            removeVideo(position);
            return true;
        }
        return false;
    }

    // ✅ HÀM MỚI: Clear toàn bộ queue
    public void clearQueue() {
        queue.clear();
        currentPosition = 0;
    }

    // Getters and Setters
    public List<Video> getQueue() {
        return queue;
    }

    public int getCurrentPosition() {
        return currentPosition;
    }

    public void setCurrentPosition(int pos) {
        this.currentPosition = pos;
    }

    // ✅ HÀM MỚI: Lấy video hiện tại
    public Video getCurrentVideo() {
        if (currentPosition >= 0 && currentPosition < queue.size()) {
            return queue.get(currentPosition);
        }
        return null;
    }

    // ✅ HÀM MỚI: Kiểm tra có video tiếp theo không
    public boolean hasNext() {
        return currentPosition + 1 < queue.size();
    }

    // ✅ HÀM MỚI: Kiểm tra có video trước đó không
    public boolean hasPrevious() {
        return currentPosition > 0;
    }
}