package com.example.workconnect.viewModels.home;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.workconnect.repository.vacations.VacationRepository;
import com.example.workconnect.utils.VacationAccrualCalculatorHelper;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Map;

public class HomeViewModel extends ViewModel {

    // -------- UI State for MVVM-consistent header --------
    public static class HeaderState {
        public final String fullName;
        public final String companyName;
        public final String companyShortId;

        public HeaderState(String fullName, String companyName, String companyShortId) {
            this.fullName = fullName;
            this.companyName = companyName;
            this.companyShortId = companyShortId;
        }
    }

    private final VacationRepository vacationRepository = new VacationRepository();
    private final VacationAccrualCalculatorHelper accrualCalculator = new VacationAccrualCalculatorHelper();

    private final ZoneId companyZone = ZoneId.of("Asia/Jerusalem");

    // Raw fields
    private final MutableLiveData<String> fullName = new MutableLiveData<>("-");
    private final MutableLiveData<String> companyName = new MutableLiveData<>("-");
    private final MutableLiveData<String> companyId = new MutableLiveData<>("-");
    private final MutableLiveData<String> todayStartTime = new MutableLiveData<>("--:--");

    private final MutableLiveData<String> startDate = new MutableLiveData<>("-");
    private final MutableLiveData<String> monthlyQuota = new MutableLiveData<>("-");
    private final MutableLiveData<String> vacationBalance = new MutableLiveData<>("0.00");

    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>("");

    // Single LiveData for header
    private final MediatorLiveData<HeaderState> headerState = new MediatorLiveData<>();

    // Firestore listener
    private ListenerRegistration userListener;

    // Guard flag to prevent infinite update loops when we update accrual fields in Firestore
    private boolean accrualUpdateInProgress = false;

    // Cache to avoid refetching company name on every user-doc change
    private String lastCompanyIdFetched = null;

    public HomeViewModel() {
        headerState.addSource(fullName, v -> emitHeader());
        headerState.addSource(companyName, v -> emitHeader());
        headerState.addSource(companyId, v -> emitHeader());
        emitHeader();
    }

    // ---- Exposed LiveData ----
    public LiveData<HeaderState> getHeaderState() { return headerState; }

    public LiveData<String> getStartDate() { return startDate; }
    public LiveData<String> getMonthlyQuota() { return monthlyQuota; }
    public LiveData<String> getVacationBalance() { return vacationBalance; }
    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<String> getError() { return error; }

    // Attendance (daily start time) from user.activeAttendance.startedAt
    public LiveData<String> getTodayStartTime() { return todayStartTime; }

    /**
     * Starts listening to the current user's profile document in Firestore.
     * UI updates automatically whenever the user's data changes.
     */
    public void loadProfile() {
        String uid = vacationRepository.getCurrentUserId();
        if (uid == null) {
            error.setValue("No logged-in user");
            return;
        }

        // Avoid registering multiple listeners if the Activity recreates itself
        if (userListener != null) return;

        loading.setValue(true);

        userListener = vacationRepository.listenToUser(uid, (doc, e) -> {
            if (e != null) {
                loading.setValue(false);
                error.setValue(e.getMessage() == null ? "Load error" : e.getMessage());
                return;
            }

            if (doc == null || !doc.exists()) {
                loading.setValue(false);
                error.setValue("User data not found");
                return;
            }

            handleUserDoc(doc);
        });
    }

