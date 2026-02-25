package com.example.workconnect.repository.notifications;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.workconnect.models.AppNotification;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository for notification CRUD/listening in Firestore.
 * Path: users/{uid}/notifications
 */
public class NotificationsRepository {

    private static final String TAG = "NotifRepo";

    // Firestore entry point
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /**
     * Real-time listener for user's notifications.
     * Returns LiveData so UI can observe changes automatically.
     */
    public LiveData<List<AppNotification>> listenNotifications(@NonNull String uid) {
        // Default value is empty list to avoid null handling in UI
        MutableLiveData<List<AppNotification>> live = new MutableLiveData<>(new ArrayList<>());

        // Listen on subcollection: users/{uid}/notifications
        db.collection("users")
                .document(uid)
                .collection("notifications")
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) {
                        // On listener error return empty list (fail-safe for UI)
                        Log.e(TAG, "listenNotifications error", e);
                        live.postValue(new ArrayList<>());
                        return;
                    }

                    // Map Firestore docs to AppNotification model
                    List<AppNotification> list = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        AppNotification n = d.toObject(AppNotification.class);
                        if (n != null) {
                            // Keep document id for later delete / navigation
                            n.setId(d.getId());
                            list.add(n);
                        }
                    }

                    Log.d(TAG, "listenNotifications size=" + list.size());
                    live.postValue(list);
                });

        return live;
    }

    /**
     * Deletes a notification document by id.
     */
    public void deleteNotification(@NonNull String uid, @NonNull String notificationId) {
        db.collection("users")
                .document(uid)
                .collection("notifications")
                .document(notificationId)
                .delete()
                .addOnSuccessListener(v -> Log.d(TAG, "delete OK: " + notificationId))
                .addOnFailureListener(e -> Log.e(TAG, "delete FAILED: " + notificationId, e));
    }
}