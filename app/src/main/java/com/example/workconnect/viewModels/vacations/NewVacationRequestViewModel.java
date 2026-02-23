package com.example.workconnect.viewModels.vacations;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.workconnect.models.VacationRequest;
import com.example.workconnect.models.enums.VacationStatus;
import com.example.workconnect.repository.vacations.VacationRepository;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * ViewModel for creating a new vacation request.
 *
 * Mandatory responsibilities:
 * - Validate input (dates + reason) before sending
 * - Prevent duplicate submissions (loading state)
 * - Load current user data needed for the request (manager id, balance, name/email)
 * - Create and save the request via repository
 * - Expose UI events via LiveData (toast message + close screen)
 */
public class NewVacationRequestViewModel extends ViewModel {

    // Repository that abstracts Firebase/Auth/Firestore access
    private final VacationRepository repository;

    // UI one-way messages (shown as Toast in the Activity)
    private final MutableLiveData<String> toastMessage = new MutableLiveData<>();

    // UI signal to close screen after success
    private final MutableLiveData<Boolean> closeScreen = new MutableLiveData<>(false);

    // Mandatory: used to disable the Send button and prevent double-click submissions
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    public NewVacationRequestViewModel() {
        repository = new VacationRepository();
    }

    public LiveData<String> getToastMessage() {
        return toastMessage;
    }

    public LiveData<Boolean> getCloseScreen() {
        return closeScreen;
    }

    // Expose loading state to the Activity (disable Send button while true)
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    /**
     * Called when user clicks "Send".
     * Validates input and creates a new vacation request in Firestore.
     */
    public void onSendClicked(Date startDate, Date endDate, String reason) {

        // Prevent sending multiple requests if user double-clicks
        if (Boolean.TRUE.equals(isLoading.getValue())) {
            return;
        }

        // Start loading (Activity should disable Send button)
        isLoading.setValue(true);

        // Basic required fields validation
        if (startDate == null || endDate == null) {
            toastMessage.setValue("Please choose start and end dates.");
            isLoading.setValue(false);
            return;
        }

        if (reason == null || reason.trim().isEmpty()) {
            toastMessage.setValue("Please enter a reason.");
            isLoading.setValue(false);
            return;
        }

        // Logical date validation
        if (endDate.before(startDate)) {
            toastMessage.setValue("End date cannot be before start date.");
            isLoading.setValue(false);
            return;
        }

        // Calculate requested days (inclusive)
        long diffMillis = endDate.getTime() - startDate.getTime();
        int daysRequested = (int) TimeUnit.DAYS.convert(diffMillis, TimeUnit.MILLISECONDS) + 1;

        // Ensure user is logged in
        String uid = repository.getCurrentUserId();
        if (uid == null) {
            toastMessage.setValue("User not logged in.");
            isLoading.setValue(false);
            return;
        }

        // Load current user document (needed for manager id + balance + display info)
        Task<DocumentSnapshot> userTask = repository.getCurrentUserTask();
        if (userTask == null) {
            toastMessage.setValue("Error loading user data.");
            isLoading.setValue(false);
            return;
        }

        userTask.addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null || !task.getResult().exists()) {
                toastMessage.setValue("Error loading user data. Please try again.");
                isLoading.postValue(false);
                return;
            }

            DocumentSnapshot doc = task.getResult();

            // Read role + manager chain fields
            String role = doc.getString("role");                       // stored as String in Firestore
            String directManagerId = doc.getString("directManagerId"); // may be null for top-level manager

            // Read current vacation balance (default to 0 if missing)
            Double vacationBalanceD = doc.getDouble("vacationBalance");
            double vacationBalance = vacationBalanceD != null ? vacationBalanceD : 0.0;

            // Read employee name + email so the manager can see who requested
            String fullName = doc.getString("fullName");
            String email = doc.getString("email");

            // Fallback: build fullName from first/last name fields if needed
            if (fullName == null || fullName.trim().isEmpty()) {
                String fn = doc.getString("firstName");
                String ln = doc.getString("lastName");
                fullName = (fn != null ? fn : "") + " " + (ln != null ? ln : "");
                fullName = fullName.trim();
            }

            // Mandatory safety: don't allow requests that exceed balance
            if (daysRequested > vacationBalance) {
                toastMessage.setValue("Not enough vacation balance.");
                isLoading.postValue(false);
                return;
            }

            // Detect top-level manager (auto-approve if no direct manager)
            boolean isTopLevelManager =
                    role != null
                            && role.equalsIgnoreCase("manager")
                            && (directManagerId == null || directManagerId.trim().isEmpty());

            VacationStatus status = isTopLevelManager
                    ? VacationStatus.APPROVED
                    : VacationStatus.PENDING;

            // Create request object
            String requestId = repository.generateVacationRequestId();

            VacationRequest request = new VacationRequest(
                    requestId,
                    uid,
                    fullName,
                    email,
                    directManagerId, // approver managerId (null if top-level manager)
                    startDate,
                    endDate,
                    reason.trim(),
                    status,
                    daysRequested,
                    new Date()
            );

            // Store redundant info in the request doc for easier manager UI display
            request.setEmployeeName(fullName);
            request.setEmployeeEmail(email);

            // Save request
            repository.createVacationRequest(request)
                    .addOnCompleteListener(saveTask -> {
                        isLoading.postValue(false);

                        if (saveTask.isSuccessful()) {
                            toastMessage.postValue(
                                    isTopLevelManager
                                            ? "Vacation approved automatically (top-level manager)."
                                            : "Vacation request sent and waiting for manager approval."
                            );
                            closeScreen.postValue(true);
                        } else {
                            toastMessage.postValue("Failed to send request. Please try again.");
                            if (saveTask.getException() != null) {
                                saveTask.getException().printStackTrace();
                            }
                        }
                    });
        });
    }
}
