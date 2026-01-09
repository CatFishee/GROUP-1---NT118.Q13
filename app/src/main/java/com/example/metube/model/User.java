package com.example.metube.model;

import com.google.firebase.firestore.PropertyName;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class User implements Serializable {
    private String userID;
    private String name;
    private String email;
    private boolean isAdmin;
    private boolean isPremium;
    private String profileURL;
    private boolean isHistoryPaused = false;
    private List<String> searchKeywords = new ArrayList<>();
    private Map<String, Boolean> notificationSettings = new HashMap<>();

    public User() {

    }

    public User(String userID, String name, String email, boolean isAdmin, boolean isPremium, String profileURL) {
        this.userID = userID;
        this.name = name;
        this.email = email;
        this.isAdmin = isAdmin;
        this.isPremium = isPremium;
        this.profileURL = profileURL;
        this.searchKeywords = generateKeywords(name);

    }
    public List<String> getSearchKeywords() { return searchKeywords; }
    public void setSearchKeywords(List<String> searchKeywords) { this.searchKeywords = searchKeywords; }


    public static List<String> generateKeywords(String name) {
        List<String> keywords = new ArrayList<>();
        if (name == null || name.isEmpty()) return keywords;

        String[] words = name.toLowerCase().split("\\s+");
        for (String word : words) {
            if (!word.isEmpty()) keywords.add(word);
        }
        return keywords;
    }

    public String getUserID() { return userID; }
    public void setUserID(String userID) { this.userID = userID; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public boolean isAdmin() { return isAdmin; }
    public void setAdmin(boolean admin) { isAdmin = admin; }

    public boolean isPremium() { return isPremium; }
    public void setPremium(boolean premium) { isPremium = premium; }

    public String getProfileURL() { return profileURL; }
    public void setProfileURL(String profileURL) { this.profileURL = profileURL; }
    @PropertyName("isHistoryPaused") // Ép tên trường trong DB là "isHistoryPaused"
    public boolean isHistoryPaused() {
        return isHistoryPaused;
    }
    @PropertyName("isHistoryPaused")
    public void setHistoryPaused(boolean historyPaused) {
        isHistoryPaused = historyPaused;
    }
    public Map<String, Boolean> getNotificationSettings() {
        return notificationSettings;
    }

    public void setNotificationSettings(Map<String, Boolean> notificationSettings) {
        this.notificationSettings = notificationSettings;
    }
}
