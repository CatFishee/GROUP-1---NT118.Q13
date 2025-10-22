package com.example.metube.model;

public class User {
    private String userID;
    private String name;
    private String email;
    private String password;
    private  boolean isAdmin;
    private  boolean isPremium;
    private String profileURL;
    public User () {}
    public User (String userID, String name, String email, String password, boolean isAdmin, boolean isPremium, String profileURL) {
        this.userID = userID;
        this.name = name;
        this.email = email;
        this.password = password;
        this.isAdmin = isAdmin;
        this.isPremium = isPremium;
        this.profileURL = profileURL;
    }

    public String getUserID () {return userID;}
    public  void setUserID (String userID) {this.userID = userID;}
    public String getName () {return name;}
    public void setName (String name) {this.name = name;}
    public String getPassword () {return password;}
    public void setPassword (String password) {this.password = password;}
    public boolean isAdmin () {return isAdmin;}
    public void setAdmin (boolean admin) {isAdmin = admin;}
    public  boolean isPremium () {return isPremium;}
    public  void setPremium (boolean premium) {isPremium = premium;}
    public String getProfileURL () {return profileURL;}
    public void setProfileURL (String profileURL) {this.profileURL = profileURL;}
}

