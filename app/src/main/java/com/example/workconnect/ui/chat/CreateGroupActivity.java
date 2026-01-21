package com.example.workconnect.ui.chat;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.workconnect.R;
import com.example.workconnect.adapters.chats.GroupMemberAdapter;
import com.example.workconnect.models.ChatMessage;
import com.example.workconnect.models.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CreateGroupActivity extends AppCompatActivity {

    private EditText etGroupTitle;
    private RecyclerView rvEmployees;
    private Button btnCreate, btnBack;

    private final List<User> employees = new ArrayList<>();
    private final List<String> selectedUids = new ArrayList<>();
    private GroupMemberAdapter adapter;

    private FirebaseFirestore db;
    private String currentUserId;
    private String companyId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_group);

        db = FirebaseFirestore.getInstance();
        currentUserId = FirebaseAuth.getInstance().getUid();

        etGroupTitle = findViewById(R.id.et_group_title);
        rvEmployees = findViewById(R.id.rv_group_members);
        btnCreate = findViewById(R.id.btn_create_group);
        btnBack = findViewById(R.id.btn_back);

        btnBack.setOnClickListener(v -> finish());

        adapter = new GroupMemberAdapter(employees, selectedUids);
        rvEmployees.setLayoutManager(new LinearLayoutManager(this));
        rvEmployees.setAdapter(adapter);

        loadCompanyIdAndEmployees();

        btnCreate.setOnClickListener(v -> createGroup());
    }

    private void loadCompanyIdAndEmployees() {
        if (currentUserId == null) return;

        db.collection("users").document(currentUserId)
                .get()
                .addOnSuccessListener(doc -> {
                    companyId = doc.getString("companyId");
                    if (companyId == null) return;
                    loadEmployees();
                });
    }

    private void loadEmployees() {
        db.collection("users")
                .whereEqualTo("companyId", companyId)
                .whereEqualTo("status", com.example.workconnect.models.enums.RegisterStatus.APPROVED.name())
                .get()
                .addOnSuccessListener(snap -> {
                    employees.clear();
                    selectedUids.clear();

                    for (DocumentSnapshot d : snap.getDocuments()) {
                        if (d.getId().equals(currentUserId)) continue;

                        User u = d.toObject(User.class);
                        if (u == null) continue;

                        u.setUid(d.getId());
                        employees.add(u);
                    }

                    adapter.notifyDataSetChanged();
                });
    }

    private void createGroup() {
        String title = etGroupTitle.getText().toString().trim();
        if (title.isEmpty()) {
            Toast.makeText(this, "Please enter a group name", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedUids.isEmpty()) {
            Toast.makeText(this, "Select at least 1 member", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentUserId == null) return;

        // participants = me + selected
        List<String> participants = new ArrayList<>();
        participants.add(currentUserId);
        participants.addAll(selectedUids);

        // unreadCounts: creator = 0, others = 1 (so they see badge "1")
        Map<String, Object> unread = new HashMap<>();
        for (String uid : participants) {
            unread.put(uid, uid.equals(currentUserId) ? 0 : 1);
        }

        // Create conversation with auto-id
        Map<String, Object> convData = new HashMap<>();
        convData.put("participantIds", participants);
        convData.put("createdAt", FieldValue.serverTimestamp());

        convData.put("type", "group");
        convData.put("title", title);
        convData.put("createdBy", currentUserId);

        // We'll set these properly after we build system message
        convData.put("lastMessageText", "");
        convData.put("lastMessageAt", FieldValue.serverTimestamp());
        convData.put("lastMessageSenderId", currentUserId); // ok to set now

        convData.put("unreadCounts", unread);

        btnCreate.setEnabled(false);

        db.collection("conversations")
                .add(convData)
                .addOnSuccessListener(convRef -> {
                    // Now fetch creator name to build a nice system message
                    db.collection("users").document(currentUserId)
                            .get()
                            .addOnSuccessListener(userDoc -> {
                                String creatorName = buildUserDisplayName(userDoc);
                                if (creatorName == null || creatorName.trim().isEmpty()) creatorName = "Someone";

                                String systemText = creatorName + " created this group";

                                writeSystemMessageAndUpdateConversation(convRef, systemText, unread);
                            })
                            .addOnFailureListener(e -> {
                                // fallback if name fetch fails
                                String systemText = "Someone created this group";
                                writeSystemMessageAndUpdateConversation(convRef, systemText, unread);
                            });
                })
                .addOnFailureListener(e -> {
                    btnCreate.setEnabled(true);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void writeSystemMessageAndUpdateConversation(
            DocumentReference convRef,
            String systemText,
            Map<String, Object> unread
    ) {
        // Message doc in subcollection
        DocumentReference msgRef = convRef.collection("messages").document();

        Map<String, Object> msg = new HashMap<>();
        msg.put("conversationId", convRef.getId());
        msg.put("senderId", "system");
        msg.put("text", systemText);
        msg.put("sentAt", FieldValue.serverTimestamp());
        msg.put("isRead", false);
        msg.put("readAt", null);
        msg.put("status", "SENT");
        msg.put("messageType", "SYSTEM");
        msg.put("systemType", ChatMessage.SystemMessageType.GROUP_CREATED.name());
        msg.put("systemUserId", currentUserId);
        msg.put("readBy", new ArrayList<String>());

        // Update conversation last message + unreadCounts
        Map<String, Object> convUpdate = new HashMap<>();
        convUpdate.put("lastMessageText", systemText);
        convUpdate.put("lastMessageAt", FieldValue.serverTimestamp());
        convUpdate.put("lastMessageSenderId", currentUserId);
        convUpdate.put("unreadCounts", unread);

        WriteBatch batch = db.batch();
        batch.set(msgRef, msg);
        batch.update(convRef, convUpdate);

        batch.commit()
                .addOnSuccessListener(v -> {
                    Toast.makeText(this, "Group created", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnCreate.setEnabled(true);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private String buildUserDisplayName(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) return null;

        String first = doc.getString("firstName");
        String last = doc.getString("lastName");

        String full = ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
        if (!full.isEmpty()) return full;

        String alt = doc.getString("fullName");
        if (alt != null && !alt.trim().isEmpty()) return alt.trim();

        alt = doc.getString("name");
        if (alt != null && !alt.trim().isEmpty()) return alt.trim();

        return null;
    }
}
