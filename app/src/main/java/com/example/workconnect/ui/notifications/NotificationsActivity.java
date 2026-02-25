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
import com.example.workconnect.ui.chat.ChatActivity;
import com.example.workconnect.ui.chat.ChatListActivity;
import com.example.workconnect.ui.vacations.PendingVacationRequestsActivity;
import com.example.workconnect.ui.vacations.VacationRequestsActivity;
import com.google.firebase.auth.FirebaseAuth;

public class NotificationsActivity extends AppCompatActivity {

    private static final String TAG = "NotificationsUI";
    private NotificationsRepository repo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        String uid = FirebaseAuth.getInstance().getUid();
        Log.d(TAG, "NotificationsActivity uid=" + uid);

        if (uid == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        repo = new NotificationsRepository();

        RecyclerView rv = findViewById(R.id.rv_notifications);
        rv.setLayoutManager(new LinearLayoutManager(this));

        NotificationsAdapter adapter = new NotificationsAdapter(n -> {

            // delete after click
            if (n.getId() != null) {
                repo.deleteNotification(uid, n.getId());
            } else {
                Log.e(TAG, "Clicked notification has null id");
            }

            // navigation by type
            String type = n.getType();

            if ("EMPLOYEE_PENDING_APPROVAL".equals(type)) {
                // ✅ עובד חדש מחכה לאישור
                startActivity(new Intent(this, com.example.workconnect.ui.auth.PendingEmployeesActivity.class));

            } else if ("VACATION_NEW_REQUEST".equals(type)) {
                // ✅ בקשת חופשה חדשה שממתינה לאישור
                startActivity(new Intent(this, PendingVacationRequestsActivity.class));

            } else if ("VACATION_APPROVED".equals(type)) {
                // ✅ חופשה אושרה
                startActivity(new Intent(this, VacationRequestsActivity.class));

            } else if ("VACATION_REJECTED".equals(type)) {
                // ❌ חופשה נדחתה
                startActivity(new Intent(this, VacationRequestsActivity.class));

            } else if ("CHAT_NEW_MESSAGE".equals(type)
                    || "CHAT_GROUP_MESSAGE".equals(type)
                    || "GROUP_CALL_STARTED".equals(type)
                    || "MISSED_CALL".equals(type)
                    || "ADDED_TO_GROUP".equals(type)) {
                // Open the relevant conversation directly
                String conversationId = n.getData() != null
                        ? (String) n.getData().get("conversationId") : null;
                if (conversationId != null) {
                    Intent i = new Intent(this, ChatActivity.class);
                    i.putExtra("conversationId", conversationId);
                    startActivity(i);
                } else {
                    startActivity(new Intent(this, ChatListActivity.class));
                }

            } else if ("REMOVED_FROM_GROUP".equals(type)) {
                // User is no longer a member — open chat list instead
                startActivity(new Intent(this, ChatListActivity.class));

            } else {
                Log.w(TAG, "Unknown notification type: " + type);
            }
        });

        rv.setAdapter(adapter);

        Button btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        repo.listenNotifications(uid).observe(this, list -> {
            Log.d(TAG, "adapter submit size=" + (list == null ? -1 : list.size()));
            adapter.submit(list);
        });
    }
}