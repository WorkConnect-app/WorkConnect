package com.example.workconnect.repository.payslips;

import androidx.annotation.Nullable;

import com.example.workconnect.models.Payslip;
import com.google.firebase.firestore.*;

import java.util.*;

public class PayslipRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

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

    public ListenerRegistration listenPayslips(String employeeUid, PayslipListCallback callback) {
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
                                if (p.getPeriodKey() == null) p.setPeriodKey(d.getId());
                                out.add(p);
                            }
                        }
                    }

                    callback.onUpdate(out);
                });
    }

    public void uploadPayslipBase64(
            String employeeUid,
            String companyId,
            int year,
            int month,
            String pdfBase64,
            long fileSizeBytes,
            @Nullable String originalFileName,
            String uploadedByUid,
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
                    data.put("fileSizeBytes", fileSizeBytes);
                    data.put("pdfBase64", pdfBase64);

                    data.put("uploadedByUid", uploadedByUid);
                    data.put("uploadedAt", new Date());

                    tx.set(docRef, data, SetOptions.merge());
                    return Result.UPLOADED;
                }).addOnSuccessListener(callback::onComplete)
                .addOnFailureListener(callback::onError);
    }

    public void deletePayslip(Payslip payslip, PayslipActionCallback callback) {
        if (payslip == null || payslip.getEmployeeUid() == null || payslip.getPeriodKey() == null) {
            callback.onComplete(Result.ERROR);
            return;
        }

        db.collection("users")
                .document(payslip.getEmployeeUid())
                .collection("payslips")
                .document(payslip.getPeriodKey())
                .delete()
                .addOnSuccessListener(v -> callback.onComplete(Result.DELETED))
                .addOnFailureListener(callback::onError);
    }
}