package com.example.workconnect.repository.shifts;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.workconnect.models.ShiftAssignment;
import com.example.workconnect.models.ShiftTemplate;
import com.example.workconnect.services.NotificationService;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ShiftAssignmentRepository {

    public interface SimpleCallback {
        void onComplete(boolean success, String message);
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public LiveData<List<ShiftAssignment>> listenAssignmentsForDate(String companyId, String teamId, String dateKey) {
        MutableLiveData<List<ShiftAssignment>> live = new MutableLiveData<>(new ArrayList<>());

        db.collection("companies").document(companyId)
                .collection("teams").document(teamId)
                .collection("assignments").document(dateKey)
                .collection("items") // NOTE: "items" = shift assignments for this date (may rename later)

                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) {
                        live.postValue(new ArrayList<>());
                        return;
                    }

                    List<ShiftAssignment> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        ShiftAssignment a = doc.toObject(ShiftAssignment.class);
                        if (a != null) {
                            // doc id is the userId (but keep it anyway)
                            a.setId(doc.getId());
                            if (a.getUserId() == null || a.getUserId().trim().isEmpty()) {
                                a.setUserId(doc.getId());
                            }
                            list.add(a);
                        }
                    }
                    live.postValue(list);
                });

        return live;
    }

    /**
     * Enforces: ONE shift per user per day.
     *
     * - For each selected user: we set docId=userId to this template => moves them if they had another template.
     * - For users previously on THIS template but now unselected: delete their doc.
     */
    public void setAssignmentsForTemplate(
            String companyId,
            String teamId,
            String dateKey,
            ShiftTemplate template,
            List<String> selectedUserIds,
            SimpleCallback cb
    ) {
        if (template == null || template.getId() == null) {
            cb.onComplete(false, "Template is missing");
            return;
        }

        DocumentReference dayDoc = db.collection("companies").document(companyId)
                .collection("teams").document(teamId)
                .collection("assignments").document(dateKey);

        com.google.firebase.firestore.CollectionReference itemsCol = dayDoc.collection("items");

        // 1) Read existing items for this day so we can delete unselected + move users
        itemsCol.get()
                .addOnSuccessListener(allSnap -> {

                    com.google.firebase.firestore.WriteBatch batch = db.batch();

                    // 2) ✅ Ensure parent date doc exists (THIS is the critical fix)
                    HashMap<String, Object> header = new HashMap<>();
                    header.put("dateKey", dateKey);
                    header.put("updatedAt", System.currentTimeMillis());
                    batch.set(dayDoc, header, SetOptions.merge());

                    // 3) Build map userId -> existing assignment
                    HashMap<String, ShiftAssignment> existing = new HashMap<>();
                    for (DocumentSnapshot doc : allSnap.getDocuments()) {
                        ShiftAssignment a = doc.toObject(ShiftAssignment.class);
                        if (a != null) {
                            String uid = a.getUserId();
                            if (uid == null || uid.trim().isEmpty()) uid = doc.getId();
                            a.setUserId(uid);
                            a.setId(doc.getId());
                            existing.put(uid, a);
                        }
                    }

                    // 4) Normalize selected list (trim / remove empty)
                    ArrayList<String> selected = new ArrayList<>();
                    if (selectedUserIds != null) {
                        for (String uid : selectedUserIds) {
                            if (uid != null) {
                                String t = uid.trim();
                                if (!t.isEmpty()) selected.add(t);
                            }
                        }
                    }

                    // 5) Delete users that were on THIS template but are now unselected
                    for (String uid : existing.keySet()) {
                        ShiftAssignment a = existing.get(uid);
                        if (a == null) continue;

                        boolean wasOnThisTemplate = template.getId().equals(a.getTemplateId());
                        boolean stillSelected = selected.contains(uid);

                        if (wasOnThisTemplate && !stillSelected) {
                            DocumentReference ref = itemsCol.document(uid);

                            NotificationService.addShiftRemoved(
                                    batch,
                                    uid,
                                    companyId,
                                    teamId,
                                    dateKey,
                                    template.getTitle() == null ? "" : template.getTitle()
                            );

                            batch.delete(ref);
                        }
                    }

                    // 6) Upsert selected users to THIS template (moves them automatically)
                    for (String uid : selected) {
                        DocumentReference ref = itemsCol.document(uid);

                        HashMap<String, Object> data = new HashMap<>();
                        data.put("userId", uid);
                        data.put("templateId", template.getId());
                        data.put("templateTitle", template.getTitle() == null ? "" : template.getTitle());
                        data.put("startHour", template.getStartHour());
                        data.put("endHour", template.getEndHour());
                        data.put("createdAt", FieldValue.serverTimestamp());

                        ShiftAssignment prev = existing.get(uid);

                        if (prev == null) {
                            NotificationService.addShiftAssigned(
                                    batch,
                                    uid,
                                    companyId,
                                    teamId,
                                    dateKey,
                                    template.getTitle() == null ? "" : template.getTitle()
                            );
                        } else if (prev.getTemplateId() != null && !prev.getTemplateId().equals(template.getId())) {
                            NotificationService.addShiftChanged(
                                    batch,
                                    uid,
                                    companyId,
                                    teamId,
                                    dateKey,
                                    prev.getTemplateTitle() == null ? "" : prev.getTemplateTitle(),
                                    template.getTitle() == null ? "" : template.getTitle()
                            );
                        }

                        batch.set(ref, data, SetOptions.merge());
                    }

                    // 7) Commit
                    batch.commit()
                            .addOnSuccessListener(unused ->
                                    cb.onComplete(true, "Saved")
                            )
                            .addOnFailureListener(e ->
                                    cb.onComplete(false, e.getMessage() == null ? "Failed to save" : e.getMessage())
                            );

                })
                .addOnFailureListener(e ->
                        cb.onComplete(false, e.getMessage() == null ? "Failed to load assignments" : e.getMessage())
                );
    }

}
