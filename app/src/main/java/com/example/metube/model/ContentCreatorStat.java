package com.example.metube.model;

import com.google.firebase.Timestamp;

/**
 * Daily snapshot of a creator's statistics.
 * One document per creator per day.
 */
public class ContentCreatorStat {

    // Firestore document ID
    private String contentCreatorStatID;

    // Creator
    private String userID;

    // Date key in format yyyy-MM-dd (used to prevent duplicates)
    private String dateKey;

    // Aggregated totals at end of day
    private long totalVideos;
    private long totalViews;

    // Snapshot timestamp (usually end of day or creation time)
    private Timestamp createdAt;

    // Required empty constructor for Firestore
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

    // --------------------
    // Getters & Setters
    // --------------------

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
