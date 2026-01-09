package com.example.metube.model;

import com.google.firebase.Timestamp;

public class ContentCreatorStat {

    private String contentCreatorStatID;

    private String userID;
    private String dateKey;

    private long totalVideos;
    private long totalViews;

    private Timestamp createdAt;

    public ContentCreatorStat() {}

    public ContentCreatorStat(
            String contentCreatorStatID,
            String userID,
            String dateKey,
            long totalVideos,
            long totalViews,
            Timestamp createdAt
    ) {
        this.contentCreatorStatID = contentCreatorStatID;
        this.userID = userID;
        this.dateKey = dateKey;
        this.totalVideos = totalVideos;
        this.totalViews = totalViews;
        this.createdAt = createdAt;
    }

    public String getContentCreatorStatID() {
        return contentCreatorStatID;
    }

    public void setContentCreatorStatID(String contentCreatorStatID) {
        this.contentCreatorStatID = contentCreatorStatID;
    }

    public String getUserID() {
        return userID;
    }

    public void setUserID(String userID) {
        this.userID = userID;
    }

    public String getDateKey() {
        return dateKey;
    }

    public void setDateKey(String dateKey) {
        this.dateKey = dateKey;
    }

    public long getTotalVideos() {
        return totalVideos;
    }

    public void setTotalVideos(long totalVideos) {
        this.totalVideos = totalVideos;
    }

    public long getTotalViews() {
        return totalViews;
    }

    public void setTotalViews(long totalViews) {
        this.totalViews = totalViews;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}
