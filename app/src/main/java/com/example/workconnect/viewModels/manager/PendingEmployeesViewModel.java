package com.example.workconnect.viewModels.manager;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.workconnect.models.User;
import com.example.workconnect.models.enums.RegisterStatus;
import com.example.workconnect.models.enums.Roles;
import com.example.workconnect.models.enums.VacationStatus;
import com.example.workconnect.repository.EmployeeRepository;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;

/**
 * ViewModel for the manager screen that displays all pending employees
 * and allows approving or rejecting them.
 */
public class PendingEmployeesViewModel extends ViewModel {

    // List of employees waiting for approval
    private final MutableLiveData<List<User>> pendingEmployees = new MutableLiveData<>();

    // Error messages to be shown in the UI
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    // Indicates whether an operation (initial load / approve / reject) is in progress
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    // Repository that handles Firestore operations
    private final EmployeeRepository repository = new EmployeeRepository();

    // Firestore listener reference (for cleanup)
    private ListenerRegistration listenerRegistration;

    // Prevents starting the listener multiple times
    private boolean initialized = false;

    // Expose LiveData to UI (read-only)
    public LiveData<List<User>> getPendingEmployees() {
        return pendingEmployees;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    /**
     * Starts listening for pending employees of a given company.
     * Will run only once per ViewModel instance.
     */
    public void startListening(String companyId) {
        if (initialized) return;
        initialized = true;

        isLoading.setValue(true);

        listenerRegistration = repository.listenForPendingEmployees(
                companyId,
                new EmployeeRepository.PendingEmployeesCallback() {

                    @Override
                    public void onSuccess(List<User> employees) {
                        isLoading.postValue(false);
                        pendingEmployees.postValue(employees);
                    }

                    @Override
                    public void onError(String message) {
                        isLoading.postValue(false);
                        errorMessage.postValue(message);
                    }
                }
        );
    }

    /**
     * Approves an employee and sets full profile details.
     */
    public void approveEmployee(
            String uid,
            Roles role,                          // EMPLOYEE / MANAGER
            @Nullable String directManagerEmail, // null for top-level manager
            Double vacationDaysPerMonth,
            String department,
            String team,
            String jobTitle
    ) {
        isLoading.setValue(true);

        repository.approveEmployeeWithDetailsByManagerEmail(
                uid,
                role,
                directManagerEmail,
                vacationDaysPerMonth,
                department,
                team,
                jobTitle,
                (success, msg) -> { // The success = true is only sent from the last function: updateEmployeeDocument(...)
                    isLoading.postValue(false);
                    if (!success) {
                        errorMessage.postValue(msg);
                    }
                }
        );
    }

    /**
     * Rejects an employee (status = REJECTED).
     */
    public void rejectEmployee(String uid) {
        isLoading.setValue(true);

        repository.updateEmployeeStatus(
                uid,
                RegisterStatus.REJECTED,
                (success, msg) -> {
                    isLoading.postValue(false);
                    if (!success) {
                        errorMessage.postValue(msg);
                    }
                }
        );
    }

    @Override
    protected void onCleared() {
        super.onCleared();

        // Stop Firestore listener when ViewModel is destroyed
        if (listenerRegistration != null) {
            listenerRegistration.remove();
        }
    }
}
