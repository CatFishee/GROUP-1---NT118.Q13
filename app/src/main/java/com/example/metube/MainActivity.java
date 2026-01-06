package com.example.metube;

import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
    private void migrateExistingUsers() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Map<String, Object> defaultSettings = new HashMap<>();
        defaultSettings.put("key_sub", true);
        defaultSettings.put("key_comments", true);
        defaultSettings.put("key_channel", true);

        db.collection("users").get()
                .addOnSuccessListener(querySnapshot -> {
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        if (!doc.contains("notificationSettings")) {
                            Log.d("Migration", "Adding notificationSettings to user: " + doc.getId());

                            db.collection("users")
                                    .document(doc.getId())
                                    .update("notificationSettings", defaultSettings)
                                    .addOnSuccessListener(aVoid -> {
                                        Log.d("Migration", "✅ Updated user: " + doc.getId());
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e("Migration", "❌ Failed to update user: " + doc.getId(), e);
                                    });
                        }
                    }
                });
    }
}