package com.example.workconnect.repository.payslips;

import android.net.Uri;

import androidx.annotation.Nullable;

import com.example.workconnect.models.Payslip;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.*;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.*;

public class PayslipRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseStorage storage = FirebaseStorage.getInstance();

    public enum Result {
        UPLOADED,
        ALREADY_EXISTS,
        DELETED,
        ERROR
    }

    public interface PayslipListCallback {
        void onUpdate(List<Payslip> payslips);
        void onError(Exception e);
    }

    public interface PayslipActionCallback {
        void onComplete(Result result);
        void onError(Exception e);
    }

    // ----------------------------
    // Listen to payslips (DESC)
    // ----------------------------
    public ListenerRegistration listenPayslips(
            String employeeUid,
            PayslipListCallback callback
    ) {
        return db.collection("users")
                .document(employeeUid)
                .collection("payslips")
                .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        callback.onError(e);
                        return;
                    }
                    List<Payslip> out = new ArrayList<>();
                    if (snap != null) {
                        for (DocumentSnapshot d : snap.getDocuments()) {
                            Payslip p = d.toObject(Payslip.class);
                            if (p != null) {
                                // ensure docId is present even if not saved as field
                                if (p.getPeriodKey() == null) p.setPeriodKey(d.getId());
                                out.add(p);
                            }
                        }
                    }
                    callback.onUpdate(out);
                });
    }

    // ----------------------------
    // Upload payslip (unique yyyy-MM)
    // Transaction reserves docId so duplicates are impossible.
    // ----------------------------
    public void uploadPayslip(
            String employeeUid,
            String companyId,
            int year,
            int month,
            Uri pdfUri,
            String uploadedByUid,
            @Nullable String originalFileName,
            PayslipActionCallback callback
    ) {
        String periodKey = String.format(Locale.US, "%04d-%02d", year, month);

        DocumentReference docRef = db.collection("users")
                .document(employeeUid)
                .collection("payslips")
                .document(periodKey);

        String safeFileName = (originalFileName != null && !originalFileName.trim().isEmpty())
                ? originalFileName
                : (periodKey + ".pdf");

        String storagePath = "payslips/" + companyId + "/" + employeeUid + "/" + periodKey + ".pdf";
        StorageReference storageRef = storage.getReference().child(storagePath);

        // 1) Reserve doc id (atomic uniqueness)
        db.runTransaction(tx -> {
            DocumentSnapshot existing = tx.get(docRef);
            if (existing.exists()) return Result.ALREADY_EXISTS;

            Map<String, Object> data = new HashMap<>();
            data.put("periodKey", periodKey);
            data.put("employeeUid", employeeUid);
            data.put("companyId", companyId);
            data.put("year", year);
            data.put("month", month);
            data.put("fileName", safeFileName);
            data.put("storagePath", storagePath);
            data.put("downloadUrl", null);
            data.put("uploadedByUid", uploadedByUid);
            data.put("uploadedAt", new Date());

            tx.set(docRef, data, SetOptions.merge());
            return Result.UPLOADED; // reserved
        }).addOnSuccessListener(res -> {
            if (res == Result.ALREADY_EXISTS) {
                callback.onComplete(Result.ALREADY_EXISTS);
                return;
            }

            // 2) Upload to storage
            storageRef.putFile(pdfUri)
                    .continueWithTask(task -> {
                        if (!task.isSuccessful()) throw Objects.requireNonNull(task.getException());
                        return storageRef.getDownloadUrl();
                    })
                    .addOnSuccessListener(url -> {
                        // 3) Update doc with downloadUrl
                        Map<String, Object> upd = new HashMap<>();
                        upd.put("downloadUrl", url.toString());
                        upd.put("uploadedAt", new Date()); // refresh exact time on success

                        docRef.update(upd)
                                .addOnSuccessListener(v -> callback.onComplete(Result.UPLOADED))
                                .addOnFailureListener(e -> {
                                    // cleanup: remove doc if update fails
                                    docRef.delete();
                                    callback.onError(e);
                                });
                    })
                    .addOnFailureListener(e -> {
                        // cleanup: delete reserved doc if upload fails
                        docRef.delete();
                        callback.onError(e);
                    });

        }).addOnFailureListener(callback::onError);
    }

    // ----------------------------
    // Delete payslip (storage + firestore)
    // ----------------------------
    public void deletePayslip(Payslip payslip, PayslipActionCallback callback) {
        if (payslip == null || payslip.getEmployeeUid() == null || payslip.getPeriodKey() == null) {
            callback.onComplete(Result.ERROR);
            return;
        }

        DocumentReference docRef = db.collection("users")
                .document(payslip.getEmployeeUid())
                .collection("payslips")
                .document(payslip.getPeriodKey());

        String storagePath = payslip.getStoragePath();
        if (storagePath == null || storagePath.trim().isEmpty()) {
            // No storage path? At least delete Firestore record.
            docRef.delete()
                    .addOnSuccessListener(v -> callback.onComplete(Result.DELETED))
                    .addOnFailureListener(callback::onError);
            return;
        }

        StorageReference storageRef = storage.getReference().child(storagePath);

        // delete storage first, then doc
        storageRef.delete()
                .addOnSuccessListener(v -> docRef.delete()
                        .addOnSuccessListener(v2 -> callback.onComplete(Result.DELETED))
                        .addOnFailureListener(callback::onError))
                .addOnFailureListener(callback::onError);
    }
}