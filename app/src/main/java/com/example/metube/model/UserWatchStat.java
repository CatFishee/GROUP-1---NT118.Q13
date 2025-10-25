package com.example.metube.model;

import java.sql.Time;
import java.util.List;
import com.google.firebase.Timestamp;

public class UserWatchStat {
    public String userWatchStatID;
    public String userID;
    public List<String> videosWatched;
    public List<String> topicsWatched;
    public long totalWatchTime;
    public Timestamp createdAt;
    public UserWatchStat() {}
    public UserWatchStat(String userWatchStatID, String userID, List<String> videosWatched, List<String> topicsWatched, long totalWatchTime, Timestamp createdAt) {
        this.userWatchStatID = userWatchStatID;
        this.userID = userID;
        this.videosWatched = videosWatched;
        this.topicsWatched = topicsWatched;
        this.totalWatchTime = totalWatchTime;
        this.createdAt = createdAt;
    }
    public String getUserWatchStatID() { return userWatchStatID; }
    public void setUserWatchStatID(String userWatchStatID) { this.userWatchStatID = userWatchStatID; }

    public String getUserID() { return userID; }
    public void setUserID(String userID) { this.userID = userID; }

    public List<String> getVideosWatched() { return videosWatched; }
    public void setVideosWatched(List<String> videosWatched) { this.videosWatched = videosWatched; }
    public List<String> getTopicsWatched() { return topicsWatched; }
    public void setTopicsWatched(List<String> topicsWatched) { this.topicsWatched = topicsWatched; }

    public long getTotalWatchTime() { return totalWatchTime; }
    public void setTotalWatchTime(long totalWatchTime) { this.totalWatchTime = totalWatchTime; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
