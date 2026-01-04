package com.example.metube.ui.notifications;

import android.util.Log;
import com.example.metube.model.Notification;
import com.example.metube.model.Video;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.Arrays;
import java.util.Map;

public class NotificationHelper {
    private static final String TAG = "NotificationHelper";

    /**
     * 1. Thông báo cho tất cả Subscriber khi có video mới (Dùng trong UploadActivity)
     */
    public static void notifySubscribersAboutNewVideo(Video video, String uploaderName) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("subscriptions")
                .whereEqualTo("uploaderID", video.getUploaderID())
                .whereIn("status", Arrays.asList("SUBSCRIBED", "MEMBERSHIP"))
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (QueryDocumentSnapshot subDoc : querySnapshot) {
                        String subscriberID = subDoc.getString("viewerID");
                        if (subscriberID != null) {
                            // Kiểm tra xem Subscriber có bật nhận thông báo Subscriptions (key_sub) không
                            checkSettingsAndCreate(subscriberID, video.getUploaderID(), video.getVideoID(),
                                    "key_sub", "NEW_VIDEO", uploaderName + " uploaded a new video",
                                    video.getTitle(), video.getThumbnailURL());
                        }
                    }
                });
    }

    /**
     * 2. Thông báo cho chủ kênh khi có người Subscribe (Dùng trong VideoActivity)
     */
    public static void notifyOwnerAboutNewSubscriber(String uploaderID, String subscriberID) {
        // ✅ Tự động lấy tên từ Firestore
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(subscriberID)
                .get()
                .addOnSuccessListener(doc -> {
                    String subscriberName = "Someone";

                    if (doc.exists()) {
                        String name = doc.getString("name");
                        if (name != null && !name.isEmpty()) {
                            subscriberName = name;
                        }
                    }

                    checkSettingsAndCreate(
                            uploaderID,
                            subscriberID,  // ✅ Dùng subscriberID để lấy avatar
                            null,
                            "key_channel",
                            "NEW_SUBSCRIBER",
                            "New Subscriber!",
                            subscriberName + " just subscribed to your channel.",
                            null
                    );
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get subscriber name", e);
                });
    }

    /**
     * 3. Thông báo cho chủ kênh khi đạt mốc View (Dùng trong VideoActivity)
     */
    public static void notifyOwnerAboutViewMilestone(String uploaderID, String videoID, String videoTitle, long views, String thumb) {
        // ✅ SỬA 2: Đổi "system" → uploaderID để lấy avatar của chính channel
        checkSettingsAndCreate(
                uploaderID,      // recipientID
                uploaderID,      // ✅ senderID = uploaderID (để lấy avatar)
                videoID,
                "key_channel",
                "VIEW_MILESTONE",
                "View Milestone reached!",
                "Your video '" + videoTitle + "' just hit " + views + " views!",
                thumb
        );
    }

    /**
     * 4. Thông báo cho chủ kênh khi có bình luận mới
     */
    public static void notifyOwnerAboutNewComment(String uploaderID, String videoID, String userName, String commentText, String thumb) {
        String currentUid = FirebaseAuth.getInstance().getUid();
        checkSettingsAndCreate(uploaderID, currentUid, videoID, "key_comments",
                "NEW_COMMENT", "New Comment",
                userName + " commented: " + commentText, thumb);
    }

    private static void checkSettingsAndCreate(String recipientID, String senderID, String videoID,
                                               String prefKey, String type, String title, String message, String thumb) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users").document(recipientID).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                Map<String, Object> settings = (Map<String, Object>) doc.get("notificationSettings");
                boolean isEnabled = true;
                if (settings != null && settings.containsKey(prefKey)) {
                    isEnabled = (boolean) settings.get(prefKey);
                }

                if (isEnabled) {
                    sendToFirestore(recipientID, senderID, videoID, type, title, message, thumb);
                }
            }
        });
    }

    private static void sendToFirestore(String recipientID, String senderID, String videoID,
                                        String type, String title, String message, String thumb) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String notificationID = db.collection("notifications").document().getId();

        Notification notification = new Notification(
                notificationID, recipientID, senderID, videoID, type, title, message, thumb, false, Timestamp.now()
        );

        db.collection("notifications").document(notificationID).set(notification);
    }
}