package com.example.metube.service;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * FirestoreService
 * ----------------
 * Lớp tổng quát để thao tác với Firestore
 *
 * Ví dụ sử dụng:
 * FirestoreService db = FirestoreService.getInstance();
 * db.add("users", userObject);
 * db.getAll("videos", Video.class, list -> { ... });
 */
public class FirestoreService {

    private static FirestoreService instance;
    private final FirebaseFirestore db;

    private FirestoreService() {
        db = FirebaseFirestore.getInstance();
    }

    public static synchronized FirestoreService getInstance() {
        if (instance == null) {
            instance = new FirestoreService();
        }
        return instance;
    }

    // -----------------------------
    // CREATE
    // -----------------------------
    public <T> void add(String collection, T data,
                        OnSuccessListener<DocumentReference> onSuccess,
                        OnFailureListener onFailure) {
        db.collection(collection).add(data)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    // -----------------------------
    // READ - Get all documents
    // -----------------------------
    public <T> void getAll(String collection, Class<T> clazz,
                           Consumer<List<T>> callback) {
        db.collection(collection)
                .get()
                .addOnSuccessListener(query -> {
                    List<T> items = new ArrayList<>();
                    for (DocumentSnapshot doc : query.getDocuments()) {
                        items.add(doc.toObject(clazz));
                    }
                    callback.accept(items);
                })
                .addOnFailureListener(e ->
                        Log.e("FirestoreService", "Error getting " + collection, e));
    }

    // -----------------------------
    // READ - Get document by ID
    // -----------------------------
    public <T> void getById(String collection, String id, Class<T> clazz,
                            Consumer<T> callback) {
        db.collection(collection)
                .document(id)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        callback.accept(doc.toObject(clazz));
                    } else {
                        callback.accept(null);
                    }
                })
                .addOnFailureListener(e ->
                        Log.e("FirestoreService", "Error getting document", e));
    }

    // -----------------------------
    // UPDATE - Update document by ID
    // -----------------------------
    public void update(String collection, String id, Object data,
                       OnSuccessListener<Void> onSuccess,
                       OnFailureListener onFailure) {
        db.collection(collection)
                .document(id)
                .set(data)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    // -----------------------------
    // DELETE
    // -----------------------------
    public void delete(String collection, String id,
                       OnSuccessListener<Void> onSuccess,
                       OnFailureListener onFailure) {
        db.collection(collection)
                .document(id)
                .delete()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    // -----------------------------
    // QUERY - Có điều kiện
    // -----------------------------
    public <T> void query(String collection, String field, Object value,
                          Class<T> clazz, Consumer<List<T>> callback) {
        db.collection(collection)
                .whereEqualTo(field, value)
                .get()
                .addOnSuccessListener(query -> {
                    List<T> items = new ArrayList<>();
                    for (DocumentSnapshot doc : query.getDocuments()) {
                        items.add(doc.toObject(clazz));
                    }
                    callback.accept(items);
                })
                .addOnFailureListener(e ->
                        Log.e("FirestoreService", "Error querying " + collection, e));
    }
}
