package com.example.metube.model;

import java.util.List;
import com.google.firebase.Timestamp;

public class VideoStat  {
    public String videoStatID;
    public String videoID;
    public int viewCount;
    public int likeCount;
    public int dislikeCount;
    public Timestamp createdAt;
    public VideoStat() {}
    public VideoStat(String videoStatID, String videoID, int viewCount, int likeCount, int dislikeCount, Timestamp createdAt) {
        this.videoStatID = videoStatID;
        this.videoID = videoID;
        this.viewCount = viewCount;
        this.likeCount = likeCount;
        this.dislikeCount = dislikeCount;
        this.createdAt = createdAt;
    }
    public String getVideoStatID() { return videoStatID; }
    public void setVideoStatID(String videoStatID) { this.videoStatID = videoStatID; }

    public String getVideoID() { return videoID; }
    public void setVideoID(String videoID) { this.videoID = videoID; }
    public int getViewCount() { return viewCount; }
    public void setViewCount(int viewCount) { this.viewCount = viewCount; }
    public int getLikeCount() { return likeCount; }
    public void setLikeCount(int likeCount) { this.likeCount = likeCount; }
    public int getDislikeCount() { return dislikeCount; }
    public void setDislikeCount(int dislikeCount) { this.dislikeCount = dislikeCount; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
