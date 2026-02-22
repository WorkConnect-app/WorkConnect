package com.example.workconnect.utils;

import com.example.workconnect.models.User;
import com.google.firebase.firestore.DocumentSnapshot;

/**
 * Utility class for user-related operations
 * Centralizes user name formatting logic to avoid duplication
 */
public class UserUtils {
    
    /**
     * Get display name for a user with fallback logic
     * Priority: fullName > firstName + lastName > fallback
     * 
     * @param user User object
     * @param fallback Fallback value if no name found (e.g., "Employee", uid, email)
     * @return Formatted name or fallback
     */
    public static String getDisplayName(User user, String fallback) {
        if (user == null) return fallback != null ? fallback : "";
        
        String name = user.getFullName();
        if (name != null && !name.trim().isEmpty()) {
            return name.trim();
        }
        
        String firstName = user.getFirstName() != null ? user.getFirstName() : "";
        String lastName = user.getLastName() != null ? user.getLastName() : "";
        name = (firstName + " " + lastName).trim();
        
        return name.isEmpty() ? (fallback != null ? fallback : "") : name;
    }
    
    /**
     * Get display name from DocumentSnapshot (for Firestore queries)
     * Priority: firstName + lastName > fullName > name > fallback
     * 
     * @param doc DocumentSnapshot from Firestore
     * @param fallback Fallback value if no name found
     * @return Formatted name or fallback
     */
    public static String getDisplayNameFromSnapshot(DocumentSnapshot doc, String fallback) {
        if (doc == null || !doc.exists()) return fallback != null ? fallback : "";
        
        String first = doc.getString("firstName");
        String last = doc.getString("lastName");
        String full = ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
        
        if (!full.isEmpty()) return full;
        
        String alt = doc.getString("fullName");
        if (alt != null && !alt.trim().isEmpty()) return alt.trim();
        
        alt = doc.getString("name");
        if (alt != null && !alt.trim().isEmpty()) return alt.trim();
        
        return fallback != null ? fallback : "";
    }
}
