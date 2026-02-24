package com.example.workconnect.ui.notifications;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.workconnect.R;
import com.example.workconnect.adapters.notifications.NotificationsAdapter;
import com.example.workconnect.repository.notifications.NotificationsRepository;
import com.example.workconnect.ui.auth.PendingEmployeesActivity;
import com.example.workconnect.ui.vacations.PendingVacationRequestsActivity;
import com.example.workconnect.ui.vacations.VacationRequestsActivity;
import com.google.firebase.auth.FirebaseAuth;

/**
 * Displays the current user's in-app notifications.
 *
 * Behavior:
 * - Listens in real-time to the user's notifications subcollection.
 * - Navigates to the relevant screen based on notification type.
 * - Deletes a notification after successful navigation.
 */
public class NotificationsActivity extends AppCompatActivity {

    private static final String TAG = "NotificationsUI";

    private NotificationsRepository repo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        // Get currently authenticated user ID
        String uid = FirebaseAuth.getInstance().getUid();
        Log.d(TAG, "NotificationsActivity uid=" + uid);

        // If no authenticated user, close the screen
        if (uid == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        repo = new NotificationsRepository();

        // Setup RecyclerView
        RecyclerView rv = findViewById(R.id.rv_notifications);
        rv.setLayoutManager(new LinearLayoutManager(this));

        // Adapter handles click events per notification
        NotificationsAdapter adapter = new NotificationsAdapter(n -> {

            String type = n.getType();

            // =========================================
            // Employee pending approval notification
            // =========================================
            if ("EMPLOYEE_PENDING_APPROVAL".equals(type)) {

                // Extract companyId from notification data
                String companyId = null;
                if (n.getData() != null) {
                    Object v = n.getData().get("companyId");
                    if (v != null) companyId = String.valueOf(v);
                }

                if (companyId == null || companyId.trim().isEmpty()) {
                    Toast.makeText(this,
                            "Missing companyId in notification",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                // Navigate to pending employees screen
                Intent i = new Intent(this, PendingEmployeesActivity.class);
                i.putExtra("companyId", companyId);
                startActivity(i);

                // Delete notification after navigation
                if (n.getId() != null) {
                    repo.deleteNotification(uid, n.getId());
                }
                return;
            }

            // =========================================
            // Manager: new vacation request
            // =========================================
            if ("VACATION_NEW_REQUEST".equals(type)) {
                startActivity(new Intent(this, PendingVacationRequestsActivity.class));

                if (n.getId() != null) {
                    repo.deleteNotification(uid, n.getId());
                }
                return;
            }

            // =========================================
            // Employee: vacation decision result
            // =========================================
            if ("VACATION_APPROVED".equals(type)
                    || "VACATION_REJECTED".equals(type)) {

                startActivity(new Intent(this, VacationRequestsActivity.class));

                if (n.getId() != null) {
                    repo.deleteNotification(uid, n.getId());
                }
                return;
            }

            // Unknown notification type (safety log)
            Log.w(TAG, "Unknown notification type: " + type);
        });

        rv.setAdapter(adapter);

        // Back button simply closes the screen
        Button btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        // Observe notifications in real-time
        repo.listenNotifications(uid).observe(this, list -> {
            Log.d(TAG, "adapter submit size=" + (list == null ? -1 : list.size()));
            adapter.submit(list);
        });
    }
}