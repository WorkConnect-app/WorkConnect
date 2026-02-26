package com.example.workconnect.repository.attendance;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.*;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import com.example.workconnect.services.NotificationService;

public class AttendanceRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private static final DateTimeFormatter DAY_KEY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final long MAX_SHIFT_MS = 13L * 60L * 60L * 1000L; // 13 hours

    // ===============================
    // Result enum (clean UI handling)
    // ===============================
    public enum Result {
        STARTED,
        ENDED,
        ALREADY_STARTED,
        NOT_STARTED,
        ERROR
    }

    public interface AttendanceCallback {
        void onComplete(Result result);
        void onError(Exception e);
    }

    // ===============================
    // MONTHLY HOURS API
    // ===============================
    public interface MonthlyHoursCallback {
        void onSuccess(double hours);
        void onError(Exception e);
    }

    public void getMonthlyHours(
            String userId,
            String companyId,
            String monthKey, // "yyyy-MM"
            MonthlyHoursCallback cb
    ) {
        if (userId == null || userId.trim().isEmpty()
                || companyId == null || companyId.trim().isEmpty()
                || monthKey == null || monthKey.trim().isEmpty()) {
            cb.onSuccess(0.0);
            return;
        }

        // Robust strategy:
        // 1) Query all attendance docs for this user inside the company
        // 2) Filter client-side by month using:
        //    - dateKey startsWith "yyyy-MM"
        //    - OR docId startsWith "uid_yyyy-MM"
        db.collection("companies")
                .document(companyId)
                .collection("attendance")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener(qs -> {
                    double totalHours = 0.0;

                    String docIdPrefix = userId + "_" + monthKey; // e.g. uid_2026-02

                    for (DocumentSnapshot doc : qs.getDocuments()) {
                        String dateKey = doc.getString("dateKey");
                        String docId = doc.getId();

                        boolean inMonth =
                                (dateKey != null && dateKey.startsWith(monthKey))
                                        || (docId != null && docId.startsWith(docIdPrefix));

                        if (!inMonth) continue;

                        List<Map<String, Object>> periods =
                                (List<Map<String, Object>>) doc.get("periods");

                        if (periods == null) continue;

                        for (Map<String, Object> p : periods) {
                            Timestamp s = (Timestamp) p.get("startAt");
                            Timestamp e = (Timestamp) p.get("endAt");
                            if (s == null) continue;

                            long startMs = s.toDate().getTime();
                            long endMs = (e == null) ? System.currentTimeMillis() : e.toDate().getTime();

                            // Clamp any period to max 13 hours (prevents 24h+ inflation if worker forgets to end shift)
                            long capEndMs = startMs + MAX_SHIFT_MS;
                            if (endMs > capEndMs) endMs = capEndMs;

                            if (endMs > startMs) {
                                totalHours += (endMs - startMs) / 3600000.0;
                            }
                        }
                    }

                    cb.onSuccess(totalHours);
                })
                .addOnFailureListener(cb::onError);
    }


    // ===============================
    // START SHIFT
    // ===============================
    public void startShift(
            String userId,
            String companyId,
            ZoneId companyZone,
            Map<String, Object> startLocation, // nullable
            AttendanceCallback callback
    ) {
        Timestamp now = Timestamp.now();

        String dateKey = ZonedDateTime
                .now(companyZone)
                .format(DAY_KEY_FORMAT);

        String attendanceDocId = userId + "_" + dateKey;

        DocumentReference attendanceRef = db
                .collection("companies")
                .document(companyId)
                .collection("attendance")
                .document(attendanceDocId);

        DocumentReference userRef = db
                .collection("users")
                .document(userId);

        db.runTransaction(transaction -> {

                    DocumentSnapshot userSnap = transaction.get(userRef);

                    // Already active?
                    if (userSnap.contains("activeAttendance")) {
                        return Result.ALREADY_STARTED;
                    }

                    DocumentSnapshot attendanceSnap = transaction.get(attendanceRef);

                    List<Map<String, Object>> periods;

                    if (attendanceSnap.exists()) {
                        periods = (List<Map<String, Object>>) attendanceSnap.get("periods");
                        if (periods == null) periods = new ArrayList<>();

                        if (!periods.isEmpty()) {
                            Map<String, Object> last = periods.get(periods.size() - 1);
                            if (last.get("endAt") == null) {
                                return Result.ALREADY_STARTED;
                            }
                        }
                    } else {
                        periods = new ArrayList<>();
                    }

                    // New period
                    Map<String, Object> newPeriod = new HashMap<>();
                    newPeriod.put("startAt", now);
                    newPeriod.put("endAt", null);

                    if (startLocation != null) {
                        newPeriod.putAll(startLocation);
                    }

                    periods.add(newPeriod);

                    Map<String, Object> attendanceData = new HashMap<>();
                    attendanceData.put("userId", userId);
                    attendanceData.put("companyId", companyId);
                    attendanceData.put("dateKey", dateKey);
                    attendanceData.put("periods", periods);
                    attendanceData.put("updatedAt", now);

                    // TTL: delete after 370 days (buffer)
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(now.toDate());
                    cal.add(Calendar.DAY_OF_YEAR, 370);
                    Timestamp expiresAt = new Timestamp(cal.getTime());
                    attendanceData.put("expiresAt", expiresAt);

                    transaction.set(attendanceRef, attendanceData, SetOptions.merge());

                    Map<String, Object> activeAttendance = new HashMap<>();
                    activeAttendance.put("companyId", companyId);
                    activeAttendance.put("dateKey", dateKey);
                    activeAttendance.put("attendanceDocId", attendanceDocId);
                    activeAttendance.put("startedAt", now);

                    transaction.update(userRef, "activeAttendance", activeAttendance);

                    return Result.STARTED;

                }).addOnSuccessListener(callback::onComplete)
                .addOnFailureListener(callback::onError);
    }

    // ===============================
    // END SHIFT (cross-midnight safe)
    // ===============================
    public void endShift(
            String userId,
            Map<String, Object> endLocation, // nullable
            AttendanceCallback callback
    ) {
        Timestamp now = Timestamp.now();

        DocumentReference userRef = db
                .collection("users")
                .document(userId);

        db.runTransaction(transaction -> {

                    DocumentSnapshot userSnap = transaction.get(userRef);

                    if (!userSnap.contains("activeAttendance")) {
                        return Result.NOT_STARTED;
                    }

                    Map<String, Object> activeAttendance =
                            (Map<String, Object>) userSnap.get("activeAttendance");

                    String companyId = (String) activeAttendance.get("companyId");
                    String attendanceDocId = (String) activeAttendance.get("attendanceDocId");

                    DocumentReference attendanceRef = db
                            .collection("companies")
                            .document(companyId)
                            .collection("attendance")
                            .document(attendanceDocId);

                    DocumentSnapshot attendanceSnap = transaction.get(attendanceRef);

                    if (!attendanceSnap.exists()) {
                        return Result.NOT_STARTED;
                    }

                    List<Map<String, Object>> periods =
                            (List<Map<String, Object>>) attendanceSnap.get("periods");

                    if (periods == null || periods.isEmpty()) {
                        return Result.NOT_STARTED;
                    }

                    Map<String, Object> last = periods.get(periods.size() - 1);

                    if (last.get("endAt") != null) {
                        return Result.NOT_STARTED;
                    }

                    last.put("endAt", now);
                    if (endLocation != null) {
                        last.putAll(endLocation);
                    }

                    // TTL refresh (optional but nice)
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(now.toDate());
                    cal.add(Calendar.DAY_OF_YEAR, 370);
                    Timestamp expiresAt = new Timestamp(cal.getTime());

                    transaction.update(attendanceRef,
                            "periods", periods,
                            "updatedAt", now,
                            "expiresAt", expiresAt
                    );

                    transaction.update(userRef, "activeAttendance", FieldValue.delete());

                    return Result.ENDED;

                }).addOnSuccessListener(callback::onComplete)
                .addOnFailureListener(callback::onError);
    }

    // ===============================
    // END SHIFT AT (forced timestamp)
    // ===============================
    public void endShiftAt(
            String userId,
            Timestamp forcedEndAt,
            Map<String, Object> endLocation, // nullable
            AttendanceCallback callback
    ) {
        if (forcedEndAt == null) {
            callback.onComplete(Result.ERROR);
            return;
        }

        DocumentReference userRef = db
                .collection("users")
                .document(userId);

        db.runTransaction(transaction -> {

                    DocumentSnapshot userSnap = transaction.get(userRef);

                    if (!userSnap.contains("activeAttendance")) {
                        return Result.NOT_STARTED;
                    }

                    Map<String, Object> activeAttendance =
                            (Map<String, Object>) userSnap.get("activeAttendance");

                    String companyId = (String) activeAttendance.get("companyId");
                    String attendanceDocId = (String) activeAttendance.get("attendanceDocId");

                    DocumentReference attendanceRef = db
                            .collection("companies")
                            .document(companyId)
                            .collection("attendance")
                            .document(attendanceDocId);

                    DocumentSnapshot attendanceSnap = transaction.get(attendanceRef);

                    if (!attendanceSnap.exists()) {
                        return Result.NOT_STARTED;
                    }

                    List<Map<String, Object>> periods =
                            (List<Map<String, Object>>) attendanceSnap.get("periods");

                    if (periods == null || periods.isEmpty()) {
                        return Result.NOT_STARTED;
                    }

                    Map<String, Object> last = periods.get(periods.size() - 1);

                    Timestamp startTs = (Timestamp) last.get("startAt");
                    if (startTs == null) return Result.NOT_STARTED;

                    if (last.get("endAt") != null) {
                        return Result.NOT_STARTED;
                    }

                    long startMs = startTs.toDate().getTime();
                    long endMs = forcedEndAt.toDate().getTime();

                    // Do not allow ending before start; also clamp to 13h max
                    long minEndMs = startMs;
                    long maxEndMs = startMs + MAX_SHIFT_MS;

                    if (endMs < minEndMs) endMs = minEndMs;
                    if (endMs > maxEndMs) endMs = maxEndMs;

                    Timestamp safeEnd = new Timestamp(new Date(endMs));

                    last.put("endAt", safeEnd);
                    if (endLocation != null) {
                        last.putAll(endLocation);
                    }

                    // TTL refresh
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(safeEnd.toDate());
                    cal.add(Calendar.DAY_OF_YEAR, 370);
                    Timestamp expiresAt = new Timestamp(cal.getTime());

                    transaction.update(attendanceRef,
                            "periods", periods,
                            "updatedAt", safeEnd,
                            "expiresAt", expiresAt
                    );

                    NotificationService.addAttendanceAutoEnded(
                            transaction,
                            userId,
                            companyId,
                            attendanceDocId.substring(attendanceDocId.indexOf("_") + 1)
                    );

                    transaction.update(userRef, "activeAttendance", FieldValue.delete());

                    return Result.ENDED;

                }).addOnSuccessListener(callback::onComplete)
                .addOnFailureListener(callback::onError);
    }

}
