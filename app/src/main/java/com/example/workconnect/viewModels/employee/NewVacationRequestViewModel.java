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

        // Validate selected dates
        if (endDate.before(startDate)) {
            toastMessage.setValue("End date cannot be before start date.");
            return;
        }

        // Calculate number of requested vacation days
        long diffMillis = endDate.getTime() - startDate.getTime();
        int daysRequested =
                (int) TimeUnit.DAYS.convert(diffMillis, TimeUnit.MILLISECONDS) + 1;

        // Check if user is logged in
        String uid = repository.getCurrentUserId();
        if (uid == null) {
            toastMessage.setValue("User not logged in.");
            return;
        }

        // Fetch user data from Firestore
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
            Long remainingDaysLong = doc.getLong("remainingVacationDays");
            String managerId = doc.getString("managerId");

            int remainingDays = remainingDaysLong != null
                    ? remainingDaysLong.intValue()
                    : 0;

            // Check if the employee has enough remaining vacation days
            if (daysRequested > remainingDays) {
                toastMessage.setValue("Not enough remaining vacation days.");
                return;
            }

            // Create a new vacation request
            String requestId = repository.generateVacationRequestId();

            VacationRequest request = new VacationRequest(
                    requestId,
                    uid,
                    managerId,
                    startDate,
                    endDate,
                    reason,
                    VacationStatus.PENDING,
                    daysRequested,
                    new Date()
            );

            // Save the request to Firestore
            repository.createVacationRequest(request)
                    .addOnCompleteListener(saveTask -> {
                        if (saveTask.isSuccessful()) {
                            toastMessage.setValue(
                                    "Vacation request sent and waiting for manager approval.");
                            closeScreen.setValue(true);
                        } else {
                            toastMessage.setValue("Failed to send request. Please try again.");
                        }
                    });
        });
    }
}
