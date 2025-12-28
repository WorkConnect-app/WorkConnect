package com.example.workconnect.viewModels.employee;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.workconnect.repository.VacationRepository;
import com.example.workconnect.useCase.VacationAccrualCalculator;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;

public class MyProfileViewModel extends ViewModel {

    private final VacationRepository vacationRepository = new VacationRepository();
    private final VacationAccrualCalculator accrualCalculator = new VacationAccrualCalculator();

    private final MutableLiveData<String> fullName = new MutableLiveData<>("-");
    private final MutableLiveData<String> companyName = new MutableLiveData<>("-");
    private final MutableLiveData<String> startDate = new MutableLiveData<>("-");
    private final MutableLiveData<String> monthlyQuota = new MutableLiveData<>("-");
    private final MutableLiveData<String> vacationBalance = new MutableLiveData<>("0.00");

    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>("");

    public LiveData<String> getFullName() { return fullName; }
    public LiveData<String> getCompanyName() { return companyName; }
    public LiveData<String> getStartDate() { return startDate; }
    public LiveData<String> getMonthlyQuota() { return monthlyQuota; }
    public LiveData<String> getVacationBalance() { return vacationBalance; }
    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<String> getError() { return error; }

    public void loadProfile() {
        if (vacationRepository.getCurrentUserTask() == null) {
            error.setValue("No logged-in user");
            return;
        }

        loading.setValue(true);

        vacationRepository.getCurrentUserTask()
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

    private void handleUserDoc(DocumentSnapshot doc) {
        fullName.setValue(nonEmptyOrDash(doc.getString("fullName")));

        // Company name is stored in companies collection; user has companyId
        String companyId = safe(doc.getString("companyId"));
        if (!companyId.isEmpty()) {
            vacationRepository.getCompanyTask(companyId)
                    .addOnSuccessListener(cDoc -> {
                        String cName = (cDoc != null && cDoc.exists()) ? safe(cDoc.getString("name")) : "";
                        companyName.setValue(nonEmptyOrDash(cName));
                    })
                    .addOnFailureListener(e -> companyName.setValue("-"));
        } else {
            companyName.setValue("-");
        }

        double monthlyDays = safeDouble(doc.getDouble("vacationDaysPerMonth"));
        double balance = safeDouble(doc.getDouble("vacationBalance"));

        monthlyQuota.setValue(monthlyDays > 0 ? String.format("%.2f days/month", monthlyDays) : "-");

        // joinDate is stored as Timestamp/Date
        LocalDate joinLocalDate = readJoinDate(doc);
        startDate.setValue(joinLocalDate == null ? "-" : joinLocalDate.toString());

        // If missing essential data, just show current balance
        if (joinLocalDate == null || monthlyDays <= 0.0) {
            loading.setValue(false);
            vacationBalance.setValue(format2(balance));
            return;
        }

        LocalDate today = LocalDate.now();

        // lastAccrualDate is stored as yyyy-MM-dd
        String lastAccrualStr = safe(doc.getString("lastAccrualDate"));
        LocalDate lastAccrualDate = parseDateOrNull(lastAccrualStr);

        // If lastAccrualDate is missing, set it to the day before join date
        // so the first run accrues starting from join date.
        if (lastAccrualDate == null) {
            lastAccrualDate = joinLocalDate.minusDays(1);
        }

        // If already accrued up to today, do not accrue again
        if (!lastAccrualDate.isBefore(today)) {
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

        // Update DB with new balance and last accrued date (today)
        vacationRepository.updateCurrentUserVacationAccrualDaily(newBalance, today.toString())
                .addOnSuccessListener(v -> {
                    loading.setValue(false);
                    vacationBalance.setValue(format2(newBalance));
                })
                .addOnFailureListener(e -> {
                    // Even if update fails, show computed balance in UI
                    loading.setValue(false);
                    vacationBalance.setValue(format2(newBalance));
                });
    }

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
        return String.format("%.2f", rounded);
    }
}
