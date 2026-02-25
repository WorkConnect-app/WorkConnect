package com.example.workconnect.repository.payslips;

import androidx.annotation.Nullable;

import com.example.workconnect.models.Payslip;
import com.google.firebase.firestore.*;
import com.example.workconnect.services.NotificationService;
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
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        callback.onError(e);
                        return;
                    }

                    List<Payslip> out = new ArrayList<>();
                    if (snap != null) {
                        for (DocumentSnapshot d : snap.getDocuments()) {
                            Payslip p = new Payslip();

                            // always set the doc id as key (your ordering is by doc id anyway)
                            p.setPeriodKey(d.getId());

                            p.setEmployeeUid(d.getString("employeeUid"));
                            p.setCompanyId(d.getString("companyId"));
                            p.setFileName(d.getString("fileName"));
                            p.setPdfBase64(d.getString("pdfBase64"));

                            Long y = d.getLong("year");
                            Long m = d.getLong("month");
                            if (y != null) p.setYear(y.intValue());
                            if (m != null) p.setMonth(m.intValue());

                            Long sz = d.getLong("fileSizeBytes");
                            if (sz != null) p.setFileSizeBytes(sz);

                            p.setUploadedByUid(d.getString("uploadedByUid"));
                            p.setUploadedAt(d.getDate("uploadedAt"));

                            out.add(p);
                        }
                    }

                    // SORT LOCALLY by periodKey DESC (yyyy-MM format sorts correctly as string)
                    Collections.sort(out, (a, b) ->
                            b.getPeriodKey().compareTo(a.getPeriodKey())
                    );
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
                    // Notify only when NEW payslip is created
                    NotificationService.addPayslipUploaded(
                            tx,
                            employeeUid,
                            companyId,
                            periodKey
                    );
                    return Result.UPLOADED;
                }).addOnSuccessListener(callback::onComplete)
                .addOnFailureListener(callback::onError);
    }

    public void deletePayslip(Payslip payslip, PayslipActionCallback callback) {
        if (payslip == null || payslip.getEmployeeUid() == null || payslip.getPeriodKey() == null) {
            callback.onComplete(Result.ERROR);
            return;
        }

        String employeeUid = payslip.getEmployeeUid();
        String periodKey = payslip.getPeriodKey();
        String companyId = payslip.getCompanyId();

        DocumentReference payslipRef = db.collection("users")
                .document(employeeUid)
                .collection("payslips")
                .document(periodKey);

        WriteBatch batch = db.batch();

        batch.delete(payslipRef);

        // Notify employee that payslip was deleted
        NotificationService.addPayslipDeleted(
                batch,
                employeeUid,
                companyId,
                periodKey
        );

        batch.commit()
                .addOnSuccessListener(v -> callback.onComplete(Result.DELETED))
                .addOnFailureListener(callback::onError);
    }
}