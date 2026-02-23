package com.example.workconnect.viewModels.vacations;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.workconnect.models.VacationRequest;
import com.example.workconnect.repository.vacations.VacationRepository;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel for employee vacation requests screen.
 * Provides the list of requests and real-time vacation balance.
 */
public class VacationRequestsViewModel extends ViewModel {

    private final VacationRepository repo = new VacationRepository();

    private LiveData<List<VacationRequest>> myRequests;

    private final MutableLiveData<Double> balance = new MutableLiveData<>(0.0);
    private final MutableLiveData<String> error = new MutableLiveData<>("");

    private ListenerRegistration userListener;

    public LiveData<List<VacationRequest>> getMyRequests() {
        return myRequests;
    }

    public LiveData<Double> getBalance() {
        return balance;
    }

    public LiveData<String> getError() {
        return error;
    }

    /**
     * Loads the employee requests LiveData and starts listening to the user document
     * to update the vacation balance in real-time.
     */
    public void load() {
        String uid = repo.getCurrentUserId();
        if (uid == null) {
            myRequests = new MutableLiveData<>(new ArrayList<>());
            error.postValue("No logged-in user");
            return;
        }

        // Requests list (LiveData backed by snapshot listener inside the repository)
        myRequests = repo.getRequestsForEmployee(uid);

        // Real-time balance listener
        if (userListener == null) {
            userListener = repo.listenToUser(uid, (DocumentSnapshot doc, com.google.firebase.firestore.FirebaseFirestoreException e) -> {
                if (e != null) {
                    error.postValue(e.getMessage());
                    return;
                }
                if (doc == null || !doc.exists()) return;

                Double bal = doc.getDouble("vacationBalance");
                balance.postValue(bal == null ? 0.0 : bal);
            });
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (userListener != null) {
            userListener.remove();
            userListener = null;
        }
    }
}
