package com.example.metube.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

public class HistoryItem {
    @DocumentId
    private String id;
    private String videoID;
    private String userID;
    private Timestamp watchedAt;

    // Transient fields: These are not stored in Firestore, but populated at runtime
    private Video video;
    private User uploader;

    public HistoryItem() {}

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getVideoID() { return videoID; }
    public void setVideoID(String videoID) { this.videoID = videoID; }

    public String getUserID() { return userID; }
    public void setUserID(String userID) { this.userID = userID; }

    public Timestamp getWatchedAt() { return watchedAt; }
    public void setWatchedAt(Timestamp watchedAt) { this.watchedAt = watchedAt; }

    // Getters and setters for transient fields
    public Video getVideo() { return video; }
    public void setVideo(Video video) { this.video = video; }

    public User getUploader() { return uploader; }
    public void setUploader(User uploader) { this.uploader = uploader; }
}
