package com.example.workconnect.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AvailabilityRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public interface SimpleCallback {
        void onComplete(boolean success, String message);
    }
    public interface AvailabilityMapCallback {
        void onComplete(@NonNull Map<String, String> uidToStatus);
    }

    /**
     * Returns map key: teamId|dateKey|shiftId -> status ("CAN"/"PREFER_NOT"/"CANT")
     */
    public LiveData<Map<String, String>> listenMyAvailability(
            @NonNull String companyId,
            @NonNull List<String> teamIds,
            @NonNull List<String> dateKeys,
            @NonNull List<String> shiftIds,
            @NonNull String uid
    ) {
        MutableLiveData<Map<String, String>> live = new MutableLiveData<>(new HashMap<>());
        Map<String, String> state = new HashMap<>();

        if (teamIds.isEmpty() || dateKeys.isEmpty() || shiftIds.isEmpty() || uid.trim().isEmpty()) {
            live.postValue(state);
            return live;
        }

        for (String teamId : teamIds) {
            for (String dateKey : dateKeys) {
                for (String shiftId : shiftIds) {
                    db.collection("companies").document(companyId)
                            .collection("teams").document(teamId)
                            .collection("availability").document(dateKey)
                            .collection("shifts").document(shiftId)
                            .collection("users").document(uid)
                            .addSnapshotListener((doc, e) -> {
                                String k = teamId + "|" + dateKey + "|" + shiftId;

                                if (e != null || doc == null || !doc.exists()) {
                                    state.remove(k); // not chosen
                                    live.postValue(new HashMap<>(state));
                                    return;
                                }

                                String status = doc.getString("status");
                                if (status == null) status = "CAN";
                                state.put(k, status);

                                live.postValue(new HashMap<>(state));
                            });
                }
            }
        }

        return live;
    }

    public void setMyAvailability(
            @NonNull String companyId,
            @NonNull String teamId,
            @NonNull String dateKey,
            @NonNull String shiftId,
            @NonNull String uid,
            @NonNull String status,
            @NonNull SimpleCallback cb
    ) {
        HashMap<String, Object> data = new HashMap<>();
        data.put("userId", uid);
        data.put("status", status);
        data.put("updatedAt", FieldValue.serverTimestamp());

        db.collection("companies").document(companyId)
                .collection("teams").document(teamId)
                .collection("availability").document(dateKey)
                .collection("shifts").document(shiftId)
                .collection("users").document(uid)
                .set(data)
                .addOnSuccessListener(unused -> cb.onComplete(true, "Saved"))
                .addOnFailureListener(e -> cb.onComplete(false, e.getMessage() == null ? "Failed" : e.getMessage()));
    }
    public void getAvailabilityForShift(
            @NonNull String companyId,
            @NonNull String teamId,
            @NonNull String dateKey,
            @NonNull String shiftId,
            @NonNull AvailabilityMapCallback cb
    ) {
        db.collection("companies").document(companyId)
                .collection("teams").document(teamId)
                .collection("availability").document(dateKey)
                .collection("shifts").document(shiftId)
                .collection("users")
                .get()
                .addOnSuccessListener(snap -> {
                    HashMap<String, String> map = new HashMap<>();
                    if (snap != null) {
                        for (var doc : snap.getDocuments()) {
                            String uid = doc.getId();
                            String status = doc.getString("status");
                            if (status == null) status = "CAN";
                            map.put(uid, status);
                        }
                    }
                    cb.onComplete(map);
                })
                .addOnFailureListener(e -> cb.onComplete(new HashMap<>()));
    }
    public void clearMyAvailability(
            @NonNull String companyId,
            @NonNull String teamId,
            @NonNull String dateKey,
            @NonNull String shiftId,
            @NonNull String uid,
            @NonNull SimpleCallback cb
    ) {
        db.collection("companies").document(companyId)
                .collection("teams").document(teamId)
                .collection("availability").document(dateKey)
                .collection("shifts").document(shiftId)
                .collection("users").document(uid)
                .delete()
                .addOnSuccessListener(unused -> cb.onComplete(true, "Cleared"))
                .addOnFailureListener(e -> cb.onComplete(false, e.getMessage() == null ? "Failed" : e.getMessage()));
    }


}
