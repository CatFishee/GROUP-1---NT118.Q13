package com.example.metube.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.List;

public class Video {
    private String videoID;
    private String uploaderID;
    private String title;
    private String description;
    private List<String> topics;
    private String thumbnailURL;
    private String videoURL;
    private long duration;
    @ServerTimestamp
    private Timestamp createdAt; //timestamp của Firebase
    public Video() {}

    public Video (String videoID, String uploaderID, String title, String description, List<String> topics, String thumbnailURL, String videoURL, long duration, Timestamp createdAt) {
        this.videoID = videoID;
        this.uploaderID = uploaderID;
        this.title = title;
        this.description = description;
        this.topics = topics;
        this.thumbnailURL = thumbnailURL;
        this.videoURL = videoURL;
        this.duration = duration;
    }
        public String getVideoID() { return videoID; }
        public void setVideoID(String videoID) { this.videoID = videoID; }

        public String getUploaderID() { return uploaderID; }
        public void setUploaderID(String uploaderID) { this.uploaderID = uploaderID; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public List<String> getTopics() { return topics; }
        public void setTopics(List<String> topics) { this.topics = topics; }

        public String getThumbnailURL() { return thumbnailURL; }
        public void setThumbnailURL(String thumbnailURL) { this.thumbnailURL = thumbnailURL; }

        public String getVideoURL() { return videoURL; }
        public void setVideoURL(String videoURL) { this.videoURL = videoURL; }

        public long getDuration() { return duration; }
        public void setDuration(long duration) { this.duration = duration; }

        public Timestamp getCreatedAt() { return createdAt; }
        public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    }

