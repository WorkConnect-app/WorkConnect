package com.example.workconnect.viewModels.auth;

import android.text.TextUtils;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.workconnect.repository.authAndUsers.EmployeeRepository;
import com.google.firebase.auth.FirebaseAuth;

/**
 * ViewModel for completing manager profile after Google registration.
 * Handles validation + delegates save operation to EmployeeRepository.
 */
public class CompleteManagerProfileViewModel extends ViewModel {

    // UI state
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> done = new MutableLiveData<>(false);

    // Repository that updates Firestore user document
    private final EmployeeRepository repo = new EmployeeRepository();

    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<String> getError() { return error; }
    public LiveData<Boolean> getDone() { return done; }

    /**
     * Saves manager profile details (after company creation).
     * Validates input before updating Firestore.
     */
    public void save(String department, String jobTitle, String vacationDaysPerMonthStr) {

        // Get currently logged-in manager UID
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        if (uid == null) {
            error.setValue("No logged-in user");
            return;
        }

        // Basic validation: all fields required
        if (TextUtils.isEmpty(jobTitle) || TextUtils.isEmpty(department) || TextUtils.isEmpty(vacationDaysPerMonthStr)) {
            error.setValue("Please fill all fields");
            return;
        }

        double v;
        try {
            // Parse vacation days per month
            v = Double.parseDouble(vacationDaysPerMonthStr.trim());
        } catch (Exception e) {
            error.setValue("Invalid vacation days value");
            return;
        }

        // Vacation days cannot be negative
        if (v < 0) {
            error.setValue("Vacation days must be 0 or higher");
            return;
        }

        loading.setValue(true);

        // Call repository to update profile in Firestore
        repo.completeManagerProfile(
                uid,
                v,
                department.trim(),
                "",
                jobTitle.trim(),
                (success, message) -> {
                    loading.postValue(false);
                    if (success) {
                        done.postValue(true); // UI should navigate forward
                    } else {
                        error.postValue(message == null ? "Save failed" : message);
                    }
                }
        );
    }
}