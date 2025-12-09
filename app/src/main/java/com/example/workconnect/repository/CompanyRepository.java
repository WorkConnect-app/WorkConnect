package com.example.workconnect.repository;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class CompanyRepository {

    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // callback to the caller
    public interface RegisterCompanyCallback {
        void onSuccess(String companyId, String companyCode);
        void onError(String message);
    }

    public void registerCompanyAndManager(
            String companyName,
            String managerName,
            String email,
            String password,
            RegisterCompanyCallback callback
    ) {

        // create user in Firebase Authentication
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful() && mAuth.getCurrentUser() != null) {

                            final String uid = mAuth.getCurrentUser().getUid();

                            // create a new document in the "companies" collection
                            final DocumentReference companyRef =
                                    db.collection("companies").document();
                            final String companyId = companyRef.getId();

                            // the coe company is the first 6 characters of the ID
                            final String companyCode = companyId.substring(0, 6);

                            // the values of company
                            Map<String, Object> companyData = new HashMap<>();
                            companyData.put("id", companyId);
                            companyData.put("name", companyName);
                            companyData.put("managerId", uid);
                            companyData.put("createdAt", Timestamp.now());
                            companyData.put("code", companyCode);

                            // save the company data
                            companyRef.set(companyData)
                                    .addOnSuccessListener(unused -> {

                                        // values of the user (manager)
                                        Map<String, Object> userData = new HashMap<>();
                                        userData.put("uid", uid);
                                        userData.put("fullName", managerName);
                                        userData.put("email", email);
                                        userData.put("role", "manager");
                                        userData.put("companyId", companyId);

                                        // save the user in "users"
                                        db.collection("users").document(uid)
                                                .set(userData)
                                                .addOnSuccessListener(unused2 -> {
                                                    // everything is ok
                                                    if (callback != null) {
                                                        callback.onSuccess(companyId, companyCode);
                                                    }
                                                })
                                                .addOnFailureListener(e -> {
                                                    if (callback != null) {
                                                        callback.onError("Error saving user data: " + e.getMessage());
                                                    }
                                                });

                                    })
                                    .addOnFailureListener(e -> {
                                        if (callback != null) {
                                            callback.onError("Error creating company: " + e.getMessage());
                                        }
                                    });

                        } else {
                            String msg = "Registration failed";
                            if (task.getException() != null) {
                                msg += ": " + task.getException().getMessage();
                            }
                            if (callback != null) {
                                callback.onError(msg);
                            }
                        }
                    }
                });
    }
}
