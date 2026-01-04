package com.example.metube.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;

public class Comment {
    private String commentID;
    private String videoID;
    private String commenterID;
    private String parentCommentID;
    private String text;
    private Timestamp createdAt;
    private int likeCount;

    // --- NEW ADDITIONS ---
    @Exclude private String authorName;
    @Exclude private String authorAvatarUrl;
    // ---------------------

    public Comment() {}

    public Comment(String commentID, String videoID, String commenterID, String parentCommentID, String text, Timestamp createdAt) {
        this.commentID = commentID;
        this.videoID = videoID;
        this.commenterID = commenterID;
        this.parentCommentID = parentCommentID;
        this.text = text;
        this.createdAt = createdAt;
    }

    // ... Keep existing Getters/Setters ...

    // Fix: Make sure getters match Firestore field names exactly or use @PropertyName if they differ.
    // Assuming your fields match, here are the new getters:

    public String getCommentID() { return commentID; } // Fixed naming convention
    public void setCommentID(String commentID) { this.commentID = commentID; }

    public String getVideoID() { return videoID; }
    public void setVideoID(String videoID) { this.videoID = videoID; }

    public String getCommenterID() { return commenterID; }
    public void setCommenterID(String commenterID) { this.commenterID = commenterID; }

    public String getParentCommentID() { return parentCommentID; }
    public void setParentCommentID(String parentCommentID) { this.parentCommentID = parentCommentID; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    // --- NEW EXCLUDED GETTERS/SETTERS ---
    @Exclude public String getAuthorName() { return authorName; }
    @Exclude public void setAuthorName(String authorName) { this.authorName = authorName; }

    @Exclude public String getAuthorAvatarUrl() { return authorAvatarUrl; }
    @Exclude public void setAuthorAvatarUrl(String authorAvatarUrl) { this.authorAvatarUrl = authorAvatarUrl; }
}