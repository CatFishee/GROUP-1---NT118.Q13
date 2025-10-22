package com.example.metube.model;
import com.google.firebase.Timestamp;
public class Subscription {
    private String subscriptionID;
    private String uploaderID;
    private String viewerID;
    private Timestamp createdAt;
    private Status status;
    public enum Status {
        UNSUBSCRIBED,
        SUBSCRIBED,
        MEMBERSHIP
    }
    public Subscription() {}

    public Subscription(String subscriptionID, String uploaderID, String viewerID, Timestamp createdAt, Status status) {
        this.subscriptionID = subscriptionID;
        this.uploaderID = uploaderID;
        this.viewerID = viewerID;
        this.createdAt = createdAt;
        this.status = status;
    }
    public String getSubscriptionID() { return subscriptionID; }
    public void setSubscriptionID(String subscriptionID) { this.subscriptionID = subscriptionID; }

    public String getUploaderID() { return uploaderID; }
    public void setUploaderID(String uploaderID) { this.uploaderID = uploaderID; }

    public String getViewerID() { return viewerID; }
    public void setViewerID(String viewerID) { this.viewerID = viewerID; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
}

