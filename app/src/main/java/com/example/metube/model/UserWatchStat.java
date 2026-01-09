package com.example.metube.model;

import java.sql.Time;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.firebase.Timestamp;

public class UserWatchStat {
    public String userWatchStatID;
    public String userID;
    public List<String> videosWatched;
    private Map<String, Long> topicCounts;
    public long totalWatchTime;
    public Timestamp createdAt;
    private Map<String, Long> dailyWatchTime;
    public UserWatchStat() {
        this.topicCounts = new HashMap<>();
        this.dailyWatchTime = new HashMap<>();
    }
    public UserWatchStat(String userWatchStatID, String userID, List<String> videosWatched, Map<String, Long> topicCounts, long totalWatchTime, Timestamp createdAt) {
        this.userWatchStatID = userWatchStatID;
        this.userID = userID;
        this.videosWatched = videosWatched;
        this.topicCounts = topicCounts;
        this.totalWatchTime = totalWatchTime;
        this.createdAt = createdAt;
    }
    public String getUserWatchStatID() { return userWatchStatID; }
    public void setUserWatchStatID(String userWatchStatID) { this.userWatchStatID = userWatchStatID; }

    public String getUserID() { return userID; }
    public void setUserID(String userID) { this.userID = userID; }

    public List<String> getVideosWatched() { return videosWatched; }
    public void setVideosWatched(List<String> videosWatched) { this.videosWatched = videosWatched; }
    public Map<String, Long> getTopicCounts() { return topicCounts; }
    public void setTopicCounts(Map<String, Long> topicCounts) { this.topicCounts = topicCounts; }

    public long getTotalWatchTime() { return totalWatchTime; }
    public void setTotalWatchTime(long totalWatchTime) { this.totalWatchTime = totalWatchTime; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Map<String, Long> getDailyWatchTime() { return dailyWatchTime; }
    public void setDailyWatchTime(Map<String, Long> dailyWatchTime) { this.dailyWatchTime = dailyWatchTime; }
}
