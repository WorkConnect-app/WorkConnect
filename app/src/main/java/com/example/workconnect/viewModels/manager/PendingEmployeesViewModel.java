package com.example.workconnect.viewModels.manager;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.workconnect.models.User;
import com.example.workconnect.models.enums.Roles;
import com.example.workconnect.repository.EmployeeRepository;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;

/**
 * ViewModel for the manager's screen that shows all pending employees
 * and allows approving or rejecting them.
 */
public class PendingEmployeesViewModel extends ViewModel {

    private final MutableLiveData<List<User>> pendingEmployees =
            new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    private final EmployeeRepository repository = new EmployeeRepository();
    private ListenerRegistration listenerRegistration;
    private boolean initialized = false;

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
     * Start listening only once (even if Activity/Fragment is recreated)
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
     * Approve employee with full details – role, manager, vacation accrual etc.
     */
    // PendingEmployeesViewModel.java
    public void approveEmployee(
            String uid,
            Roles role,                          // "EMPLOYEE" or "MANAGER"
            @Nullable String directManagerEmail,  // email (null/empty for top-level manager)
            Double vacationDaysPerMonth,
            String department,
            String team,
            String jobTitle
    ) {
        repository.approveEmployeeWithDetailsByManagerEmail(
                uid,
                role,
                directManagerEmail,
                vacationDaysPerMonth,
                department,
                team,
                jobTitle,
                (success, msg) -> {
                    if (!success) {
                        errorMessage.postValue(msg);
                    }
                }
        );
    }


    /**
     * Reject employee (status = "rejected")
     */
    public void rejectEmployee(String uid) {
        repository.updateEmployeeStatus(uid, "rejected", (success, msg) -> {
            if (!success) {
                errorMessage.postValue(msg);
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (listenerRegistration != null) {
            listenerRegistration.remove();
        }
    }
}
