package com.example.workconnect.viewModels.employee;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.workconnect.models.VacationRequest;
import com.example.workconnect.repository.VacationRepository;

import java.util.ArrayList;
import java.util.List;

public class EmployeeVacationRequestsViewModel extends ViewModel {

    private final VacationRepository repo = new VacationRepository();
    private final MutableLiveData<List<VacationRequest>> myRequests = new MutableLiveData<>(new ArrayList<>());

    public LiveData<List<VacationRequest>> getMyRequests() {
        return myRequests;
    }

    public void load() {
        String uid = repo.getCurrentUserId();
        if (uid == null) {
            myRequests.postValue(new ArrayList<>());
            return;
        }

        repo.getRequestsForEmployee(uid).observeForever(myRequests::postValue);
    }
}
