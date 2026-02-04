package com.example.workconnect.repository;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.*;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class AttendanceRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private static final DateTimeFormatter DAY_KEY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

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

                    transaction.update(attendanceRef,
                            "periods", periods,
                            "updatedAt", now
                    );

                    transaction.update(userRef, "activeAttendance", FieldValue.delete());

                    return Result.ENDED;

                }).addOnSuccessListener(callback::onComplete)
                .addOnFailureListener(callback::onError);
    }
}