    private void handleUserDoc(@NonNull DocumentSnapshot doc) {
        // ---- Profile ----
        fullName.setValue(nonEmptyOrDash(doc.getString("fullName")));

        // ---- Attendance: read from user.activeAttendance.startedAt ----
        updateTodayStartTimeFromUser(doc);

        // ---- Company ----
        String cId = safe(doc.getString("companyId"));
        companyId.setValue(nonEmptyOrDash(cId));

        if (cId.isEmpty()) {
            lastCompanyIdFetched = null;
            if (companyName.getValue() == null || "-".equals(companyName.getValue())) {
                companyName.setValue("-");
            }
        } else if (!cId.equals(lastCompanyIdFetched)) {
            lastCompanyIdFetched = cId;

            vacationRepository.getCompanyTask(cId)
                    .addOnSuccessListener(cDoc -> {
                        String cName = (cDoc != null && cDoc.exists()) ? safe(cDoc.getString("name")) : "";
                        companyName.setValue(nonEmptyOrDash(cName));
                    })
                    .addOnFailureListener(e -> {
                        if (companyName.getValue() == null || "-".equals(companyName.getValue())) {
                            companyName.setValue("-");
                        }
                    });
        }

        // ---- Vacation ----
        double monthlyDays = safeDouble(doc.getDouble("vacationDaysPerMonth"));
        double balance = safeDouble(doc.getDouble("vacationBalance"));

        monthlyQuota.setValue(monthlyDays > 0 ? String.format(Locale.US, "%.2f days/month", monthlyDays) : "-");

        LocalDate joinLocalDate = readJoinDate(doc);
        startDate.setValue(joinLocalDate == null ? "-" : joinLocalDate.toString());

        // If missing essential data, just show current balance
        if (joinLocalDate == null || monthlyDays <= 0.0) {
            loading.setValue(false);
            vacationBalance.setValue(format2(balance));
            return;
        }

        LocalDate today = LocalDate.now();

        String lastAccrualStr = safe(doc.getString("lastAccrualDate"));
        LocalDate lastAccrualDate = parseDateOrNull(lastAccrualStr);

        if (lastAccrualDate == null) {
            lastAccrualDate = joinLocalDate.minusDays(1);
        }

        if (!lastAccrualDate.isBefore(today)) {
            loading.setValue(false);
            vacationBalance.setValue(format2(balance));
            return;
        }

        if (accrualUpdateInProgress) {
            loading.setValue(false);
            vacationBalance.setValue(format2(balance));
            return;
        }

        double earned = accrualCalculator.calculateDailyVacationAccrual(
                monthlyDays,
                joinLocalDate,
                lastAccrualDate,
                today
        );

        double newBalance = balance + earned;

        accrualUpdateInProgress = true;

        vacationRepository.updateCurrentUserVacationAccrualDaily(newBalance, today.toString())
                .addOnSuccessListener(v -> {
                    accrualUpdateInProgress = false;
                    loading.setValue(false);
                    vacationBalance.setValue(format2(newBalance));
                })
                .addOnFailureListener(e -> {
                    accrualUpdateInProgress = false;
                    loading.setValue(false);
                    vacationBalance.setValue(format2(newBalance));
                });
    }

    private void updateTodayStartTimeFromUser(@NonNull DocumentSnapshot doc) {
        try {
            Map<String, Object> active = (Map<String, Object>) doc.get("activeAttendance");
            if (active == null) {
                todayStartTime.setValue("--:--");
                return;
            }

            Object startedAtObj = active.get("startedAt");
            if (!(startedAtObj instanceof Timestamp)) {
                todayStartTime.setValue("--:--");
                return;
            }

            Timestamp ts = (Timestamp) startedAtObj;

            ZonedDateTime zdt = ts.toDate().toInstant().atZone(companyZone);
            String hhmm = String.format(Locale.US, "%02d:%02d", zdt.getHour(), zdt.getMinute());
            todayStartTime.setValue(hhmm);
        } catch (Exception e) {
            todayStartTime.setValue("--:--");
        }
    }

    // ---------- Header emitter ----------
    private void emitHeader() {
        String n = nonEmptyOrDash(fullName.getValue());
        String c = nonEmptyOrDash(companyName.getValue());

        String rawId = safe(companyId.getValue());
        String shortId = "-";
        if (!rawId.isEmpty() && !"-".equals(rawId)) {
            shortId = rawId.length() >= 6 ? rawId.substring(0, 6).toUpperCase() : rawId.toUpperCase();
        }

        headerState.setValue(new HeaderState(n, c, shortId));
    }

    // ---------- Helpers ----------
    private LocalDate readJoinDate(DocumentSnapshot doc) {
        Timestamp ts = doc.getTimestamp("joinDate");
        if (ts == null) return null;

        return ts.toDate()
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
    }

    private LocalDate parseDateOrNull(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try {
            return LocalDate.parse(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String safe(String s) { return s == null ? "" : s.trim(); }

    private String nonEmptyOrDash(String s) {
        String v = safe(s);
        return v.isEmpty() ? "-" : v;
    }

    private double safeDouble(Double d) { return d == null ? 0.0 : d; }

    private String format2(double value) {
        double rounded = new BigDecimal(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
        return String.format(Locale.US, "%.2f", rounded);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (userListener != null) {
            userListener.remove();
            userListener = null;
        }
    }

    /**
     * One-time refresh (optional). Uses the same handleUserDoc logic.
     */
    public void refreshProfileOnce() {
        String uid = vacationRepository.getCurrentUserId();
        if (uid == null) {
            error.setValue("No logged-in user");
            return;
        }

        loading.setValue(true);

        vacationRepository.getUserTask(uid)
                .addOnSuccessListener(doc -> {
                    if (doc == null || !doc.exists()) {
                        loading.setValue(false);
                        error.setValue("User data not found");
                        return;
                    }
                    handleUserDoc(doc);
                })
                .addOnFailureListener(e -> {
                    loading.setValue(false);
                    error.setValue(e.getMessage() == null ? "Load error" : e.getMessage());
                });
    }
}