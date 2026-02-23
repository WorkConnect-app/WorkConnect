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

    private final MutableLiveData<List<Map<String, Object>>> periodsLiveData =
            new MutableLiveData<>();

    private final MutableLiveData<Boolean> isShiftActiveLiveData =
            new MutableLiveData<>(false);

    private final MutableLiveData<String> activeDateKeyLiveData =
            new MutableLiveData<>();

    private final MutableLiveData<AttendanceRepository.Result> actionResultLiveData =
            new MutableLiveData<>();

    // NEW (monthly)
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

    // NEW
    public LiveData<String> getMonthKey() { return monthKeyLiveData; }
    public LiveData<Double> getMonthlyHours() { return monthlyHoursLiveData; }

    // ---------------- INIT ----------------

    public void init(String companyId) {
        this.userId = FirebaseAuth.getInstance().getUid();
        this.companyId = companyId;

        String mk = ZonedDateTime.now(companyZone).format(MONTH_KEY_FORMAT);
        monthKeyLiveData.setValue(mk);
        refreshMonthlyHours(mk);

        listenToUserActiveAttendance();
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

                String dateKey = (String) active.get("dateKey");
                activeDateKeyLiveData.postValue(dateKey);
                isShiftActiveLiveData.postValue(true);

                listenToAttendanceDay(dateKey);
            } else {
                isShiftActiveLiveData.postValue(false);

                String todayKey = ZonedDateTime
                        .now(companyZone)
                        .format(DAY_KEY_FORMAT);

                activeDateKeyLiveData.postValue(todayKey);
                listenToAttendanceDay(todayKey);
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
    }
}
