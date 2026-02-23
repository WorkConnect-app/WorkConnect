package com.example.workconnect.repository.notifications;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.workconnect.models.AppNotification;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class NotificationsRepository {

    private static final String TAG = "NotifRepo";
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public LiveData<List<AppNotification>> listenNotifications(@NonNull String uid) {
        MutableLiveData<List<AppNotification>> live = new MutableLiveData<>(new ArrayList<>());

        db.collection("users")
                .document(uid)
                .collection("notifications")
               // .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) {
                        Log.e(TAG, "listenNotifications error", e);
                        live.postValue(new ArrayList<>());
                        return;
                    }

                    List<AppNotification> list = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        AppNotification n = d.toObject(AppNotification.class);
                        if (n != null) {
                            n.setId(d.getId());
                            list.add(n);
                        }
                    }

                    Log.d(TAG, "listenNotifications size=" + list.size());
                    live.postValue(list);
                });

        return live;
    }

    public void markRead(@NonNull String uid, @NonNull String notificationId) {
        db.collection("users")
                .document(uid)
                .collection("notifications")
                .document(notificationId)
                .update("read", true)
                .addOnSuccessListener(v -> Log.d(TAG, "markRead OK: " + notificationId))
                .addOnFailureListener(e -> Log.e(TAG, "markRead FAILED: " + notificationId, e));
    }

    public LiveData<Integer> listenUnreadCount(@NonNull String uid) {
        MutableLiveData<Integer> live = new MutableLiveData<>(0);

        db.collection("users")
                .document(uid)
                .collection("notifications")
                .whereEqualTo("read", false)
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) {
                        Log.e(TAG, "listenUnreadCount error", e);
                        live.postValue(0);
                        return;
                    }
                    int c = snap.size();
                    Log.d(TAG, "listenUnreadCount=" + c);
                    live.postValue(c);
                });

        return live;
    }

    public void createNotification(@NonNull String uid, @NonNull AppNotification notification) {
        db.collection("users")
                .document(uid)
                .collection("notifications")
                .add(notification)
                .addOnSuccessListener(ref -> Log.d(TAG, "createNotification OK: " + ref.getId()))
                .addOnFailureListener(e -> Log.e(TAG, "createNotification FAILED", e));
    }

    public DocumentReference newNotificationRef(@NonNull String uid) {
        return db.collection("users")
                .document(uid)
                .collection("notifications")
                .document();
    }

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