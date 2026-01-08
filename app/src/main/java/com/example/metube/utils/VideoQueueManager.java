package com.example.metube.utils;

import android.util.Log;
import com.example.metube.model.Video;
import java.util.ArrayList;
import java.util.List;

public class VideoQueueManager {
    private static final String TAG = "VideoQueueManager";
    private static VideoQueueManager instance;

    private List<Video> queue = new ArrayList<>();
    private int currentPosition = 0;

    private VideoQueueManager() {}

    public static synchronized VideoQueueManager getInstance() {
        if (instance == null) {
            instance = new VideoQueueManager();
        }
        return instance;
    }

    // ✅ Tìm vị trí video trong queue
    private int findVideoPosition(String videoId) {
        if (videoId == null) return -1;
        for (int i = 0; i < queue.size(); i++) {
            Video video = queue.get(i);
            if (video != null && video.getVideoID() != null && video.getVideoID().equals(videoId)) {
                return i;
            }
        }
        return -1;
    }

    // 1. Phát ngay lập tức (Clear queue, play new video)
    public void playNow(Video video) {
        Log.d(TAG, "playNow: " + video.getTitle());
        queue.clear();
        queue.add(video);
        currentPosition = 0;
    }

    // 2. Phát tiếp theo (Insert after current)
    public void playNext(Video video) {
        if (queue.isEmpty()) {
            playNow(video);
            return;
        }

        int existingPos = findVideoPosition(video.getVideoID());

        // Nếu đã ở vị trí play next rồi thì skip
        if (existingPos == currentPosition + 1) {
            Log.d(TAG, "playNext: Video already at next position");
            return;
        }

        // Xóa video ở vị trí cũ nếu có
        if (existingPos != -1) {
            queue.remove(existingPos);
            if (existingPos < currentPosition) {
                currentPosition--;
            } else if (existingPos == currentPosition) {
                // Đang phát thì không xóa được
                Log.w(TAG, "playNext: Cannot move currently playing video");
                return;
            }
        }

        // Chèn vào sau video đang phát
        int insertPosition = Math.min(currentPosition + 1, queue.size());
        queue.add(insertPosition, video);
        Log.d(TAG, "playNext: Added at position " + insertPosition);
    }

    // 3. Thêm vào cuối queue
    public void addToQueue(Video video) {
        if (findVideoPosition(video.getVideoID()) != -1) {
            Log.d(TAG, "addToQueue: Video already in queue");
            return;
        }
        queue.add(video);
        Log.d(TAG, "addToQueue: Added to end. Queue size: " + queue.size());
    }

    // 4. Phát playlist
    public void playPlaylist(List<Video> videos) {
        Log.d(TAG, "playPlaylist: " + videos.size() + " videos");
        queue.clear();
        for (Video video : videos) {
            if (video != null && video.getVideoID() != null) {
                queue.add(video);
            }
        }
        currentPosition = 0;
    }

    // 5. Thêm video (general purpose)
    public void addVideo(Video video) {
        if (video == null || video.getVideoID() == null) {
            Log.w(TAG, "addVideo: Invalid video");
            return;
        }

        if (findVideoPosition(video.getVideoID()) == -1) {
            queue.add(video);
            Log.d(TAG, "addVideo: Added. Queue size: " + queue.size());
        } else {
            Log.d(TAG, "addVideo: Already exists");
        }
    }

    // 6. Alias
    public void addVideoToBottom(Video video) {
        addToQueue(video);
    }

    // ✅ XÓA VIDEO THEO VỊ TRÍ (Fixed Logic)
    public boolean removeVideo(int position) {
        if (position < 0 || position >= queue.size()) {
            Log.w(TAG, "removeVideo: Invalid position " + position);
            return false;
        }

        // KHÔNG CHO XÓA VIDEO ĐANG PHÁT
        if (position == currentPosition) {
            Log.w(TAG, "removeVideo: Cannot remove currently playing video at " + position);
            return false;
        }

        Video removed = queue.remove(position);
        Log.d(TAG, "removeVideo: Removed " + removed.getTitle() + " at position " + position);

        // Điều chỉnh currentPosition
        if (position < currentPosition) {
            currentPosition--;
            Log.d(TAG, "removeVideo: Adjusted currentPosition to " + currentPosition);
        }

        return true;
    }

    // ✅ XÓA VIDEO THEO ID
    public boolean removeVideoById(String videoId) {
        int position = findVideoPosition(videoId);
        if (position != -1) {
            return removeVideo(position);
        }
        Log.w(TAG, "removeVideoById: Video not found " + videoId);
        return false;
    }

    // ✅ DI CHUYỂN VIDEO (For drag & drop)
    public void moveVideo(int fromPosition, int toPosition) {
        if (fromPosition < 0 || fromPosition >= queue.size() ||
                toPosition < 0 || toPosition >= queue.size()) {
            return;
        }

        Video movedVideo = queue.remove(fromPosition);
        queue.add(toPosition, movedVideo);

        // Điều chỉnh currentPosition
        if (fromPosition == currentPosition) {
            currentPosition = toPosition;
        } else if (fromPosition < currentPosition && toPosition >= currentPosition) {
            currentPosition--;
        } else if (fromPosition > currentPosition && toPosition <= currentPosition) {
            currentPosition++;
        }

        Log.d(TAG, "moveVideo: from " + fromPosition + " to " + toPosition +
                ", currentPos now: " + currentPosition);
    }

    // ✅ CLEAR QUEUE
    public void clearQueue() {
        Log.d(TAG, "clearQueue: Clearing " + queue.size() + " videos");
        queue.clear();
        currentPosition = 0;
    }

    // ✅ UPDATE CURRENT POSITION (Gọi khi ExoPlayer transition)
    public void setCurrentPosition(int pos) {
        if (pos >= 0 && pos < queue.size()) {
            currentPosition = pos;
            Log.d(TAG, "setCurrentPosition: " + pos + " / " + queue.size());
        } else {
            Log.w(TAG, "setCurrentPosition: Invalid position " + pos);
        }
    }

    // ✅ GET VIDEO TẠI VỊ TRÍ CỤ THỂ
    public Video getVideoAt(int position) {
        if (position >= 0 && position < queue.size()) {
            return queue.get(position);
        }
        return null;
    }

    // Getters
    public List<Video> getQueue() {
        return new ArrayList<>(queue); // Return copy để tránh concurrent modification
    }

    public int getCurrentPosition() {
        return currentPosition;
    }

    public Video getCurrentVideo() {
        return getVideoAt(currentPosition);
    }

    public boolean hasNext() {
        return currentPosition + 1 < queue.size();
    }

    public boolean hasPrevious() {
        return currentPosition > 0;
    }

    public int getQueueSize() {
        return queue.size();
    }

    // ✅ DEBUG INFO
    public void printQueueState() {
        Log.d(TAG, "========== QUEUE STATE ==========");
        Log.d(TAG, "Total videos: " + queue.size());
        Log.d(TAG, "Current position: " + currentPosition);
        for (int i = 0; i < queue.size(); i++) {
            Video v = queue.get(i);
            String marker = (i == currentPosition) ? " ← NOW PLAYING" : "";
            Log.d(TAG, String.format("[%d] %s%s", i, v.getTitle(), marker));
        }
        Log.d(TAG, "================================");
    }
}