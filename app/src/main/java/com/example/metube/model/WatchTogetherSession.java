package com.example.metube.model;

import java.util.List;

public class WatchTogetherSession {
    public String watchTogetherSessionID;
    public String videoID;
    public String hostID;
    public List<String> participantID;
    public PlaybackState playbackState;
    public enum PlaybackState {
        PAUSED,
        PLAYING,
        STOPPED
    }
    public long currentTimestamp;
    public WatchTogetherSession() {}

    public WatchTogetherSession(String watchTogetherSessionID, String videoID, String hostID, List<String> participantID, WatchTogetherSession.PlaybackState playbackState) {
        this.watchTogetherSessionID = watchTogetherSessionID;
        this.videoID = videoID;
        this.hostID = hostID;
        this.participantID = participantID;
        this.playbackState = playbackState;
    }
    public String getWatchTogetherSessionID() { return watchTogetherSessionID; }
    public void setWatchTogetherSessionID(String watchTogetherSessionID) { this.watchTogetherSessionID = watchTogetherSessionID; }

    public String getVideoID() { return videoID; }
    public void setVideoID(String videoID) { this.videoID = videoID; }

    public String getHostID() { return hostID; }
    public void setHostID(String hostID) { this.hostID = hostID; }

    public WatchTogetherSession.PlaybackState getPlaybackState() { return playbackState; }
    public void setStatus(WatchTogetherSession.PlaybackState playbackState) { this.playbackState = playbackState; }

}
