package com.example.workconnect;

import android.app.Application;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;

public class WorkConnectApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Enable Firebase offline persistence
        // IMPORTANT: Must be called before any Firestore operations
        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build();
        FirebaseFirestore.getInstance().setFirestoreSettings(settings);
    }
}
