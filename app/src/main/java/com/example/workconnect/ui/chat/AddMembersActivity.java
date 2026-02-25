package com.example.workconnect.ui.chat;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import com.example.workconnect.services.NotificationService;
import com.example.workconnect.utils.SystemMessageHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class AddMembersActivity extends AppCompatActivity {

    private FirebaseFirestore db;

    private String conversationId;
    private String currentUserId;
    private String companyId;

    private RecyclerView rvEmployees;
    private Button btnBack, btnAddSelected;
    private EditText etSearch;

    private final List<User> employees = new ArrayList<>();
    private final List<User> filteredEmployees = new ArrayList<>();
    private final List<String> selectedUids = new ArrayList<>();
    private GroupMemberAdapter adapter;

    // ids already in the group (to exclude from list)
    private final Set<String> existingParticipantIds = new HashSet<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_members);

        db = FirebaseFirestore.getInstance();
        currentUserId = FirebaseAuth.getInstance().getUid();

        conversationId = getIntent().getStringExtra("conversationId");
        if (conversationId == null || currentUserId == null) {
            finish();
            return;
        }

        rvEmployees = findViewById(R.id.rv_employees);
        btnBack = findViewById(R.id.btn_back);
        btnAddSelected = findViewById(R.id.btn_add_selected);
        etSearch = findViewById(R.id.et_search);

        btnBack.setOnClickListener(v -> finish());

        adapter = new GroupMemberAdapter(filteredEmployees, selectedUids);
        rvEmployees.setLayoutManager(new LinearLayoutManager(this));
        rvEmployees.setAdapter(adapter);

        btnAddSelected.setOnClickListener(v -> addSelectedMembers());

        // Setup search filter
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                filterEmployees(s.toString());
            }
        });

        loadConversationThenEmployees();
    }

    private void loadConversationThenEmployees() {
        db.collection("conversations").document(conversationId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        Toast.makeText(this, "Conversation not found", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    List<String> participantIds = (List<String>) doc.get("participantIds");
                    if (participantIds != null) existingParticipantIds.addAll(participantIds);

                    // load my companyId
                    loadCompanyIdAndEmployees();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    private void loadCompanyIdAndEmployees() {
        db.collection("users").document(currentUserId)
                .get()
                .addOnSuccessListener(doc -> {
                    companyId = doc.getString("companyId");
                    if (companyId == null) {
                        Toast.makeText(this, "No companyId", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
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
                        String uid = d.getId();

                        // exclude me + already in group
                        if (uid.equals(currentUserId)) continue;
                        if (existingParticipantIds.contains(uid)) continue;

                        User u = d.toObject(User.class);
                        if (u == null) continue;

                        u.setUid(uid);
                        employees.add(u);
                    }

                    // Apply current search filter
                    filterEmployees(etSearch.getText().toString());
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    private void filterEmployees(String query) {
        filteredEmployees.clear();
        
        if (query == null || query.trim().isEmpty()) {
            // No filter - show all
            filteredEmployees.addAll(employees);
        } else {
            // Filter by name, email
            String lowerQuery = query.toLowerCase(Locale.getDefault());
            
            for (User user : employees) {
                String fullName = user.getFullName();
                if (fullName == null || fullName.trim().isEmpty()) {
                    // Build full name from firstName and lastName
                    String firstName = user.getFirstName() != null ? user.getFirstName() : "";
                    String lastName = user.getLastName() != null ? user.getLastName() : "";
                    fullName = (firstName + " " + lastName).trim();
                }
                
                String firstName = user.getFirstName() != null ? user.getFirstName().toLowerCase(Locale.getDefault()) : "";
                String lastName = user.getLastName() != null ? user.getLastName().toLowerCase(Locale.getDefault()) : "";
                String email = user.getEmail() != null ? user.getEmail().toLowerCase(Locale.getDefault()) : "";
                
                if (fullName.toLowerCase(Locale.getDefault()).contains(lowerQuery) ||
                    firstName.contains(lowerQuery) ||
                    lastName.contains(lowerQuery) ||
                    email.contains(lowerQuery)) {
                    filteredEmployees.add(user);
                }
            }
        }
        
        adapter.notifyDataSetChanged();
    }

    private void addSelectedMembers() {
        if (selectedUids.isEmpty()) {
            Toast.makeText(this, "Select at least 1 user", Toast.LENGTH_SHORT).show();
            return;
        }

        // Build batch update
        WriteBatch batch = db.batch();

        // 1) arrayUnion all selected
        Map<String, Object> convUpdates = new HashMap<>();
        convUpdates.put("participantIds", FieldValue.arrayUnion(selectedUids.toArray()));

        // 2) init unreadCounts for each added user
        for (String uid : selectedUids) {
            convUpdates.put("unreadCounts." + uid, 0);
        }

        batch.update(db.collection("conversations").document(conversationId), convUpdates);

        batch.commit()
                .addOnSuccessListener(v -> {
                    // Create system messages for each added member
                    for (String uid : selectedUids) {
                        SystemMessageHelper.createSystemMessage(
                                conversationId,
                                ChatMessage.SystemMessageType.USER_ADDED,
                                uid,
                                currentUserId, // Actor: who added the member
                                null // Text will be generated in adapter
                        );
                    }

                    // Send ADDED_TO_GROUP notifications: fetch adder name + group title
                    List<String> addedUids = new ArrayList<>(selectedUids);
                    db.collection("users").document(currentUserId).get()
                            .addOnSuccessListener(adderDoc -> {
                                String firstName = adderDoc.getString("firstName");
                                String lastName  = adderDoc.getString("lastName");
                                String full      = adderDoc.getString("fullName");
                                String adderName;
                                if (firstName != null && !firstName.trim().isEmpty()) {
                                    adderName = (firstName.trim() + " " + (lastName != null ? lastName.trim() : "")).trim();
                                } else if (full != null && !full.trim().isEmpty()) {
                                    adderName = full.trim();
                                } else {
                                    adderName = "Someone";
                                }

                                db.collection("conversations").document(conversationId).get()
                                        .addOnSuccessListener(convDoc -> {
                                            String groupTitle = convDoc.getString("title");
                                            if (groupTitle == null || groupTitle.trim().isEmpty()) groupTitle = "Group";

                                            WriteBatch notifBatch = db.batch();
                                            for (String uid : addedUids) {
                                                if (uid == null) continue;
                                                NotificationService.addAddedToGroup(
                                                        notifBatch, uid, adderName, groupTitle, conversationId);
                                            }
                                            notifBatch.commit();
                                        });
                            });

                    Toast.makeText(this, "Member(s) added", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }
}
