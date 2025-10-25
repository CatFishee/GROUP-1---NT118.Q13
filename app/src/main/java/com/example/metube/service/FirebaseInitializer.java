package com.example.metube.service;

import android.app.Application;
import android.util.Log;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;

public class FirebaseInitializer extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Khởi tạo Firebase cho toàn app
        FirebaseApp.initializeApp(this);

        // Thiết lập Firestore (tùy chọn, để bật cache offline)
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build();
        db.setFirestoreSettings(settings);

        Log.d("FirebaseInit", "Firebase initialized successfully");
    }
}
