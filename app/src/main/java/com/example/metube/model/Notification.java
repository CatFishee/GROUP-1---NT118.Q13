package com.example.metube.model;

import com.google.firebase.Timestamp;

public class Notification {
    private String notificationID;
    private String recipientID;
    private String senderID;
    private String videoID;
    private String type;
    private String title;
    private String message;
    private String thumbnailURL;
    private boolean isRead;
    private Timestamp createdAt;

    public Notification() {}

    public Notification(String notificationID, String recipientID, String senderID,
                        String videoID, String type, String title, String message,
                        String thumbnailURL, boolean isRead, Timestamp createdAt) {
        this.notificationID = notificationID;
        this.recipientID = recipientID;
        this.senderID = senderID;
        this.videoID = videoID;
        this.type = type;
        this.title = title;
        this.message = message;
        this.thumbnailURL = thumbnailURL;
        this.isRead = isRead;
        this.createdAt = createdAt;
    }

    public String getNotificationID() { return notificationID; }
    public void setNotificationID(String notificationID) { this.notificationID = notificationID; }

    public String getRecipientID() { return recipientID; }
    public void setRecipientID(String recipientID) { this.recipientID = recipientID; }

    public String getSenderID() { return senderID; }
    public void setSenderID(String senderID) { this.senderID = senderID; }

    public String getVideoID() { return videoID; }
    public void setVideoID(String videoID) { this.videoID = videoID; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getThumbnailURL() { return thumbnailURL; }
    public void setThumbnailURL(String thumbnailURL) { this.thumbnailURL = thumbnailURL; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}