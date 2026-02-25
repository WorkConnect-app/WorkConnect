package com.example.workconnect.ui.chat;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.workconnect.R;
import com.example.workconnect.adapters.chats.ChatConversationAdapter;
import com.example.workconnect.adapters.chats.EmployeeSearchAdapter;
import com.example.workconnect.models.ChatConversation;
import com.example.workconnect.models.User;
import com.example.workconnect.ui.home.BaseDrawerActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatListActivity extends BaseDrawerActivity {

    private static final String TAG = "ChatListActivity";

    private String currentUserId;
    private String companyId;

    // UI
    private EditText etSearchEmployee;

    // Search
    private RecyclerView rvSearchResults;
    private final List<User> searchResults = new ArrayList<>();
    private EmployeeSearchAdapter searchAdapter;

    // Conversations
    private RecyclerView rvConversations;
    private final List<ChatConversation> conversations = new ArrayList<>();
    private ChatConversationAdapter conversationAdapter;

    // Listener lifecycle management
    private ListenerRegistration conversationsListener;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_list);


        if (mAuth.getCurrentUser() == null) {
            Log.e(TAG, "mAuth.getCurrentUser() == null -> finish()");
            finish();
            return;
        }
        currentUserId = mAuth.getCurrentUser().getUid();
        Log.d(TAG, "currentUserId=" + currentUserId);

        etSearchEmployee = findViewById(R.id.et_search_employee);
        rvSearchResults  = findViewById(R.id.rv_search_results);
        rvConversations  = findViewById(R.id.rv_conversations);

        searchAdapter = new EmployeeSearchAdapter(searchResults, user -> {
            if (user != null && user.getUid() != null) {
                createOrOpenDirectConversation(user.getUid());
            }
        });
        rvSearchResults.setLayoutManager(new LinearLayoutManager(this));
        rvSearchResults.setAdapter(searchAdapter);

        // Initialize conversations adapter
        conversationAdapter = new ChatConversationAdapter(
                conversations,
                currentUserId,
                conversation -> {
                    Intent intent = new Intent(this, ChatActivity.class);
                    intent.putExtra("conversationId", conversation.getId());
                    startActivity(intent);
                }
        );

        rvConversations.setLayoutManager(new LinearLayoutManager(this));
        rvConversations.setAdapter(conversationAdapter);

        // Load company id once (listener will start in onStart)
        loadCompanyId();

        etSearchEmployee.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                String q = s.toString().trim();
                if (q.length() < 2) {
                    searchResults.clear();
                    searchAdapter.notifyDataSetChanged();
                    return;
                }
                searchEmployees(q);
            }
        });


    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.chat_list_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_new_group) {
            Intent i = new Intent(ChatListActivity.this, CreateGroupActivity.class);
            startActivity(i);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Attach listener when screen is visible
    @Override
    protected void onStart() {
        super.onStart();

        Log.d(TAG, "onStart() companyId=" + companyId + " currentUserId=" + currentUserId);

        // Start only when we have companyId loaded
        if (companyId != null && currentUserId != null) {
            startConversationsListener();
        } else {
            Log.d(TAG, "onStart(): companyId not loaded yet -> waiting");
        }
    }

    // Detach listener when leaving screen
    @Override
    protected void onStop() {
        super.onStop();
        stopConversationsListener();
    }

    // Load data

    private void loadCompanyId() {
        db.collection("users").document(currentUserId)
                .get()
                .addOnSuccessListener(doc -> {
                    companyId = doc.getString("companyId");
                    Log.d(TAG, "loadCompanyId(): companyId=" + companyId);

                    // If activity already visible, start now.
                    // (onStart may already have happened)
                    if (!isFinishing() && companyId != null) {
                        startConversationsListener();
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "loadCompanyId() failed", e));
    }

    private void startConversationsListener() {
        stopConversationsListener();

        Log.d(TAG, "startConversationsListener() for uid=" + currentUserId);

        conversationsListener =
                db.collection("conversations")
                        .whereArrayContains("participantIds", currentUserId)
                        .orderBy("lastMessageAt", Query.Direction.DESCENDING)
                        .addSnapshotListener((snap, e) -> {

                            if (e != null) {
                                Log.e(TAG, "Conversations listener error", e);
                                return;
                            }
                            if (snap == null) {
                                Log.e(TAG, "Conversations listener: snap == null");
                                return;
                            }

                            Log.d(TAG, "Conversations count=" + snap.size());

                            conversations.clear();
                            for (DocumentSnapshot d : snap.getDocuments()) {
                                ChatConversation c = d.toObject(ChatConversation.class);
                                if (c != null) {
                                    c.setId(d.getId());
                                    conversations.add(c);
                                }
                            }
                            conversationAdapter.notifyDataSetChanged();
                        });
    }

    private void stopConversationsListener() {
        if (conversationsListener != null) {
            conversationsListener.remove();
            conversationsListener = null;
            Log.d(TAG, "stopConversationsListener()");
        }
    }

    private void searchEmployees(String query) {
        if (companyId == null) {
            Log.d(TAG, "searchEmployees(): companyId == null (skip)");
            return;
        }

        String qLower = query.toLowerCase();

        db.collection("users")
                .whereEqualTo("companyId", companyId)
                .whereEqualTo("status", com.example.workconnect.models.enums.RegisterStatus.APPROVED.name())
                .get()
                .addOnSuccessListener(snap -> {
                    searchResults.clear();

                    for (DocumentSnapshot d : snap.getDocuments()) {
                        if (d.getId().equals(currentUserId)) continue;

                        User u = d.toObject(User.class);
                        if (u == null) continue;

                        u.setUid(d.getId());

                        String first = u.getFirstName() != null ? u.getFirstName() : "";
                        String last  = u.getLastName() != null ? u.getLastName() : "";
                        String name  = (first + " " + last).trim().toLowerCase();
                        String email = u.getEmail() != null ? u.getEmail().toLowerCase() : "";

                        if (name.contains(qLower) || email.contains(qLower)) {
                            searchResults.add(u);
                        }
                    }
                    searchAdapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> Log.e(TAG, "searchEmployees() failed", e));
    }

    // Create or open direct conversation (fixed ID)

    private void createOrOpenDirectConversation(String otherUserId) {
        if (currentUserId == null || otherUserId == null) return;

        String conversationId = buildConversationId(currentUserId, otherUserId);

        Map<String, Object> data = new HashMap<>();
        data.put("participantIds", Arrays.asList(currentUserId, otherUserId));
        data.put("createdAt", FieldValue.serverTimestamp());

        data.put("type", "direct");
        data.put("title", null);
        data.put("createdBy", currentUserId);

        data.put("lastMessageText", "");
        data.put("lastMessageAt", FieldValue.serverTimestamp());
        data.put("lastMessageSenderId", "");

        Map<String, Object> unread = new HashMap<>();
        unread.put(currentUserId, 0);
        unread.put(otherUserId, 0);
        data.put("unreadCounts", unread);

        db.collection("conversations")
                .document(conversationId)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(v -> openChat(conversationId))
                .addOnFailureListener(e -> Log.e(TAG, "createOrOpenDirectConversation() failed", e));
    }

    private String buildConversationId(String uid1, String uid2) {
        return uid1.compareTo(uid2) < 0
                ? uid1 + "_" + uid2
                : uid2 + "_" + uid1;
    }

    private void openChat(String conversationId) {
        Intent i = new Intent(this, ChatActivity.class);
        i.putExtra("conversationId", conversationId);
        startActivity(i);
    }
}


