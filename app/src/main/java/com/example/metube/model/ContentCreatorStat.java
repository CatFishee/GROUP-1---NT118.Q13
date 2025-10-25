package com.example.metube.model;

import java.util.List;
import com.google.firebase.Timestamp;

public class ContentCreatorStat {
    public String contentCreatorStatID;
    public String userID;
    public int subGained;
    public int subLost;
    public Timestamp createdAt;
    public ContentCreatorStat() {}
    public ContentCreatorStat(String contentCreatorStatID, String userID, int subGained, int subLost, Timestamp createdAt) {
        this.contentCreatorStatID = contentCreatorStatID;
        this.userID = userID;
        this.subGained = subGained;
        this.subLost = subLost;
        this.createdAt = createdAt;
    }
    public String getContentCreatorStatID() { return contentCreatorStatID; }
    public void setContentCreatorStatID(String contentCreatorStatID) { this.contentCreatorStatID = contentCreatorStatID; }

    public String getUserID() { return userID; }
    public void setUserID(String userID) { this.userID = userID; }
    public int getSubGained() { return subGained; }
    public void setSubGained(int subGained) { this.subGained = subGained; }
    public int getSubLost() { return subLost; }
    public void setSubLost(int subLost) { this.subLost = subLost; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
