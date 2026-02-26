package com.example.workconnect.viewModels.attendance;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.workconnect.repository.attendance.AttendanceRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class AttendanceViewModel extends ViewModel {

    private final AttendanceRepository attendanceRepository = new AttendanceRepository();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private static final DateTimeFormatter DAY_KEY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final DateTimeFormatter MONTH_KEY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM");

    private static final long MAX_SHIFT_MS = 13L * 60L * 60L * 1000L; // 13 hours

    private boolean autoEndInProgress = false;

    private final MutableLiveData<List<Map<String, Object>>> periodsLiveData =
            new MutableLiveData<>();
    // two lists
    private final MutableLiveData<List<Map<String, Object>>> todayPeriodsLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<Map<String, Object>>> selectedDayPeriodsLiveData = new MutableLiveData<>();

    // two listeners
    private ListenerRegistration todayListener;
    private ListenerRegistration selectedDayListener;
    private final MutableLiveData<Boolean> isShiftActiveLiveData =
            new MutableLiveData<>(false);

    private final MutableLiveData<String> activeDateKeyLiveData =
            new MutableLiveData<>();

    private final MutableLiveData<AttendanceRepository.Result> actionResultLiveData =
            new MutableLiveData<>();

    private final MutableLiveData<String> monthKeyLiveData = new MutableLiveData<>();
    private final MutableLiveData<Double> monthlyHoursLiveData = new MutableLiveData<>(0.0);

    private ListenerRegistration attendanceListener;
    private ListenerRegistration userListener;

    private String userId;
    private String companyId;
    private ZoneId companyZone = ZoneId.of("Asia/Jerusalem");

    // ---------------- PUBLIC API ----------------

    public LiveData<List<Map<String, Object>>> getPeriods() {
        return periodsLiveData;
    }

    public LiveData<Boolean> isShiftActive() {
        return isShiftActiveLiveData;
    }

    public LiveData<String> getActiveDateKey() {
        return activeDateKeyLiveData;
    }

    public LiveData<AttendanceRepository.Result> getActionResult() {
        return actionResultLiveData;
    }

    public LiveData<String> getMonthKey() { return monthKeyLiveData; }
    public LiveData<Double> getMonthlyHours() { return monthlyHoursLiveData; }

    public LiveData<List<Map<String, Object>>> getTodayPeriods() { return todayPeriodsLiveData; }
    public LiveData<List<Map<String, Object>>> getSelectedDayPeriods() { return selectedDayPeriodsLiveData; }

    // ---------------- INIT ----------------

    public void init(String companyId) {
        this.userId = FirebaseAuth.getInstance().getUid();
        this.companyId = companyId;

        String mk = ZonedDateTime.now(companyZone).format(MONTH_KEY_FORMAT);
        monthKeyLiveData.setValue(mk);
        refreshMonthlyHours(mk);

        listenToUserActiveAttendance();

        // today listener
        attachTodayListener();

        // default selected day = today
        String todayKey = ZonedDateTime.now(companyZone).format(DAY_KEY_FORMAT);
        selectDay(todayKey);
    }

    // ---------------- MONTHLY ----------------

    public void refreshMonthlyHours(String monthKey) {
        monthKeyLiveData.setValue(monthKey);

        if (userId == null || companyId == null || companyId.trim().isEmpty()) {
            monthlyHoursLiveData.postValue(0.0);
            return;
        }

        attendanceRepository.getMonthlyHours(userId, companyId, monthKey,
                new AttendanceRepository.MonthlyHoursCallback() {
                    @Override
                    public void onSuccess(double hours) {
                        monthlyHoursLiveData.postValue(hours);
                    }

                    @Override
                    public void onError(Exception e) {
                        monthlyHoursLiveData.postValue(0.0);
                    }
                });
    }

    // ---------------- LISTENERS ----------------

    private void listenToUserActiveAttendance() {
        DocumentReference userRef =
                db.collection("users").document(userId);

        userListener = userRef.addSnapshotListener((snapshot, e) -> {
            if (snapshot == null || !snapshot.exists()) return;

            if (snapshot.contains("activeAttendance")) {
                Map<String, Object> active =
                        (Map<String, Object>) snapshot.get("activeAttendance");

                // Auto-end safety: if shift ran 13h+, force end it
                if (!autoEndInProgress) {
                    Object startedObj = active.get("startedAt");
                    if (startedObj instanceof com.google.firebase.Timestamp) {
                        com.google.firebase.Timestamp startedAt = (com.google.firebase.Timestamp) startedObj;

                        long startMs = startedAt.toDate().getTime();
                        long nowMs = System.currentTimeMillis();

                        if (nowMs - startMs >= MAX_SHIFT_MS) {
                            autoEndInProgress = true;

                            com.google.firebase.Timestamp forcedEnd =
                                    new com.google.firebase.Timestamp(new java.util.Date(startMs + MAX_SHIFT_MS));

                            attendanceRepository.endShiftAt(
                                    userId,
                                    forcedEnd,
                                    null,
                                    new AttendanceRepository.AttendanceCallback() {
                                        @Override
                                        public void onComplete(AttendanceRepository.Result result) {
                                            autoEndInProgress = false;
                                            actionResultLiveData.postValue(result);

                                            String mk = monthKeyLiveData.getValue();
                                            if (mk != null) refreshMonthlyHours(mk);
                                            // user doc will update (activeAttendance deleted) and listener will re-run
                                        }

                                        @Override
                                        public void onError(Exception e) {
                                            autoEndInProgress = false;
                                            actionResultLiveData.postValue(AttendanceRepository.Result.ERROR);

                                            String mk = monthKeyLiveData.getValue();
                                            if (mk != null) refreshMonthlyHours(mk);
                                        }
                                    }
                            );

                            return; // wait for listener to refresh after auto-end
                        }
                    }
                }

                String dateKey = (String) active.get("dateKey");
                activeDateKeyLiveData.postValue(dateKey);
                isShiftActiveLiveData.postValue(true);

            } else {
                isShiftActiveLiveData.postValue(false);

                String todayKey = ZonedDateTime
                        .now(companyZone)
                        .format(DAY_KEY_FORMAT);

                activeDateKeyLiveData.postValue(todayKey);
            }
        });
    }

    private void listenToAttendanceDay(String dateKey) {
        if (attendanceListener != null) {
            attendanceListener.remove();
        }

        String docId = userId + "_" + dateKey;

        attendanceListener = db
                .collection("companies")
                .document(companyId)
                .collection("attendance")
                .document(docId)
                .addSnapshotListener((snapshot, e) -> {
                    if (snapshot == null || !snapshot.exists()) {
                        periodsLiveData.postValue(null);
                        return;
                    }

                    List<Map<String, Object>> periods =
                            (List<Map<String, Object>>) snapshot.get("periods");

                    periodsLiveData.postValue(periods);
                });
    }

    @SuppressWarnings("unchecked")
    private ListenerRegistration listenToAttendanceDayInto(
            String dateKey,
            MutableLiveData<List<Map<String, Object>>> target
    ) {
        String docId = userId + "_" + dateKey;

        return db.collection("companies")
                .document(companyId)
                .collection("attendance")
                .document(docId)
                .addSnapshotListener((snapshot, e) -> {
                    if (snapshot == null || !snapshot.exists()) {
                        target.postValue(null);
                        return;
                    }

                    List<Map<String, Object>> periods =
                            (List<Map<String, Object>>) snapshot.get("periods");

                    target.postValue(periods);
                });
    }

    @SuppressWarnings("unchecked")
    private void attachTodayListener() {
        if (todayListener != null) todayListener.remove();

        String todayKey = ZonedDateTime
                .now(companyZone)
                .format(DAY_KEY_FORMAT);

        String docId = userId + "_" + todayKey;

        todayListener = db.collection("companies")
                .document(companyId)
                .collection("attendance")
                .document(docId)
                .addSnapshotListener((snapshot, e) -> {
                    if (snapshot == null || !snapshot.exists()) {
                        todayPeriodsLiveData.postValue(null);
                        return;
                    }

                    List<Map<String, Object>> periods =
                            (List<Map<String, Object>>) snapshot.get("periods");

                    todayPeriodsLiveData.postValue(periods);
                });
    }

    @SuppressWarnings("unchecked")
    public void selectDay(String dateKey) {
        if (selectedDayListener != null) selectedDayListener.remove();

        String docId = userId + "_" + dateKey;

        selectedDayListener = db.collection("companies")
                .document(companyId)
                .collection("attendance")
                .document(docId)
                .addSnapshotListener((snapshot, e) -> {
                    if (snapshot == null || !snapshot.exists()) {
                        selectedDayPeriodsLiveData.postValue(null);
                        return;
                    }

                    List<Map<String, Object>> periods =
                            (List<Map<String, Object>>) snapshot.get("periods");

                    selectedDayPeriodsLiveData.postValue(periods);
                });
    }
    // ---------------- ACTIONS ----------------

    public void startShift(Map<String, Object> locationData) {
        attendanceRepository.startShift(
                userId,
                companyId,
                companyZone,
                locationData,
                new AttendanceRepository.AttendanceCallback() {
                    @Override
                    public void onComplete(AttendanceRepository.Result result) {
                        actionResultLiveData.postValue(result);

                        String mk = monthKeyLiveData.getValue();
                        if (mk != null) refreshMonthlyHours(mk);
                    }

                    @Override
                    public void onError(Exception e) {
                        actionResultLiveData.postValue(AttendanceRepository.Result.ERROR);

                        String mk = monthKeyLiveData.getValue();
                        if (mk != null) refreshMonthlyHours(mk);
                    }
                }
        );
    }

    public void endShift(Map<String, Object> locationData) {
        attendanceRepository.endShift(
                userId,
                locationData,
                new AttendanceRepository.AttendanceCallback() {
                    @Override
                    public void onComplete(AttendanceRepository.Result result) {
                        actionResultLiveData.postValue(result);

                        String mk = monthKeyLiveData.getValue();
                        if (mk != null) refreshMonthlyHours(mk);
                    }

                    @Override
                    public void onError(Exception e) {
                        actionResultLiveData.postValue(AttendanceRepository.Result.ERROR);

                        String mk = monthKeyLiveData.getValue();
                        if (mk != null) refreshMonthlyHours(mk);
                    }
                }
        );
    }

    // ---------------- CLEANUP ----------------

    @Override
    protected void onCleared() {
        if (attendanceListener != null) attendanceListener.remove();
        if (userListener != null) userListener.remove();

        if (todayListener != null) todayListener.remove();
        if (selectedDayListener != null) selectedDayListener.remove();
    }
}
