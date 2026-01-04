package com.example.metube.utils;

import android.util.Log;
import com.example.metube.model.Notification;
import com.example.metube.model.Video;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.Arrays;

public class NotificationHelper {
    private static final String TAG = "NotificationHelper";

    /**
     * Tạo thông báo cho tất cả subscribers khi có video mới
     */
    public static void notifySubscribersAboutNewVideo(Video video, String uploaderName) {
        if (video == null || video.getUploaderID() == null) {
            Log.e(TAG, "Invalid video or uploaderID");
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // 1. Tìm tất cả người đã subscribe channel này
        db.collection("subscriptions")
                .whereEqualTo("uploaderID", video.getUploaderID())
                .whereIn("status", Arrays.asList("SUBSCRIBED", "MEMBERSHIP"))
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    int subscriberCount = querySnapshot.size();
                    Log.d(TAG, "Found " + subscriberCount + " subscribers for channel: " + video.getUploaderID());

                    // 2. Tạo notification cho từng subscriber
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String subscriberID = doc.getString("viewerID");

                        if (subscriberID != null) {
                            createNotification(
                                    subscriberID,
                                    video.getUploaderID(),
                                    video.getVideoID(),
                                    uploaderName,
                                    video.getTitle(),
                                    video.getThumbnailURL()
                            );
                        }
                    }

                    Log.d(TAG, "✅ Created notifications for " + subscriberCount + " subscribers");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get subscribers", e);
                });
    }

    /**
     * Tạo 1 notification document trong Firestore
     */
    private static void createNotification(String recipientID, String senderID,
                                           String videoID, String uploaderName,
                                           String videoTitle, String thumbnailURL) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String notificationID = db.collection("notifications").document().getId();

        String title = uploaderName + " uploaded a new video";
        String message = videoTitle;

        Notification notification = new Notification(
                notificationID,
                recipientID,
                senderID,
                videoID,
                "NEW_VIDEO",
                title,
                message,
                thumbnailURL,
                false, // isRead = false
                Timestamp.now()
        );

        db.collection("notifications")
                .document(notificationID)
                .set(notification)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ Notification created for user: " + recipientID);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to create notification", e);
                });
    }
}