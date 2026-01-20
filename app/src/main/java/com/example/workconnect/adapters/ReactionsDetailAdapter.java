package com.example.workconnect.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.workconnect.R;
import com.example.workconnect.ui.chat.ChatActivity;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ReactionsDetailAdapter extends RecyclerView.Adapter<ReactionsDetailAdapter.ReactionDetailViewHolder> {
    
    private List<ReactionDetailItem> reactionDetails;
    private ChatActivity activity;
    private ChatMessageAdapter messageAdapter; // To access name cache
    
    // Map to cache user names
    private Map<String, String> nameCache;
    
    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    
    public ReactionsDetailAdapter(List<ChatActivity.ReactionDetail> reactions, ChatActivity activity, ChatMessageAdapter messageAdapter) {
        this.activity = activity;
        this.messageAdapter = messageAdapter;
        this.nameCache = messageAdapter != null ? messageAdapter.getNameCache() : new java.util.HashMap<>();
        
        // Convert to display items
        this.reactionDetails = new ArrayList<>();
        for (ChatActivity.ReactionDetail detail : reactions) {
            this.reactionDetails.add(new ReactionDetailItem(detail.emoji, detail.userIds));
        }
    }
    
    @NonNull
    @Override
    public ReactionDetailViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_reaction_detail, parent, false);
        return new ReactionDetailViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ReactionDetailViewHolder holder, int position) {
        ReactionDetailItem item = reactionDetails.get(position);
        
        holder.emojiView.setText(item.emoji);
        holder.countView.setText(String.valueOf(item.userIds.size()));
        
        // Load and display user names
        loadUserNames(item.userIds, holder);
    }
    
    @Override
    public int getItemCount() {
        return reactionDetails.size();
    }
    
    private void loadUserNames(List<String> userIds, ReactionDetailViewHolder holder) {
        List<String> names = new ArrayList<>();
        List<String> toLoad = new ArrayList<>();
        
        // First, check cache
        for (String userId : userIds) {
            String cachedName = nameCache.get(userId);
            if (cachedName != null) {
                names.add(cachedName);
            } else {
                toLoad.add(userId);
            }
        }
        
        // Display what we have so far
        updateUserNamesDisplay(holder, names, toLoad.size(), userIds);
        
        // Load remaining names
        for (String userId : toLoad) {
            db.collection("users").document(userId)
                    .get()
                    .addOnSuccessListener(doc -> {
                        String first = doc.getString("firstName");
                        String last = doc.getString("lastName");
                        String full = ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
                        if (full.isEmpty()) full = doc.getString("fullName");
                        if (full == null || full.trim().isEmpty()) full = doc.getString("name");
                        if (full == null || full.trim().isEmpty()) full = userId;
                        
                        nameCache.put(userId, full);
                        
                        // Rebuild list and update display
                        List<String> allNames = new ArrayList<>();
                        for (String uid : userIds) {
                            String name = nameCache.get(uid);
                            if (name != null) {
                                allNames.add(name);
                            }
                        }
                        updateUserNamesDisplay(holder, allNames, userIds.size() - allNames.size(), userIds);
                    })
                    .addOnFailureListener(e -> {
                        // On error, just use user ID
                        nameCache.put(userId, userId);
                        List<String> allNames = new ArrayList<>();
                        for (String uid : userIds) {
                            String name = nameCache.get(uid);
                            if (name != null) {
                                allNames.add(name);
                            }
                        }
                        updateUserNamesDisplay(holder, allNames, userIds.size() - allNames.size(), userIds);
                    });
        }
    }
    
    private void updateUserNamesDisplay(ReactionDetailViewHolder holder, List<String> names, int remaining, List<String> userIds) {
        if (holder.getAdapterPosition() == -1) return; // ViewHolder is recycled
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(names.get(i));
        }
        if (remaining > 0) {
            if (names.size() > 0) sb.append(", ");
            sb.append("... (").append(remaining).append(" more)");
        }
        
        holder.usersView.setText(sb.toString());
        holder.usersView.setVisibility(sb.length() > 0 ? View.VISIBLE : View.GONE);
    }
    
    static class ReactionDetailViewHolder extends RecyclerView.ViewHolder {
        TextView emojiView;
        TextView countView;
        TextView usersView;
        
        ReactionDetailViewHolder(@NonNull View itemView) {
            super(itemView);
            emojiView = itemView.findViewById(R.id.textEmoji);
            countView = itemView.findViewById(R.id.textReactionCount);
            usersView = itemView.findViewById(R.id.textReactionUsers);
        }
    }
    
    private static class ReactionDetailItem {
        String emoji;
        List<String> userIds;
        
        ReactionDetailItem(String emoji, List<String> userIds) {
            this.emoji = emoji;
            this.userIds = userIds;
        }
    }
}