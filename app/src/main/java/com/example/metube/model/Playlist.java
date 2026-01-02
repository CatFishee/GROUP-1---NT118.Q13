package com.example.metube.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.ServerTimestamp;
import java.util.List;

public class Playlist {
    private String playlistId;
    private String ownerId; // ID của người tạo
    private String title;
    private String visibility; // "PUBLIC", "UNLISTED", "PRIVATE"
    private List<String> videoIds; // Danh sách ID các video trong playlist
    private List<String> containedTopics;
    private String thumbnailURL;
    private String description;


    @ServerTimestamp
    private Timestamp createdAt;

    public Playlist() {}

    public Playlist(String playlistId, String ownerId, String title, String visibility, List<String> videoIds) {
        this.playlistId = playlistId;
        this.ownerId = ownerId;
        this.title = title;
        this.visibility = visibility;
        this.videoIds = videoIds;

    }

    // Getters and Setters
    public String getPlaylistId() { return playlistId; }
    public void setPlaylistId(String playlistId) { this.playlistId = playlistId; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }

    public List<String> getVideoIds() { return videoIds; }
    public void setVideoIds(List<String> videoIds) { this.videoIds = videoIds; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public List<String> getContainedTopics() { return containedTopics; }
    public void setContainedTopics(List<String> containedTopics) { this.containedTopics = containedTopics; }
    public String getThumbnailURL() { return thumbnailURL; }
    public void setThumbnailURL(String thumbnailURL) { this.thumbnailURL = thumbnailURL; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}