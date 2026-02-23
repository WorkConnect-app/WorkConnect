package com.example.workconnect.adapters.chats;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.workconnect.R;
import com.example.workconnect.models.ChatConversation;
import com.example.workconnect.repository.UserRepository;
import com.example.workconnect.utils.UserUtils;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.DateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatConversationAdapter extends RecyclerView.Adapter<ChatConversationAdapter.ConversationViewHolder> {

    public interface OnConversationClickListener {
        void onConversationClick(ChatConversation conversation);
    }

    private final List<ChatConversation> conversations;
    private final OnConversationClickListener listener;

    private final String currentUserId;

    private final Map<String, String> nameCache = new HashMap<>();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public ChatConversationAdapter(List<ChatConversation> conversations,
                                   String currentUserId,
                                   OnConversationClickListener listener) {
        this.conversations = conversations;
        this.currentUserId = currentUserId;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ConversationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_conversation, parent, false);
        return new ConversationViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ConversationViewHolder holder, int position) {
        ChatConversation conv = conversations.get(position);

        // -----------------------------
        // TITLE: group title vs other user
        // -----------------------------
        holder.tvTitle.setText("Loading...");

        String type = conv.getType();
        boolean isGroup = "group".equals(type);

        if (isGroup) {
            String title = conv.getTitle();
            if (title == null || title.trim().isEmpty()) title = "Group";

            holder.tvTitle.setText(title);
            holder.tvTitle.setTextColor(
                    holder.itemView.getContext().getColor(R.color.navyBlue)
            );
        } else {
            holder.tvTitle.setTextColor(
                    holder.itemView.getContext().getColor(R.color.navyBlue)
            );

            String otherUserId = getOtherParticipantId(conv);
            if (otherUserId != null) {
                String cached = nameCache.get(otherUserId);
                if (cached != null) {
                    holder.tvTitle.setText(cached);
                } else {
                    loadUserName(otherUserId, holder, false);
                }
            } else {
                holder.tvTitle.setText("Conversation");
            }
        }

        // -----------------------------
        // LAST MESSAGE
        // Group: if empty => show members preview
        // Direct: just last text (no prefix)
        // -----------------------------
        String lastText = conv.getLastMessageText() != null ? conv.getLastMessageText() : "";
        String senderId = conv.getLastMessageSenderId();

        if (!isGroup) {
            // Direct message: no prefix
            holder.tvLastMessage.setText(lastText);
        } else {
            // Group message
            if (lastText.trim().isEmpty()) {
                // No message yet: show participants names
                showGroupMembersPreview(conv, holder);
            } else {
                // Normal group last message: prefix with sender name
                if (senderId == null || senderId.trim().isEmpty()) {
                    holder.tvLastMessage.setText(lastText);
                } else if (senderId.equals(currentUserId)) {
                    holder.tvLastMessage.setText("You: " + lastText);
                } else {
                    String cachedSender = nameCache.get(senderId);
                    if (cachedSender != null) {
                        holder.tvLastMessage.setText(cachedSender + ": " + lastText);
                    } else {
                        holder.tvLastMessage.setText("...: " + lastText);
                        loadUserName(senderId, holder, true);
                    }
                }
            }
        }


        // -----------------------------
        // DATE
        // -----------------------------
        if (conv.getLastMessageAt() != null) {
            holder.tvLastMessageTime.setText(
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(conv.getLastMessageAt())
            );
        } else {
            holder.tvLastMessageTime.setText("");
        }

        // -----------------------------
        // UNREAD BADGE
        // -----------------------------
        long unread = 0;
        try {
            unread = conv.getUnreadCountFor(currentUserId);
        } catch (Exception ignored) {}

        if (unread > 0) {
            holder.tvUnreadBadge.setVisibility(View.VISIBLE);
            holder.tvUnreadBadge.setText(String.valueOf(unread));
        } else {
            holder.tvUnreadBadge.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onConversationClick(conv);
        });
    }

    private String getOtherParticipantId(ChatConversation conv) {
        if (conv == null || conv.getParticipantIds() == null) return null;
        for (String id : conv.getParticipantIds()) {
            if (id != null && !id.equals(currentUserId)) return id;
        }
        return null;
    }
    private void showGroupMembersPreview(ChatConversation conv, ConversationViewHolder holder) {
        if (conv == null || conv.getParticipantIds() == null) {
            holder.tvLastMessage.setText("");
            return;
        }

        // Display participant names (TextView will show "..." if too long)
        List<String> ids = conv.getParticipantIds();

        StringBuilder sb = new StringBuilder();
        int added = 0;
        int maxNames = 4; // Maximum number of names to display

        // Priority: show others first, current user at the end (optional)
        for (String uid : ids) {
            if (uid == null) continue;
            if (uid.equals(currentUserId)) continue;

            if (added >= maxNames) break;

            String name = nameCache.get(uid);
            if (name != null) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(name);
                added++;
            } else {
                // Placeholder until names are loaded
                if (sb.length() > 0) sb.append(", ");
                sb.append("...");
                added++;
            }
        }

        holder.tvLastMessage.setText(sb.toString());

        // Load missing names and refresh display
        preloadNamesForGroup(ids, holder);
    }

    private void preloadNamesForGroup(List<String> ids, ConversationViewHolder holder) {
        int count = 0;
        for (String uid : ids) {
            if (uid == null) continue;
            if (nameCache.containsKey(uid)) continue;

            // Avoid spamming Firestore
            count++;
            if (count > 6) break;

            db.collection("users").document(uid)
                    .get()
                    .addOnSuccessListener((DocumentSnapshot doc) -> {
                        String full = UserUtils.getDisplayNameFromSnapshot(doc, uid);
                        nameCache.put(uid, full);

                        // refresh visible row safely
                        notifyDataSetChanged();
                    });
        }
    }

    /**
     * @param updateLastMessagePrefix if true, we refresh tvLastMessage using the loaded name
     */
    private void loadUserName(String uid, ConversationViewHolder holder, boolean updateLastMessagePrefix) {
        UserRepository.loadUserName(uid, full -> {
            if (full != null && !full.isEmpty()) {
                nameCache.put(uid, full);

                if (updateLastMessagePrefix) {
                    String current = holder.tvLastMessage.getText().toString();
                    if (current.startsWith("...:")) {
                        holder.tvLastMessage.setText(full + current.substring(3)); // keeps ": message"
                    } else if (current.startsWith("...")) {
                        holder.tvLastMessage.setText(full + ": " + current);
                    } else {
                        // if something else, do nothing (avoid corrupting text)
                    }
                } else {
                    holder.tvTitle.setText(full);
                }
            } else {
                // Fallback to uid if name not found
                nameCache.put(uid, uid);
                if (!updateLastMessagePrefix) holder.tvTitle.setText(uid);
            }
        });
    }

    @Override
    public int getItemCount() {
        return conversations.size();
    }

    static class ConversationViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvLastMessage, tvLastMessageTime, tvUnreadBadge;

        public ConversationViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_conversation_title);
            tvLastMessage = itemView.findViewById(R.id.tv_last_message);
            tvLastMessageTime = itemView.findViewById(R.id.tv_last_message_time);
            tvUnreadBadge = itemView.findViewById(R.id.tv_unread_badge);
        }
    }
}
