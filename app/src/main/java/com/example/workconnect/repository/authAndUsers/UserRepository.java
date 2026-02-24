package com.example.workconnect.repository.authAndUsers;

import android.util.Log;
import com.example.workconnect.models.User;
import com.example.workconnect.utils.UserUtils;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.function.Consumer;

/**
 * Repository for user-related Firestore operations
 * Centralizes user data loading to avoid duplication
 */
public class UserRepository {
    private static final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private static final String TAG = "UserRepository";
    
    /**
     * Load user name from Firestore
     * @param userId User ID
     * @param callback Callback with formatted name or null if not found/error
     */
    public static void loadUserName(String userId, Consumer<String> callback) {
        if (userId == null || userId.trim().isEmpty()) {
            callback.accept(null);
            return;
        }
        
        db.collection("users").document(userId).get()
            .addOnSuccessListener(document -> {
                if (document.exists()) {
                    User user = document.toObject(User.class);
                    if (user != null) {
                        String name = UserUtils.getDisplayName(user, null);
                        callback.accept(name.isEmpty() ? null : name);
                    } else {
                        callback.accept(null);
                    }
                } else {
                    callback.accept(null);
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Failed to load user name: " + userId, e);
                callback.accept(null);
            });
    }
    
    /**
     * Load user object from Firestore
     * @param userId User ID
     * @param callback Callback with User object or null if not found/error
     */
    public static void loadUser(String userId, Consumer<User> callback) {
        if (userId == null || userId.trim().isEmpty()) {
            callback.accept(null);
            return;
        }
        
        db.collection("users").document(userId).get()
            .addOnSuccessListener(document -> {
                if (document.exists()) {
                    callback.accept(document.toObject(User.class));
                } else {
                    callback.accept(null);
                }
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Failed to load user: " + userId, e);
                callback.accept(null);
            });
    }
}
