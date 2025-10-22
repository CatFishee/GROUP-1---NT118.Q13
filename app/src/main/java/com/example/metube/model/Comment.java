package com.example.metube.model;
import com.google.firebase.Timestamp;
public class Comment {
    private String commentID;
    private String videoID;
    private String commenterID;
    private String parentCommentID; // null nếu là comment gốc
    private String text;
    private Timestamp createdAt;
    int likeCount;
    public Comment() {}
    public Comment(String commentID, String videoID, String commenterID, String parentCommentID, String text, Timestamp createdAt) {
        this.commentID = commentID;
        this.videoID= videoID;
        this.commenterID = commenterID;
        this.parentCommentID = parentCommentID;
        this.text = text;
        this.createdAt = createdAt;
    }
    public String getCommentId() { return commentID; }
    public void setCommentId(String commentID) { this.commentID = commentID; }

    public String getVideoID() { return videoID; }
    public void setVideoId(String videoId) { this.videoID = videoID; }

    public String getUserId() { return commenterID; }
    public void setUserId(String commenterID) { this.commenterID = commenterID; }

    public String getParentCommentId() { return parentCommentID; }
    public void setParentCommentId(String parentCommentID) { this.parentCommentID = parentCommentID; }

    public String getContent() { return text; }
    public void setContent(String text) { this.text = text; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
