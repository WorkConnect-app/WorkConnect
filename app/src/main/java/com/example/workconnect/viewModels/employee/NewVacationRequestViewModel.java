package com.example.workconnect.viewModels.employee;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.workconnect.models.VacationRequest;
import com.example.workconnect.models.enums.VacationStatus;
import com.example.workconnect.repository.VacationRepository;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Date;
import java.util.concurrent.TimeUnit;

public class NewVacationRequestViewModel extends ViewModel {

    private final VacationRepository repository;

    private final MutableLiveData<String> toastMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> closeScreen = new MutableLiveData<>(false);

    public NewVacationRequestViewModel() {
        repository = new VacationRepository();
    }

    public LiveData<String> getToastMessage() {
        return toastMessage;
    }

    public LiveData<Boolean> getCloseScreen() {
        return closeScreen;
    }

    public void onSendClicked(Date startDate, Date endDate, String reason) {

        if (startDate == null || endDate == null) {
            toastMessage.setValue("Please choose start and end dates.");
            return;
        }

        if (endDate.before(startDate)) {
            toastMessage.setValue("End date cannot be before start date.");
            return;
        }

        long diffMillis = endDate.getTime() - startDate.getTime();
        int daysRequested = (int) TimeUnit.DAYS.convert(diffMillis, TimeUnit.MILLISECONDS) + 1;

        String uid = repository.getCurrentUserId();
        if (uid == null) {
            toastMessage.setValue("User not logged in.");
            return;
        }

        Task<DocumentSnapshot> userTask = repository.getCurrentUserTask();
        if (userTask == null) {
            toastMessage.setValue("Error loading user data.");
            return;
        }

        userTask.addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null || !task.getResult().exists()) {
                toastMessage.setValue("Error loading user data. Please try again.");
                return;
            }

            DocumentSnapshot doc = task.getResult();

            String role = doc.getString("role");                 // "employee" / "manager"
            String directManagerId = doc.getString("directManagerId"); // can be null
            Double vacationBalanceD = doc.getDouble("vacationBalance");
            double vacationBalance = vacationBalanceD != null ? vacationBalanceD : 0.0;

            // --- NEW: Pull employee name + email so manager can see them ---
            String fullName = doc.getString("fullName");
            String email = doc.getString("email");

            if (fullName == null || fullName.trim().isEmpty()) {
                String fn = doc.getString("firstName");
                String ln = doc.getString("lastName");
                fullName = (fn != null ? fn : "") + " " + (ln != null ? ln : "");
                fullName = fullName.trim();
            }
            // -------------------------------------------------------------

            if (daysRequested > vacationBalance) {
                toastMessage.setValue("Not enough vacation balance.");
                return;
            }

            boolean isTopLevelManager =
                    role != null
                            && role.equalsIgnoreCase("manager")
                            && (directManagerId == null || directManagerId.trim().isEmpty());

            VacationStatus status = isTopLevelManager ? VacationStatus.APPROVED : VacationStatus.PENDING;

            String requestId = repository.generateVacationRequestId();

            VacationRequest request = new VacationRequest(
                    requestId,
                    uid,
                    fullName,
                    email,
                    directManagerId,  // approver managerId (null if top-level manager)
                    startDate,
                    endDate,
                    reason,
                    status,
                    daysRequested,
                    new Date()
            );

            // --- NEW: Store the name/email inside the vacation request document ---
            // Make sure VacationRequest model has these fields + setters/getters:
            // private String employeeName; private String employeeEmail;
            request.setEmployeeName(fullName);
            request.setEmployeeEmail(email);
            // ---------------------------------------------------------------------

            repository.createVacationRequest(request)
                    .addOnCompleteListener(saveTask -> {
                        if (saveTask.isSuccessful()) {
                            if (isTopLevelManager) {
                                toastMessage.setValue("Vacation approved automatically (top-level manager).");
                            } else {
                                toastMessage.setValue("Vacation request sent and waiting for manager approval.");
                            }
                            closeScreen.setValue(true);
                        } else {
                            toastMessage.setValue("Failed to send request. Please try again.");
                            if (saveTask.getException() != null) {
                                saveTask.getException().printStackTrace();
                            }
                        }
                    });
        });
    }
}
