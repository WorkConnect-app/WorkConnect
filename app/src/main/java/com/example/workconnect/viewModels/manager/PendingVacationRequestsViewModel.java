package com.example.workconnect.viewModels.manager;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.workconnect.models.VacationRequest;
import com.example.workconnect.repository.VacationRepository;

import java.util.List;
public class PendingVacationRequestsViewModel extends ViewModel {

    private final VacationRepository repo = new VacationRepository();

    private LiveData<List<VacationRequest>> pendingRequests;
    private final MutableLiveData<String> message = new MutableLiveData<>();

    public LiveData<List<VacationRequest>> getPendingRequests() {
        return pendingRequests;
    }

    public LiveData<String> getMessage() {
        return message;
    }

    public void load(String managerId) {
        pendingRequests = repo.getPendingRequestsForManager(managerId);
    }

    public void approve(String requestId) {
        repo.updateRequestStatus(requestId, "APPROVED")
                .addOnSuccessListener(v -> message.postValue("Approved"))
                .addOnFailureListener(e -> message.postValue("Failed: " + e.getMessage()));
    }

    public void reject(String requestId) {
        repo.updateRequestStatus(requestId, "REJECTED")
                .addOnSuccessListener(v -> message.postValue("Rejected"))
                .addOnFailureListener(e -> message.postValue("Failed: " + e.getMessage()));
    }

}
