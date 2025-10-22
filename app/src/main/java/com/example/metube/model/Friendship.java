package com.example.metube.model;

import com.google.firebase.Timestamp;

public class Friendship {
    public String friendshipID;
    public String userID1;
    public String userID2;
    public Status status;

    public enum Status {
        ACCEPTED,
        REFUSED,
        PENDING
    }

    public Friendship() {}

    public Friendship(String friendshipID, String userID1, String userID2, Friendship.Status status) {
        this.friendshipID = friendshipID;
        this.userID1 = userID1;
        this.userID2 = userID2;
        this.status = status;
    }
    public String getFriendshipID() { return friendshipID; }
    public void setFriendshipID(String friendshipID) { this.friendshipID = friendshipID; }

    public String getUserID1() { return userID1; }
    public void setUserID1(String userID1) { this.userID1 = userID1; }

    public String getUserID2() { return userID2; }
    public void setUserID2(String userID2) { this.userID2 = userID2; }

    public Friendship.Status getStatus() { return status; }
    public void setStatus(Friendship.Status status) { this.status = status; }
}
