package com.example.workconnect.viewModels.manager;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.workconnect.models.VacationRequest;
import com.example.workconnect.repository.VacationRepository;

import java.util.List;

/**
 * ViewModel for manager screen that handles pending vacation requests.
 *
 * Responsibilities:
 * - Expose list of pending vacation requests for a specific manager
 * - Handle approve / reject actions
 * - Provide user feedback messages to the UI
 */
public class PendingVacationRequestsViewModel extends ViewModel {

    /**
     * Repository that communicates with Firestore.
     */
    private final VacationRepository repo = new VacationRepository();

    /**
     * LiveData holding all pending vacation requests for the manager.
     * Observed by the UI and updated automatically when Firestore changes.
     */
    private LiveData<List<VacationRequest>> pendingRequests;

    /**
     * One-time messages for UI feedback (success / failure).
     */
    private final MutableLiveData<String> message = new MutableLiveData<>();

    /**
     * Expose pending vacation requests as immutable LiveData.
     */
    public LiveData<List<VacationRequest>> getPendingRequests() {
        return pendingRequests;
    }

    /**
     * Expose messages to the UI.
     */
    public LiveData<String> getMessage() {
        return message;
    }

    /**
     * Load all pending vacation requests for the given manager.
     */
    public void load(String managerId) {
        // Delegates data fetching to the repository
        pendingRequests = repo.getPendingRequestsForManager(managerId);
    }

    /**
     * Approve a vacation request.
     */
    public void approve(String requestId) {
        repo.approveRequestAndDeductBalance(requestId)
                .addOnSuccessListener(v ->
                        message.postValue("Approved")
                )
                .addOnFailureListener(e ->
                        message.postValue("Failed: " + e.getMessage())
                );
    }

    /**
     * Reject a vacation request.
     */
    public void reject(String requestId) {
        repo.rejectRequest(requestId)
                .addOnSuccessListener(v ->
                        message.postValue("Rejected")
                )
                .addOnFailureListener(e ->
                        message.postValue("Failed: " + e.getMessage())
                );
    }
}
