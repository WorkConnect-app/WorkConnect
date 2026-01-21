package com.example.workconnect.viewModels.auth;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.workconnect.models.Team;
import com.example.workconnect.models.User;
import com.example.workconnect.models.enums.RegisterStatus;
import com.example.workconnect.models.enums.Roles;
import com.example.workconnect.repository.EmployeeRepository;
import com.example.workconnect.repository.TeamRepository;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;

/**
 * ViewModel for the manager's screen that shows all pending employees
 * and allows approving or rejecting them.
 */
public class PendingEmployeesViewModel extends ViewModel {

    private final MutableLiveData<List<User>> pendingEmployees = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    private final EmployeeRepository employeeRepository = new EmployeeRepository();
    private final TeamRepository teamRepository = new TeamRepository();

    private ListenerRegistration listenerRegistration;
    private boolean initialized = false;

    private LiveData<List<Team>> teamsLiveData;

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
     * Teams list for the approval dialog spinner.
     * NOTE: this expects TeamRepository.listenTeamsForCompany(companyId) to exist.
     */
    public LiveData<List<Team>> getTeamsForCompany(String companyId) {
        if (teamsLiveData == null) {
            teamsLiveData = teamRepository.getTeamsForCompany(companyId);
        }
        return teamsLiveData;
    }

    /**
     * Start listening only once (even if Activity is recreated)
     */
    public void startListening(String companyId) {
        if (initialized) return;
        initialized = true;

        isLoading.setValue(true);

        listenerRegistration = employeeRepository.listenForPendingEmployees(
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
     * team is REMOVED; we now pass optional selectedTeamId + employmentType.
     */
    public void approveEmployee(
            String uid,
            Roles role,
            @Nullable String directManagerEmail,
            Double vacationDaysPerMonth,
            String department,
            String jobTitle,
            @Nullable String selectedTeamId,
            @Nullable String employmentType
    ) {
        employeeRepository.approveEmployeeWithDetailsByManagerEmail(
                uid,
                role,
                directManagerEmail,
                vacationDaysPerMonth,
                department,
                jobTitle,
                selectedTeamId,
                employmentType,
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
        employeeRepository.updateEmployeeStatus(uid, RegisterStatus.REJECTED, (success, msg) -> {
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
