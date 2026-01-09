package com.example.metube.model;

import java.util.HashMap;
import java.util.Map;

public class WatchTogetherSession {
    private String sessionID;
    private String videoID;
    private String videoTitle;
    private String hostID;
    private Map<String, Long> participants = new HashMap<>();
    private PlaybackState playbackState;
    private long currentTimestamp;
    private float playbackSpeed;

    public enum PlaybackState {
        PAUSED,
        PLAYING,
        STOPPED
    }

    public WatchTogetherSession() {
        // Default constructor required for DataSnapshot.getValue(WatchTogetherSession.class)
        this.playbackState = PlaybackState.PAUSED;
        this.playbackSpeed = 1.0f;
        this.currentTimestamp = 0;
    }

    // Getters and Setters
    public String getSessionID() { return sessionID; }
    public void setSessionID(String sessionID) { this.sessionID = sessionID; }

    public String getVideoID() { return videoID; }
    public void setVideoID(String videoID) { this.videoID = videoID; }

    public String getVideoTitle() { return videoTitle; }
    public void setVideoTitle(String videoTitle) { this.videoTitle = videoTitle; }

    public String getHostID() { return hostID; }
    public void setHostID(String hostID) { this.hostID = hostID; }

    public Map<String, Long> getParticipants() { return participants; }
    public void setParticipants(Map<String, Long> participants) { this.participants = participants; }

    public PlaybackState getPlaybackState() { return playbackState; }
    public void setPlaybackState(PlaybackState playbackState) { this.playbackState = playbackState; }

    public long getCurrentTimestamp() { return currentTimestamp; }
    public void setCurrentTimestamp(long currentTimestamp) { this.currentTimestamp = currentTimestamp; }

    public float getPlaybackSpeed() { return playbackSpeed; }
    public void setPlaybackSpeed(float playbackSpeed) { this.playbackSpeed = playbackSpeed; }
}