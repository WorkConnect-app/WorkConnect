package com.example.workconnect.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.workconnect.models.ShiftAssignment;
import com.example.workconnect.models.ShiftSwapOffer;
import com.example.workconnect.models.ShiftSwapRequest;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ShiftSwapRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private DocumentReference reqDoc(String companyId, String teamId, String requestId) {
        return db.collection("companies").document(companyId)
                .collection("teams").document(teamId)
                .collection("swapRequests").document(requestId);
    }

    private CollectionReference reqCol(String companyId, String teamId) {
        return db.collection("companies").document(companyId)
                .collection("teams").document(teamId)
                .collection("swapRequests");
    }

    private DocumentReference assignmentItemDoc(String companyId, String teamId, String dateKey, String userUid) {
        return db.collection("companies").document(companyId)
                .collection("teams").document(teamId)
                .collection("assignments").document(dateKey)
                .collection("items").document(userUid);
    }

    public interface SimpleCallback {
        void onDone(boolean success, String msg);
    }

    // -----------------------
    // LISTENERS
    // -----------------------

    public LiveData<List<ShiftSwapRequest>> listenMyRequests(String companyId, String teamId, String myUid) {
        MutableLiveData<List<ShiftSwapRequest>> live = new MutableLiveData<>(new ArrayList<>());

        reqCol(companyId, teamId)
                .whereEqualTo("requesterUid", myUid)
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) return;

                    List<ShiftSwapRequest> out = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        ShiftSwapRequest r = d.toObject(ShiftSwapRequest.class);
                        if (r != null) {
                            r.setId(d.getId());
                            out.add(r);
                        }
                    }

                    Collections.sort(out, (a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
                    live.postValue(out);
                });

        return live;
    }

    public LiveData<List<ShiftSwapRequest>> listenOpenRequests(String companyId, String teamId, String myUid) {
        MutableLiveData<List<ShiftSwapRequest>> live = new MutableLiveData<>(new ArrayList<>());

        reqCol(companyId, teamId)
                .whereEqualTo("status", ShiftSwapRequest.OPEN)
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) return;

                    List<ShiftSwapRequest> out = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        ShiftSwapRequest r = d.toObject(ShiftSwapRequest.class);
                        if (r == null) continue;

                        r.setId(d.getId());
                        if (myUid != null && myUid.equals(r.getRequesterUid())) continue;
                        out.add(r);
                    }

                    Collections.sort(out, (a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
                    live.postValue(out);
                });

        return live;
    }

    public LiveData<List<ShiftSwapRequest>> listenPendingApprovals(String companyId, String teamId) {
        MutableLiveData<List<ShiftSwapRequest>> live = new MutableLiveData<>(new ArrayList<>());

        reqCol(companyId, teamId)
                .whereEqualTo("status", ShiftSwapRequest.PENDING_APPROVAL)
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) return;

                    List<ShiftSwapRequest> out = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        ShiftSwapRequest r = d.toObject(ShiftSwapRequest.class);
                        if (r != null) {
                            r.setId(d.getId());
                            out.add(r);
                        }
                    }

                    Collections.sort(out, (a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
                    live.postValue(out);
                });

        return live;
    }

    public LiveData<List<ShiftSwapOffer>> listenOffers(String companyId, String teamId, String requestId) {
        MutableLiveData<List<ShiftSwapOffer>> live = new MutableLiveData<>(new ArrayList<>());

        reqDoc(companyId, teamId, requestId)
                .collection("offers")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) return;

                    List<ShiftSwapOffer> out = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        ShiftSwapOffer o = d.toObject(ShiftSwapOffer.class);
                        if (o != null) {
                            o.setId(d.getId());
                            out.add(o);
                        }
                    }
                    live.postValue(out);
                });

        return live;
    }

    // -----------------------
    // MUTATIONS (WITH DUPLICATE PREVENTION)
    // -----------------------

    /**
     * Prevent duplicate requests of the SAME KIND (type) for same shift (dateKey+templateId)
     * while existing request is OPEN or PENDING_APPROVAL.
     * Note: SWAP and GIVE_UP can both exist for the same shift (allowed).
     */
    public void createRequest(String companyId, String teamId, ShiftSwapRequest r, SimpleCallback cb) {
        if (r == null) { cb.onDone(false, "Missing request"); return; }
        if (r.getRequesterUid() == null || r.getRequesterUid().trim().isEmpty()) { cb.onDone(false, "Missing user"); return; }
        if (r.getDateKey() == null || r.getDateKey().trim().isEmpty()) { cb.onDone(false, "Missing date"); return; }
        if (r.getTemplateId() == null || r.getTemplateId().trim().isEmpty()) { cb.onDone(false, "Missing shift"); return; }
        if (r.getType() == null || r.getType().trim().isEmpty()) { cb.onDone(false, "Missing type"); return; }

        reqCol(companyId, teamId)
                .whereEqualTo("requesterUid", r.getRequesterUid())
                .whereEqualTo("dateKey", r.getDateKey())
                .whereEqualTo("templateId", r.getTemplateId())
                .whereEqualTo("type", r.getType())
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap != null) {
                        for (DocumentSnapshot d : snap.getDocuments()) {
                            String st = d.getString("status");
                            if (ShiftSwapRequest.OPEN.equals(st) || ShiftSwapRequest.PENDING_APPROVAL.equals(st)) {
                                cb.onDone(false, "You already submitted this request for this shift");
                                return;
                            }
                        }
                    }

                    DocumentReference doc = reqCol(companyId, teamId).document();

                    r.setId(doc.getId());
                    r.setCompanyId(companyId);
                    r.setTeamId(teamId);
                    r.setStatus(ShiftSwapRequest.OPEN);
                    r.setSelectedOfferId(null);
                    r.setCreatedAt(System.currentTimeMillis());

                    doc.set(r)
                            .addOnSuccessListener(unused -> cb.onDone(true, "Request created"))
                            .addOnFailureListener(e -> cb.onDone(false, "Failed: " + (e.getMessage() == null ? "" : e.getMessage())));
                })
                .addOnFailureListener(e -> cb.onDone(false, "Failed: " + (e.getMessage() == null ? "" : e.getMessage())));
    }

    public void cancelRequest(String companyId, String teamId, String requestId, SimpleCallback cb) {
        reqDoc(companyId, teamId, requestId)
                .update("status", ShiftSwapRequest.CANCELLED)
                .addOnSuccessListener(unused -> cb.onDone(true, "Cancelled"))
                .addOnFailureListener(e -> cb.onDone(false, "Failed: " + (e.getMessage() == null ? "" : e.getMessage())));
    }

    public void expireRequest(String companyId, String teamId, String requestId) {
        reqDoc(companyId, teamId, requestId).update("status", ShiftSwapRequest.EXPIRED);
    }

    /**
     * Prevent duplicate offers by same user on the same request.
     */
    public void makeOffer(String companyId, String teamId, String requestId, ShiftSwapOffer o, SimpleCallback cb) {
        if (o == null) { cb.onDone(false, "Missing offer"); return; }
        if (o.getOfferedByUid() == null || o.getOfferedByUid().trim().isEmpty()) { cb.onDone(false, "Missing user"); return; }

        DocumentReference req = reqDoc(companyId, teamId, requestId);

        req.collection("offers")
                .whereEqualTo("offeredByUid", o.getOfferedByUid())
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap != null && !snap.isEmpty()) {
                        cb.onDone(false, "You already submitted an offer on this request");
                        return;
                    }

                    DocumentReference doc = req.collection("offers").document();
                    o.setId(doc.getId());
                    o.setRequestId(requestId);
                    o.setCreatedAt(System.currentTimeMillis());

                    doc.set(o)
                            .addOnSuccessListener(unused -> cb.onDone(true, "Offer submitted"))
                            .addOnFailureListener(e -> cb.onDone(false, "Failed: " + (e.getMessage() == null ? "" : e.getMessage())));
                })
                .addOnFailureListener(e -> cb.onDone(false, "Failed: " + (e.getMessage() == null ? "" : e.getMessage())));
    }

    public void withdrawOffer(String companyId, String teamId, String requestId, String offerId, SimpleCallback cb) {
        reqDoc(companyId, teamId, requestId)
                .collection("offers").document(offerId)
                .delete()
                .addOnSuccessListener(unused -> cb.onDone(true, "Offer withdrawn"))
                .addOnFailureListener(e -> cb.onDone(false, "Failed: " + (e.getMessage() == null ? "" : e.getMessage())));
    }

    public void submitForApproval(String companyId, String teamId, String requestId, String offerId, SimpleCallback cb) {
        HashMap<String, Object> up = new HashMap<>();
        up.put("status", ShiftSwapRequest.PENDING_APPROVAL);
        up.put("selectedOfferId", offerId);

        reqDoc(companyId, teamId, requestId)
                .update(up)
                .addOnSuccessListener(unused -> cb.onDone(true, "Sent for manager approval"))
                .addOnFailureListener(e -> cb.onDone(false, "Failed: " + (e.getMessage() == null ? "" : e.getMessage())));
    }

    public void managerRejectPending(String companyId, String teamId, String requestId, SimpleCallback cb) {
        HashMap<String, Object> up = new HashMap<>();
        up.put("status", ShiftSwapRequest.OPEN);
        up.put("selectedOfferId", null);

        reqDoc(companyId, teamId, requestId)
                .update(up)
                .addOnSuccessListener(unused -> cb.onDone(true, "Rejected (back to open)"))
                .addOnFailureListener(e -> cb.onDone(false, "Failed: " + (e.getMessage() == null ? "" : e.getMessage())));
    }

    /**
     * APPROVAL DOES EVERYTHING:
     * - Validates request + selected offer
     * - Updates assignments in /assignments/{dateKey}/items/{uid}
     * - Marks this request APPROVED
     * - Cancels other OPEN/PENDING requests of requester for same shift (dateKey+templateId), regardless of type
     *
     * IMPORTANT: Android Transaction cannot read Query, so "cancel conflicts" runs AFTER transaction succeeds.
     */
    public void managerMarkApproved(String companyId, String teamId, String requestId, SimpleCallback cb) {
        DocumentReference requestRef = reqDoc(companyId, teamId, requestId);

        db.runTransaction((Transaction.Function<Map<String, String>>) transaction -> {

            DocumentSnapshot reqSnap = transaction.get(requestRef);
            if (reqSnap == null || !reqSnap.exists()) {
                throw new RuntimeException("Request not found");
            }

            String status = reqSnap.getString("status");
            if (ShiftSwapRequest.APPROVED.equals(status)) {
                Map<String, String> out = new HashMap<>();
                out.put("ok", "true");
                out.put("msg", "Already approved");
                out.put("requesterUid", reqSnap.getString("requesterUid"));
                out.put("dateKey", reqSnap.getString("dateKey"));
                out.put("templateId", reqSnap.getString("templateId"));
                return out;
            }
            if (!ShiftSwapRequest.PENDING_APPROVAL.equals(status)) {
                throw new RuntimeException("Request is not pending approval");
            }

            String requesterUid = reqSnap.getString("requesterUid");
            String dateKey = reqSnap.getString("dateKey");
            String templateId = reqSnap.getString("templateId");
            String selectedOfferId = reqSnap.getString("selectedOfferId");
            String type = reqSnap.getString("type");

            if (requesterUid == null || dateKey == null || templateId == null || selectedOfferId == null || type == null) {
                throw new RuntimeException("Missing request fields");
            }

            DocumentReference offerRef = requestRef.collection("offers").document(selectedOfferId);
            DocumentSnapshot offerSnap = transaction.get(offerRef);
            if (offerSnap == null || !offerSnap.exists()) {
                throw new RuntimeException("Selected offer not found");
            }

            String offererUid = offerSnap.getString("offeredByUid");
            if (offererUid == null || offererUid.trim().isEmpty()) {
                throw new RuntimeException("Offer missing user");
            }

            // ---------- APPLY ASSIGNMENT CHANGES ----------
            if (ShiftSwapRequest.GIVE_UP.equals(type)) {
                // requester gives up shift on dateKey/templateId, offerer takes it.
                DocumentReference reqAssignmentRef = assignmentItemDoc(companyId, teamId, dateKey, requesterUid);
                DocumentSnapshot reqAssignSnap = transaction.get(reqAssignmentRef);
                if (reqAssignSnap == null || !reqAssignSnap.exists()) {
                    throw new RuntimeException("Requester assignment not found (cannot give up)");
                }

                Map<String, Object> a = new HashMap<>();
                a.put("userId", offererUid);
                a.put("templateId", reqAssignSnap.getString("templateId"));
                a.put("templateTitle", reqAssignSnap.getString("templateTitle"));
                Long sh = reqAssignSnap.getLong("startHour");
                Long eh = reqAssignSnap.getLong("endHour");
                a.put("startHour", sh == null ? 0 : sh.intValue());
                a.put("endHour", eh == null ? 0 : eh.intValue());

                DocumentReference offererAssignmentRef = assignmentItemDoc(companyId, teamId, dateKey, offererUid);
                transaction.set(offererAssignmentRef, a);
                transaction.delete(reqAssignmentRef);

            } else if (ShiftSwapRequest.SWAP.equals(type)) {
                String offeredDateKey = offerSnap.getString("offeredDateKey");
                if (offeredDateKey == null || offeredDateKey.trim().isEmpty()) {
                    throw new RuntimeException("Offer missing offeredDateKey");
                }

                DocumentReference reqARef = assignmentItemDoc(companyId, teamId, dateKey, requesterUid);
                DocumentReference offARef = assignmentItemDoc(companyId, teamId, offeredDateKey, offererUid);

                DocumentSnapshot reqASnap = transaction.get(reqARef);
                DocumentSnapshot offASnap = transaction.get(offARef);

                if (reqASnap == null || !reqASnap.exists()) {
                    throw new RuntimeException("Requester assignment not found (cannot swap)");
                }
                if (offASnap == null || !offASnap.exists()) {
                    throw new RuntimeException("Offerer assignment not found (cannot swap)");
                }

                Map<String, Object> toOfferer = new HashMap<>();
                toOfferer.put("userId", offererUid);
                toOfferer.put("templateId", reqASnap.getString("templateId"));
                toOfferer.put("templateTitle", reqASnap.getString("templateTitle"));
                Long rsh = reqASnap.getLong("startHour");
                Long reh = reqASnap.getLong("endHour");
                toOfferer.put("startHour", rsh == null ? 0 : rsh.intValue());
                toOfferer.put("endHour", reh == null ? 0 : reh.intValue());

                Map<String, Object> toRequester = new HashMap<>();
                toRequester.put("userId", requesterUid);
                toRequester.put("templateId", offASnap.getString("templateId"));
                toRequester.put("templateTitle", offASnap.getString("templateTitle"));
                Long osh = offASnap.getLong("startHour");
                Long oeh = offASnap.getLong("endHour");
                toRequester.put("startHour", osh == null ? 0 : osh.intValue());
                toRequester.put("endHour", oeh == null ? 0 : oeh.intValue());

                DocumentReference offererOnReqDayRef = assignmentItemDoc(companyId, teamId, dateKey, offererUid);
                DocumentReference requesterOnOffDayRef = assignmentItemDoc(companyId, teamId, offeredDateKey, requesterUid);

                transaction.set(offererOnReqDayRef, toOfferer);
                transaction.set(requesterOnOffDayRef, toRequester);

                transaction.delete(reqARef);
                transaction.delete(offARef);

            } else {
                throw new RuntimeException("Unknown request type");
            }

            // ---------- MARK APPROVED ----------
            transaction.update(requestRef, "status", ShiftSwapRequest.APPROVED);

            Map<String, String> out = new HashMap<>();
            out.put("ok", "true");
            out.put("msg", "Approved");
            out.put("requesterUid", requesterUid);
            out.put("dateKey", dateKey);
            out.put("templateId", templateId);
            return out;

        }).addOnSuccessListener(result -> {
            if (result == null || !"true".equals(result.get("ok"))) {
                cb.onDone(false, "Failed");
                return;
            }

            // Now cancel conflicts OUTSIDE the transaction (Android Transaction can't read Query)
            String requesterUid = result.get("requesterUid");
            String dateKey = result.get("dateKey");
            String templateId = result.get("templateId");

            cancelConflictingRequests(companyId, teamId, requestId, requesterUid, dateKey, templateId, (ok2, msg2) -> {
                // We still consider the approval success even if conflict-cancel fails, but we show message.
                if (ok2) cb.onDone(true, "Approved");
                else cb.onDone(true, "Approved (warning: could not close other requests)");
            });

        }).addOnFailureListener(e -> cb.onDone(false, "Failed: " + (e.getMessage() == null ? "" : e.getMessage())));
    }

    private void cancelConflictingRequests(
            String companyId,
            String teamId,
            String approvedRequestId,
            String requesterUid,
            String dateKey,
            String templateId,
            SimpleCallback cb
    ) {
        if (requesterUid == null || dateKey == null || templateId == null) {
            cb.onDone(false, "Missing conflict fields");
            return;
        }

        reqCol(companyId, teamId)
                .whereEqualTo("requesterUid", requesterUid)
                .whereEqualTo("dateKey", dateKey)
                .whereEqualTo("templateId", templateId)
                .get()
                .addOnSuccessListener(snap -> {
                    WriteBatch batch = db.batch();

                    if (snap != null) {
                        for (DocumentSnapshot d : snap.getDocuments()) {
                            if (d == null) continue;
                            if (approvedRequestId.equals(d.getId())) continue;

                            String st = d.getString("status");
                            if (ShiftSwapRequest.OPEN.equals(st) || ShiftSwapRequest.PENDING_APPROVAL.equals(st)) {
                                batch.update(d.getReference(), "status", ShiftSwapRequest.CANCELLED);
                            }
                        }
                    }

                    batch.commit()
                            .addOnSuccessListener(unused -> cb.onDone(true, "OK"))
                            .addOnFailureListener(e -> cb.onDone(false, "Failed: " + (e.getMessage() == null ? "" : e.getMessage())));
                })
                .addOnFailureListener(e -> cb.onDone(false, "Failed: " + (e.getMessage() == null ? "" : e.getMessage())));
    }

    // ===========================
    // Availability check
    // ===========================
    public void hasMyShiftOnDate(
            @NonNull String companyId,
            @NonNull String teamId,
            @NonNull String dateKey,
            @NonNull String userUid,
            @NonNull SimpleCallback cb
    ) {
        db.collection("companies").document(companyId)
                .collection("teams").document(teamId)
                .collection("assignments").document(dateKey)
                .collection("items").document(userUid)
                .get()
                .addOnSuccessListener(doc -> {
                    boolean has = (doc != null && doc.exists());
                    cb.onDone(true, has ? "HAS_SHIFT" : "FREE");
                })
                .addOnFailureListener(e -> cb.onDone(false, "Failed: " + (e.getMessage() == null ? "" : e.getMessage())));
    }

    // ===========================
    // Upcoming shifts picker
    // ===========================
    public static class UpcomingShift {
        public String id;
        public String dateKey;
        public String templateId;
        public String templateTitle;

        public UpcomingShift() {}

        public UpcomingShift(String dateKey, String templateId, String templateTitle, String id) {
            this.dateKey = dateKey;
            this.templateId = templateId;
            this.templateTitle = templateTitle;
            this.id = id;
        }

        public String getDateKey() { return dateKey; }
    }

    public LiveData<List<UpcomingShift>> listenMyUpcomingShifts(
            @NonNull String companyId,
            @NonNull List<String> teamIds,
            @NonNull String userUid
    ) {
        MutableLiveData<List<UpcomingShift>> live = new MutableLiveData<>(new ArrayList<>());

        if (teamIds.isEmpty()) {
            live.postValue(new ArrayList<>());
            return live;
        }

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        String todayKey = String.format("%04d-%02d-%02d",
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));

        Map<String, UpcomingShift> bucket = new HashMap<>();
        AtomicInteger pendingTeams = new AtomicInteger(teamIds.size());

        for (String teamId : teamIds) {
            db.collection("companies").document(companyId)
                    .collection("teams").document(teamId)
                    .collection("assignments")
                    .orderBy(FieldPath.documentId())
                    .startAfter(todayKey)
                    .get()
                    .addOnSuccessListener(assignSnap -> {
                        List<DocumentSnapshot> assignDocs = assignSnap.getDocuments();
                        if (assignDocs.isEmpty()) {
                            if (pendingTeams.decrementAndGet() == 0) {
                                List<UpcomingShift> list = new ArrayList<>(bucket.values());
                                list.sort((a, b) -> a.dateKey.compareTo(b.dateKey));
                                live.postValue(list);
                            }
                            return;
                        }

                        AtomicInteger pendingDates = new AtomicInteger(assignDocs.size());

                        for (DocumentSnapshot assignDoc : assignDocs) {
                            String dateKey = assignDoc.getId();

                            assignDoc.getReference()
                                    .collection("items")
                                    .whereEqualTo("userId", userUid)
                                    .get()
                                    .addOnSuccessListener(itemsSnap -> {
                                        for (DocumentSnapshot itemDoc : itemsSnap.getDocuments()) {
                                            ShiftAssignment a = itemDoc.toObject(ShiftAssignment.class);
                                            if (a == null) continue;

                                            UpcomingShift us = new UpcomingShift(
                                                    dateKey,
                                                    a.getTemplateId(),
                                                    a.getTemplateTitle(),
                                                    itemDoc.getId()
                                            );

                                            String key = teamId + "|" + dateKey + "|" + a.getTemplateId();
                                            bucket.put(key, us);
                                        }

                                        if (pendingDates.decrementAndGet() == 0) {
                                            if (pendingTeams.decrementAndGet() == 0) {
                                                List<UpcomingShift> list = new ArrayList<>(bucket.values());
                                                list.sort((u1, u2) -> u1.dateKey.compareTo(u2.dateKey));
                                                live.postValue(list);
                                            }
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        if (pendingDates.decrementAndGet() == 0) {
                                            if (pendingTeams.decrementAndGet() == 0) {
                                                List<UpcomingShift> list = new ArrayList<>(bucket.values());
                                                list.sort((u1, u2) -> u1.dateKey.compareTo(u2.dateKey));
                                                live.postValue(list);
                                            }
                                        }
                                    });
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (pendingTeams.decrementAndGet() == 0) {
                            List<UpcomingShift> list = new ArrayList<>(bucket.values());
                            list.sort((a, b) -> a.dateKey.compareTo(b.dateKey));
                            live.postValue(list);
                        }
                    });
        }

        return live;
    }
}
