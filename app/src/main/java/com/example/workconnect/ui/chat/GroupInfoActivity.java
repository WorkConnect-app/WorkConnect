package com.example.workconnect.ui.chat;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.workconnect.R;
import com.example.workconnect.adapters.GroupInfoMemberAdapter;
import com.example.workconnect.models.ChatMessage;
import com.example.workconnect.models.User;
import com.example.workconnect.utils.SystemMessageHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GroupInfoActivity extends AppCompatActivity {

    private String conversationId;
    private String currentUserId;

    private FirebaseFirestore db;
    private ListenerRegistration groupListener;

    private TextView tvTitle;
    private RecyclerView rvMembers;

    private Button btnBack, btnAdd, btnSub, btnQuit;

    private final List<User> members = new ArrayList<>();
    private GroupInfoMemberAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_info);

        db = FirebaseFirestore.getInstance();
        currentUserId = FirebaseAuth.getInstance().getUid();

        conversationId = getIntent().getStringExtra("conversationId");
        if (conversationId == null || currentUserId == null) {
            finish();
            return;
        }

        tvTitle = findViewById(R.id.tv_group_title);
        rvMembers = findViewById(R.id.rv_members);

        btnBack = findViewById(R.id.btn_back);
        btnAdd  = findViewById(R.id.btn_add_user);
        btnSub  = findViewById(R.id.btn_sub_user);
        btnQuit = findViewById(R.id.btn_quit_group);

        btnBack.setOnClickListener(v -> finish());

        adapter = new GroupInfoMemberAdapter(members, selectedUids -> {
            // for add num of selectioned
        });

        rvMembers.setLayoutManager(new LinearLayoutManager(this));
        rvMembers.setAdapter(adapter);

        btnAdd.setOnClickListener(v -> {
            Intent i = new Intent(GroupInfoActivity.this, AddMembersActivity.class);
            i.putExtra("conversationId", conversationId);
            startActivity(i);
        });


        btnSub.setOnClickListener(v -> {
            Set<String> selected = new HashSet<>(adapter.getSelectedUids());

            if (selected.isEmpty()) {
                Toast.makeText(this, "Select at least 1 member", Toast.LENGTH_SHORT).show();
                return;
            }

            // safety: can't remove self from here
            if (selected.contains(currentUserId)) {
                Toast.makeText(this, "Use Quit group to remove yourself", Toast.LENGTH_SHORT).show();
                return;
            }

            // remove all selected
            removeMembers(selected);

            // clear UI selection
            adapter.clearSelection();
        });

        btnQuit.setOnClickListener(v -> quitGroup());
    }

    @Override
    protected void onStart() {
        super.onStart();
        attachGroupListener();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (groupListener != null) {
            groupListener.remove();
            groupListener = null;
        }
    }

    // =========================
    // Live group info
    // =========================

    private void attachGroupListener() {
        if (groupListener != null) {
            groupListener.remove();
            groupListener = null;
        }

        groupListener = db.collection("conversations")
                .document(conversationId)
                .addSnapshotListener((doc, e) -> {
                    if (e != null || doc == null || !doc.exists()) return;

                    String title = doc.getString("title");
                    if (title == null || title.trim().isEmpty()) title = "Group info";
                    tvTitle.setText(title);

                    @SuppressWarnings("unchecked")
                    List<String> participantIds = (List<String>) doc.get("participantIds");
                    if (participantIds == null) participantIds = new ArrayList<>();

                    loadMembers(participantIds);
                });
    }

    private void loadMembers(List<String> participantIds) {
        members.clear();
        adapter.notifyDataSetChanged();

        if (participantIds.isEmpty()) return;

        for (String uid : participantIds) {
            db.collection("users").document(uid)
                    .get()
                    .addOnSuccessListener(userDoc -> {
                        User u = userDoc.toObject(User.class);
                        if (u == null) u = new User();
                        u.setUid(userDoc.getId());
                        members.add(u);
                        adapter.notifyDataSetChanged();
                    });
        }
    }

    // =========================
    // Remove members
    // =========================

    private void removeMembers(Set<String> uids) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("participantIds", FieldValue.arrayRemove(uids.toArray()));

        // delete unreadCounts.<uid> for each
        for (String uid : uids) {
            updates.put("unreadCounts." + uid, FieldValue.delete());
        }

        db.collection("conversations")
                .document(conversationId)
                .update(updates)
                .addOnSuccessListener(v -> {
                    // Create system messages for each removed member
                    for (String uid : uids) {
                        SystemMessageHelper.createSystemMessage(
                                conversationId,
                                ChatMessage.SystemMessageType.USER_REMOVED,
                                uid,
                                currentUserId, // Actor: who removed the member
                                null
                        );
                    }
                    Toast.makeText(this, "Member(s) removed", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(err ->
                        Toast.makeText(this, "Error: " + err.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    // If FieldValue.arrayRemove with array doesn't behave on your Firebase version,
    // replace removeMembers with a loop calling removeMember(uid) one by one.

    private void removeMember(String uid) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("participantIds", FieldValue.arrayRemove(uid));
        updates.put("unreadCounts." + uid, FieldValue.delete());

        db.collection("conversations")
                .document(conversationId)
                .update(updates);
    }

    // =========================
    // Quit group
    // =========================

    private void quitGroup() {
        Map<String, Object> updates = new HashMap<>();
        updates.put("participantIds", FieldValue.arrayRemove(currentUserId));
        updates.put("unreadCounts." + currentUserId, FieldValue.delete());

        db.collection("conversations")
                .document(conversationId)
                .update(updates)
                .addOnSuccessListener(v -> {
                    // Create system message for user leaving
                    SystemMessageHelper.createSystemMessage(
                            conversationId,
                            ChatMessage.SystemMessageType.USER_LEFT,
                            currentUserId,
                            null
                    );
                    Toast.makeText(this, "You left the group", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(err ->
                        Toast.makeText(this, "Error: " + err.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }
}
