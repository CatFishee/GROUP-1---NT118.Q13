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
    public static void notifyOwnerAboutNewComment(String uploaderID, String videoID, String userName, String commentText, String videoThumb) {
        String currentUid = FirebaseAuth.getInstance().getUid();

        Log.d(TAG, "════════════════════════════════════════");
        Log.d(TAG, "🔔 notifyOwnerAboutNewComment CALLED");
        Log.d(TAG, "uploaderID: " + uploaderID);
        Log.d(TAG, "videoID: " + videoID);
        Log.d(TAG, "userName: " + userName);
        Log.d(TAG, "commentText: " + commentText);
        Log.d(TAG, "videoThumb: " + videoThumb);
        Log.d(TAG, "currentUid: " + currentUid);
        Log.d(TAG, "════════════════════════════════════════");

        if (currentUid == null) {
            Log.e(TAG, "❌ currentUid is NULL - Cannot send notification");
            return;
        }

        // ✅ ĐIỀU KIỆN: Nếu chủ kênh tự comment trên video của mình thì KHÔNG gửi thông báo
        if (currentUid.equals(uploaderID)) {
            Log.d(TAG, "⚠️ Owner commented on their own video - No notification sent.");
            return;
        }

        Log.d(TAG, "✅ Proceeding to checkSettingsAndCreate...");

        checkSettingsAndCreate(
                uploaderID,    // recipientID: Chủ kênh
                currentUid,    // senderID: Người comment (để hiện avatar)
                videoID,       // videoID: Để nhấn vào mở video
                "key_comments",
                "NEW_COMMENT",
                "New comment on your video",
                userName + ": " + commentText,
                videoThumb
        );
    }

    private static void checkSettingsAndCreate(String recipientID, String senderID, String videoID,
                                               String prefKey, String type, String title, String message, String thumb) {
        Log.d(TAG, "────────────────────────────────────────");
        Log.d(TAG, "🔍 checkSettingsAndCreate CALLED");
        Log.d(TAG, "recipientID: " + recipientID);
        Log.d(TAG, "senderID: " + senderID);
        Log.d(TAG, "prefKey: " + prefKey);
        Log.d(TAG, "────────────────────────────────────────");

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users").document(recipientID).get()
                .addOnSuccessListener(doc -> {
                    Log.d(TAG, "✅ User document fetched successfully");

                    if (doc.exists()) {
                        Log.d(TAG, "✅ User document exists");

                        // Mặc định là bật (true) nếu người dùng chưa bao giờ vào cài đặt
                        boolean isEnabled = true;

                        if (doc.contains("notificationSettings")) {
                            Log.d(TAG, "📋 User has notificationSettings");
                            Map<String, Object> settings = (Map<String, Object>) doc.get("notificationSettings");
                            if (settings != null && settings.containsKey(prefKey)) {
                                isEnabled = (boolean) settings.get(prefKey);
                                Log.d(TAG, prefKey + " = " + isEnabled);
                            } else {
                                Log.d(TAG, "⚠️ " + prefKey + " not found in settings, using default: true");
                            }
                        } else {
                            Log.d(TAG, "⚠️ notificationSettings field not found, using default: true");
                        }

                        // Chỉ gửi nếu isEnabled là true
                        if (isEnabled) {
                            Log.d(TAG, "✅ Notification enabled - Proceeding to sendToFirestore");
                            sendToFirestore(recipientID, senderID, videoID, type, title, message, thumb);
                        } else {
                            Log.d(TAG, "❌ Notification DISABLED by user settings");
                        }
                    } else {
                        Log.e(TAG, "❌ User document does NOT exist for recipientID: " + recipientID);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ FAILED to fetch user document", e);
                    Log.e(TAG, "Error message: " + e.getMessage());
                });
    }

    private static void sendToFirestore(String recipientID, String senderID, String videoID,
                                        String type, String title, String message, String thumb) {
        Log.d(TAG, "════════════════════════════════════════");
        Log.d(TAG, "💾 sendToFirestore CALLED");
        Log.d(TAG, "recipientID: " + recipientID);
        Log.d(TAG, "senderID: " + senderID);
        Log.d(TAG, "videoID: " + videoID);
        Log.d(TAG, "type: " + type);
        Log.d(TAG, "title: " + title);
        Log.d(TAG, "message: " + message);
        Log.d(TAG, "thumb: " + thumb);
        Log.d(TAG, "════════════════════════════════════════");

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String notificationID = db.collection("notifications").document().getId();

        Log.d(TAG, "📝 Generated notificationID: " + notificationID);

        Notification notification = new Notification(
                notificationID, recipientID, senderID, videoID, type, title, message, thumb, false, Timestamp.now()
        );

        Log.d(TAG, "🚀 Attempting to write to Firestore...");

        db.collection("notifications").document(notificationID).set(notification)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅✅✅ NOTIFICATION WRITTEN SUCCESSFULLY! ✅✅✅");
                    Log.d(TAG, "Document ID: " + notificationID);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌❌❌ FAILED TO WRITE NOTIFICATION ❌❌❌");
                    Log.e(TAG, "Error type: " + e.getClass().getName());
                    Log.e(TAG, "Error message: " + e.getMessage());
                    Log.e(TAG, "Stack trace:", e);
                });
    }
}