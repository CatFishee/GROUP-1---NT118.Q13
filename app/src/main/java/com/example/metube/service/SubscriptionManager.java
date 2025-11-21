package com.example.metube.service;

import android.util.Log;
import androidx.annotation.NonNull;

import com.example.metube.model.ContentCreatorStat;
import com.example.metube.model.Subscription;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.Transaction;

import java.util.Calendar;
import java.util.Date;

/**
 * SubscriptionManager
 * Handles subscription logic and automatically updates ContentCreatorStat
 */
public class SubscriptionManager {
    private static final String TAG = "SubscriptionManager";
    private static SubscriptionManager instance;
    private final FirebaseFirestore db;

    private SubscriptionManager() {
        db = FirebaseFirestore.getInstance();
    }

    public static synchronized SubscriptionManager getInstance() {
        if (instance == null) {
            instance = new SubscriptionManager();
        }
        return instance;
    }

    /**
     * Subscribe to a creator
     */
    public void subscribe(String viewerId, String uploaderId, OnSubscriptionListener listener) {
        if (viewerId.equals(uploaderId)) {
            listener.onFailure(new Exception("Cannot subscribe to yourself"));
            return;
        }

        String subscriptionId = db.collection("subscriptions").document().getId();
        Subscription subscription = new Subscription(
                subscriptionId,
                uploaderId,
                viewerId,
                Timestamp.now(),
                Subscription.Status.SUBSCRIBED
        );

        db.collection("subscriptions").document(subscriptionId)
                .set(subscription)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Subscription created: " + subscriptionId);
                    updateCreatorStats(uploaderId, 1, 0); // +1 subscriber gained
                    listener.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create subscription", e);
                    listener.onFailure(e);
                });
    }

    /**
     * Unsubscribe from a creator
     */
    public void unsubscribe(String viewerId, String uploaderId, OnSubscriptionListener listener) {
        db.collection("subscriptions")
                .whereEqualTo("viewerID", viewerId)
                .whereEqualTo("uploaderID", uploaderId)
                .whereEqualTo("status", Subscription.Status.SUBSCRIBED.name())
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                        doc.getReference().update("status", Subscription.Status.UNSUBSCRIBED.name())
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "Unsubscribed from: " + uploaderId);
                                    updateCreatorStats(uploaderId, 0, 1); // +1 subscriber lost
                                    listener.onSuccess();
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to unsubscribe", e);
                                    listener.onFailure(e);
                                });
                    } else {
                        listener.onFailure(new Exception("Subscription not found"));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to query subscription", e);
                    listener.onFailure(e);
                });
    }

    /**
     * Check if viewer is subscribed to uploader
     */
    public void isSubscribed(String viewerId, String uploaderId, OnCheckSubscriptionListener listener) {
        db.collection("subscriptions")
                .whereEqualTo("viewerID", viewerId)
                .whereEqualTo("uploaderID", uploaderId)
                .whereEqualTo("status", Subscription.Status.SUBSCRIBED.name())
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    listener.onResult(!querySnapshot.isEmpty());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check subscription", e);
                    listener.onResult(false);
                });
    }

    /**
     * Get total subscriber count for a creator
     */
    public void getSubscriberCount(String uploaderId, OnCountListener listener) {
        db.collection("subscriptions")
                .whereEqualTo("uploaderID", uploaderId)
                .whereEqualTo("status", Subscription.Status.SUBSCRIBED.name())
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    listener.onCount(querySnapshot.size());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get subscriber count", e);
                    listener.onCount(0);
                });
    }

    /**
     * Update or create today's ContentCreatorStat entry
     */
    private void updateCreatorStats(String userId, int subGained, int subLost) {
        Date today = getTodayMidnight();
        Timestamp todayTimestamp = new Timestamp(today);

        db.collection("contentCreatorStats")
                .whereEqualTo("userID", userId)
                .whereGreaterThanOrEqualTo("createdAt", todayTimestamp)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        // Update existing stat for today
                        DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                        ContentCreatorStat stat = doc.toObject(ContentCreatorStat.class);
                        if (stat != null) {
                            stat.setSubGained(stat.getSubGained() + subGained);
                            stat.setSubLost(stat.getSubLost() + subLost);
                            doc.getReference().set(stat)
                                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Updated today's stat"))
                                    .addOnFailureListener(e -> Log.e(TAG, "Failed to update stat", e));
                        }
                    } else {
                        // Create new stat for today
                        String statId = db.collection("contentCreatorStats").document().getId();
                        ContentCreatorStat newStat = new ContentCreatorStat(
                                statId,
                                userId,
                                subGained,
                                subLost,
                                todayTimestamp
                        );
                        db.collection("contentCreatorStats").document(statId).set(newStat)
                                .addOnSuccessListener(aVoid -> Log.d(TAG, "Created today's stat"))
                                .addOnFailureListener(e -> Log.e(TAG, "Failed to create stat", e));
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to query stats", e));

        // Cleanup old stats (>30 days)
        cleanupOldStats(userId);
    }

    /**
     * Delete ContentCreatorStat records older than 30 days
     */
    private void cleanupOldStats(String userId) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -30);
        Timestamp thirtyDaysAgo = new Timestamp(cal.getTime());

        db.collection("contentCreatorStats")
                .whereEqualTo("userID", userId)
                .whereLessThan("createdAt", thirtyDaysAgo)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        doc.getReference().delete()
                                .addOnSuccessListener(aVoid -> Log.d(TAG, "Deleted old stat: " + doc.getId()))
                                .addOnFailureListener(e -> Log.e(TAG, "Failed to delete old stat", e));
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to cleanup old stats", e));
    }

    /**
     * Get today's date at midnight
     */
    private Date getTodayMidnight() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    // Listener Interfaces
    public interface OnSubscriptionListener {
        void onSuccess();
        void onFailure(Exception e);
    }

    public interface OnCheckSubscriptionListener {
        void onResult(boolean isSubscribed);
    }

    public interface OnCountListener {
        void onCount(int count);
    }
}