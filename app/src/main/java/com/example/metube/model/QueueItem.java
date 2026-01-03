package com.example.metube.model;

public class QueueItem {
    private String key; // Firebase Key
    private String title;
    private String url;
    private String addedBy;

    public QueueItem() {} // Required for Firebase

    public QueueItem(String title, String url, String addedBy) {
        this.title = title;
        this.url = url;
        this.addedBy = addedBy;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getAddedBy() { return addedBy; }
    public void setAddedBy(String addedBy) { this.addedBy = addedBy; }
}