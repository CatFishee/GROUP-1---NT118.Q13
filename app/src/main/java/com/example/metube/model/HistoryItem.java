package com.example.metube.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;

public class HistoryItem {
    @DocumentId
    private String id;
    private String videoID;
    private String userID;
    private Timestamp watchedAt;

    private Video video;
    private long resumePosition;
    private User uploader;
    @Exclude
    private String documentId;

    public HistoryItem() {}

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

    public long getResumePosition() { return resumePosition; }
    public void setResumePosition(long resumePosition) { this.resumePosition = resumePosition; }

    public User getUploader() { return uploader; }
    public void setUploader(User uploader) { this.uploader = uploader; }

    @Exclude
    public String getDocumentId() { return documentId; }
    @Exclude
    public void setDocumentId(String documentId) { this.documentId = documentId; }
}
