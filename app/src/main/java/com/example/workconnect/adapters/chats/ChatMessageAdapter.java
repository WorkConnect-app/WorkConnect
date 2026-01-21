package com.example.workconnect.adapters.chats;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.workconnect.R;
import com.example.workconnect.models.ChatMessage;
import com.example.workconnect.models.ChatItem;
import com.example.workconnect.utils.DateHelper;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageAdapter.MessageViewHolder> {

    public interface OnRetryClickListener {
        void onRetryClick(ChatMessage message);
    }
    
    public interface OnMessageLongClickListener {
        void onMessageLongClick(ChatMessage message, View view);
    }
    
    public interface OnReactionsClickListener {
        void onReactionsClick(ChatMessage message);
    }

    private final String currentUserId;

    // Use AsyncListDiffer for incremental updates
    // Changed to ChatItem to support date separators
    private final AsyncListDiffer<ChatItem> differ;

    private boolean isGroup = false;
    private List<String> participantIds; // For calculating read status in groups

    // Cache uid -> name (only used when isGroup)
    private final Map<String, String> nameCache = new HashMap<>();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    
    private OnRetryClickListener retryClickListener;
    private OnMessageLongClickListener longClickListener;
    private OnReactionsClickListener reactionsClickListener;

    public ChatMessageAdapter(String currentUserId) {
        this.currentUserId = currentUserId;
        
        // Initialize AsyncListDiffer with ItemDiffCallback
        this.differ = new AsyncListDiffer<>(this, new ItemDiffCallback());
    }
    
    public void setOnRetryClickListener(OnRetryClickListener listener) {
        this.retryClickListener = listener;
    }
    
    public void setOnMessageLongClickListener(OnMessageLongClickListener listener) {
        this.longClickListener = listener;
    }
    
    public void setOnReactionsClickListener(OnReactionsClickListener listener) {
        this.reactionsClickListener = listener;
    }

    /**
     * Submit a list of messages and automatically insert date separators
     */
    public void submitList(List<ChatMessage> newMessages) {
        List<ChatItem> itemsWithSeparators = insertDateSeparators(newMessages);
        differ.submitList(itemsWithSeparators);
    }
    
    /**
     * Insert date separators between messages from different days (WhatsApp style)
     */
    private List<ChatItem> insertDateSeparators(List<ChatMessage> messages) {
        List<ChatItem> items = new ArrayList<>();
        
        if (messages.isEmpty()) {
            return items;
        }
        
        // Always add a separator for the first message's date
        items.add(new ChatItem(messages.get(0).getSentAt()));
        items.add(new ChatItem(messages.get(0)));
        
        // Check for date changes between consecutive messages
        for (int i = 1; i < messages.size(); i++) {
            ChatMessage current = messages.get(i);
            ChatMessage previous = messages.get(i - 1);
            
            // If different day, add a date separator
            if (current.getSentAt() != null && previous.getSentAt() != null &&
                DateHelper.isDifferentDay(previous.getSentAt(), current.getSentAt())) {
                items.add(new ChatItem(current.getSentAt()));
            }
            
            items.add(new ChatItem(current));
        }
        
        return items;
    }

    public void setGroup(boolean group) {
        this.isGroup = group;
        notifyDataSetChanged();
    }
    
    public void setParticipantIds(List<String> participantIds) {
        this.participantIds = participantIds;
    }

    @Override
    public int getItemViewType(int position) {
        ChatItem item = differ.getCurrentList().get(position);
        
        if (item.isDateSeparator()) {
            return 3; // Date separator
        }
        
        ChatMessage msg = item.getMessage();
        // System messages have their own view type
        if (msg.isSystemMessage()) {
            return 2; // System message
        }
        return msg.getSenderId().equals(currentUserId) ? 0 : 1;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout;
        if (viewType == 2) {
            layout = R.layout.item_chat_message_system;
        } else if (viewType == 3) {
            layout = R.layout.item_chat_date_separator;
        } else {
            layout = (viewType == 0)
                    ? R.layout.item_chat_message_me
                    : R.layout.item_chat_message_other;
        }

        View view = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new MessageViewHolder(view, viewType);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        ChatItem item = differ.getCurrentList().get(position);
        
        // Handle date separators
        if (item.isDateSeparator()) {
            if (holder.textDateSeparator != null && item.getSeparatorDate() != null) {
                String dateText = DateHelper.getDateSeparatorText(item.getSeparatorDate());
                holder.textDateSeparator.setText(dateText);
            }
            return;
        }
        
        ChatMessage msg = item.getMessage();
        
        // Handle system messages
        if (msg.isSystemMessage()) {
            if (holder.textSystemMessage != null) {
                String systemText = generateSystemMessageText(msg);
                holder.textSystemMessage.setText(systemText);
            }
            // Add time for system messages
            if (holder.textSystemTime != null && msg.getSentAt() != null) {
                holder.textSystemTime.setText(DateHelper.formatTime(msg.getSentAt()));
                holder.textSystemTime.setVisibility(View.VISIBLE);
            } else if (holder.textSystemTime != null) {
                holder.textSystemTime.setVisibility(View.GONE);
            }
            return; // Don't process further for system messages
        }

        // Handle file/image messages
        if (msg.hasFile()) {
            if (holder.imagePreview != null) {
                if (msg.isImage()) {
                    // Show image
                    holder.imagePreview.setVisibility(View.VISIBLE);
                    Glide.with(holder.itemView.getContext())
                            .load(msg.getFileUrl())
                            .into(holder.imagePreview);
                } else {
                    holder.imagePreview.setVisibility(View.GONE);
                }
            }
            
            if (holder.fileInfo != null && holder.textFileName != null) {
                if (msg.isImage()) {
                    holder.fileInfo.setVisibility(View.GONE);
                } else {
                    // Show file info
                    holder.fileInfo.setVisibility(View.VISIBLE);
                    String fileName = msg.getFileName() != null ? msg.getFileName() : "File";
                    holder.textFileName.setText("📎 " + fileName);
                }
            }
        } else {
            if (holder.imagePreview != null) {
                holder.imagePreview.setVisibility(View.GONE);
            }
            if (holder.fileInfo != null) {
                holder.fileInfo.setVisibility(View.GONE);
            }
        }
        
        holder.textMessage.setText(msg.getText());

        // Time - use DateHelper for consistent formatting (HH:mm)
        // Always show time if available (below the bubble for "me" messages, same as "other")
        if (holder.textTime != null) {
            if (msg.getSentAt() != null) {
                String timeText = DateHelper.formatTime(msg.getSentAt());
                holder.textTime.setText(timeText);
                holder.textTime.setVisibility(View.VISIBLE);
            } else {
                holder.textTime.setText("");
                holder.textTime.setVisibility(View.GONE);
            }
        } else {
            // Debug: log if textTime is null
            android.util.Log.e("ChatAdapter", "textTime is null for message: " + msg.getId());
        }

        // Sender name in group (only for "other" bubbles)
        boolean isOtherBubble = getItemViewType(position) == 1;

        if (holder.textSenderName != null) {
            if (isGroup && isOtherBubble) {
                holder.textSenderName.setVisibility(View.VISIBLE);
                String senderId = msg.getSenderId();

                if (senderId == null || senderId.trim().isEmpty()) {
                    holder.textSenderName.setText("");
                } else {
                    String cached = nameCache.get(senderId);
                    if (cached != null) {
                        holder.textSenderName.setText(cached);
                    } else {
                        holder.textSenderName.setText("...");
                        loadUserName(senderId, holder);
                    }
                }
            } else {
                holder.textSenderName.setVisibility(View.GONE);
            }
        }
        
        // Show error icon for failed messages (only for "me" bubbles)
        if (holder.iconError != null && !isOtherBubble) {
            if (msg.getStatus() == ChatMessage.MessageStatus.FAILED) {
                holder.iconError.setVisibility(View.VISIBLE);
                holder.itemView.setOnClickListener(v -> {
                    if (retryClickListener != null) {
                        retryClickListener.onRetryClick(msg);
                    }
                });
            } else {
                holder.iconError.setVisibility(View.GONE);
                holder.itemView.setOnClickListener(null);
            }
        }
        
        // WhatsApp-style read receipts (only for "me" bubbles)
        if (holder.textReadStatus != null && !isOtherBubble) {
            updateReadStatus(holder, msg);
        }
        
        // Display reactions
        displayReactions(holder, msg);
        
        // Long-press listener for context menu (skip for system messages and date separators)
        if (holder.itemView != null && !msg.isSystemMessage()) {
            holder.itemView.setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onMessageLongClick(msg, holder.itemView);
                    return true;
                }
                return false;
            });
        }
    }
    
    private String generateSystemMessageText(ChatMessage msg) {
        if (msg.getSystemType() == null || msg.getSystemUserId() == null) {
            return msg.getText() != null ? msg.getText() : "System message";
        }
        
        String userId = msg.getSystemUserId();
        String userName = nameCache.get(userId);
        
        // If name not cached, try to load it (but return placeholder for now)
        if (userName == null) {
            loadUserNameForSystemMessage(userId);
            userName = "Someone";
        }
        
        // For actions with an actor (who performed the action)
        String actorName = null;
        if (msg.getSystemActorId() != null && !msg.getSystemActorId().isEmpty()) {
            actorName = nameCache.get(msg.getSystemActorId());
            if (actorName == null) {
                loadUserNameForSystemMessage(msg.getSystemActorId());
                actorName = "Someone";
            }
        }
        
        switch (msg.getSystemType()) {
            case USER_JOINED:
                return userName + " joined the group";
            case USER_LEFT:
                return userName + " left the group";
            case GROUP_CREATED:
                // Use the text from the message (e.g., "Raphael created this group")
                if (msg.getText() != null && !msg.getText().isEmpty()) {
                    return msg.getText();
                }
                return userName + " created this group";
            case USER_ADDED:
                if (actorName != null) {
                    return actorName + " added " + userName + " to the group";
                }
                return userName + " was added to the group";
            case USER_REMOVED:
                if (actorName != null) {
                    return actorName + " removed " + userName + " from the group";
                }
                return userName + " was removed from the group";
            case GROUP_OPENED:
                return userName + " opened this group";
            default:
                return msg.getText() != null ? msg.getText() : "System message";
        }
    }
    
    private void loadUserNameForSystemMessage(String uid) {
        if (nameCache.containsKey(uid)) return;
        
        db.collection("users").document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    String first = doc.getString("firstName");
                    String last = doc.getString("lastName");
                    String full = ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
                    if (full.isEmpty()) full = doc.getString("fullName");
                    if (full == null || full.trim().isEmpty()) full = doc.getString("name");
                    if (full == null || full.trim().isEmpty()) full = uid;
                    nameCache.put(uid, full);
                    notifyDataSetChanged(); // Refresh to show correct name
                });
    }
    
    private void updateReadStatus(MessageViewHolder holder, ChatMessage msg) {
        if (msg.getStatus() == ChatMessage.MessageStatus.PENDING || 
            msg.getStatus() == ChatMessage.MessageStatus.FAILED) {
            holder.textReadStatus.setVisibility(View.GONE);
            return;
        }
        
        holder.textReadStatus.setVisibility(View.VISIBLE);
        
        if (isGroup) {
            // Group: show checkmarks based on read status
            if (participantIds == null || participantIds.isEmpty()) {
                // No participants info - show single checkmark (sent)
                holder.textReadStatus.setText("✓");
                holder.textReadStatus.setTextColor(holder.itemView.getContext().getColor(android.R.color.darker_gray));
            } else {
                int totalRecipients = participantIds.size() - 1; // Exclude sender
                int readCount = msg.getReadCount();
                
                if (readCount == 0) {
                    // Not read by anyone yet - single checkmark (sent)
                    holder.textReadStatus.setText("✓");
                    holder.textReadStatus.setTextColor(holder.itemView.getContext().getColor(android.R.color.darker_gray));
                } else if (readCount == totalRecipients) {
                    // Read by all - double checkmarks blue
                    holder.textReadStatus.setText("✓✓");
                    holder.textReadStatus.setTextColor(holder.itemView.getContext().getColor(R.color.readCheckmarkBlue));
                } else {
                    // Partially read - double checkmarks gray (some have read, some haven't)
                    holder.textReadStatus.setText("✓✓");
                    holder.textReadStatus.setTextColor(holder.itemView.getContext().getColor(android.R.color.darker_gray));
                }
            }
        } else {
            // Direct message: WhatsApp style
            // ✓ = sent (not received/read yet), ✓✓ = read (blue)
            List<String> readBy = msg.getReadBy();
            boolean isRead = readBy != null && !readBy.isEmpty();
            
            if (isRead) {
                // Read (blue double checkmarks) - WhatsApp style
                // Use lighter blue for better visibility on blue bubble
                holder.textReadStatus.setText("✓✓");
                holder.textReadStatus.setTextColor(holder.itemView.getContext().getColor(R.color.readCheckmarkBlue));
            } else {
                // Sent but not read yet (single checkmark) - WhatsApp style
                holder.textReadStatus.setText("✓");
                holder.textReadStatus.setTextColor(holder.itemView.getContext().getColor(android.R.color.darker_gray));
            }
        }
    }

    private void loadUserName(String uid, MessageViewHolder holder) {
        db.collection("users").document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    String first = doc.getString("firstName");
                    String last  = doc.getString("lastName");

                    String full = ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
                    if (full.isEmpty()) full = doc.getString("fullName");
                    if (full == null || full.trim().isEmpty()) full = doc.getString("name");
                    if (full == null || full.trim().isEmpty()) full = uid;

                    nameCache.put(uid, full);

                    // ⚠️ holder could be recycled; we still set it, it's acceptable for MVP.
                    if (holder.textSenderName != null) {
                        holder.textSenderName.setText(full);
                    }
                })
                .addOnFailureListener(e -> {
                    nameCache.put(uid, uid);
                    if (holder.textSenderName != null) {
                        holder.textSenderName.setText(uid);
                    }
                });
    }

    @Override
    public int getItemCount() {
        return differ.getCurrentList().size();
    }

    // ===== DiffUtil Callback =====
    private static class ItemDiffCallback extends DiffUtil.ItemCallback<ChatItem> {
        @Override
        public boolean areItemsTheSame(@NonNull ChatItem oldItem, @NonNull ChatItem newItem) {
            if (oldItem.getType() != newItem.getType()) {
                return false;
            }
            
            if (oldItem.isDateSeparator()) {
                // Compare dates for separators - same if same day
                Date oldDate = oldItem.getSeparatorDate();
                Date newDate = newItem.getSeparatorDate();
                return oldDate != null && newDate != null && 
                       !DateHelper.isDifferentDay(oldDate, newDate);
            } else {
                // Compare by message ID
                ChatMessage oldMsg = oldItem.getMessage();
                ChatMessage newMsg = newItem.getMessage();
                return oldMsg != null && newMsg != null &&
                       oldMsg.getId() != null && oldMsg.getId().equals(newMsg.getId());
            }
        }

        @Override
        public boolean areContentsTheSame(@NonNull ChatItem oldItem, @NonNull ChatItem newItem) {
            if (oldItem.getType() != newItem.getType()) {
                return false;
            }
            
            if (oldItem.isDateSeparator()) {
                // For separators, compare dates
                Date oldDate = oldItem.getSeparatorDate();
                Date newDate = newItem.getSeparatorDate();
                if (oldDate == null || newDate == null) {
                    return oldDate.equals(newDate);
                }
                return !DateHelper.isDifferentDay(oldDate, newDate);
            } else {
                // Compare message contents
                ChatMessage oldMsg = oldItem.getMessage();
                ChatMessage newMsg = newItem.getMessage();
                return oldMsg != null && newMsg != null && oldMsg.equals(newMsg);
            }
        }
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {

        TextView textMessage, textTime;

        // Only exists in item_chat_message_other
        TextView textSenderName;
        
        // Only exists in item_chat_message_me
        ImageView iconError;
        TextView textReadStatus;
        
        // Only exists in item_chat_message_system
        TextView textSystemMessage;
        TextView textSystemTime;
        
        // For date separator
        TextView textDateSeparator;
        
        // For file/image messages
        ImageView imagePreview;
        LinearLayout fileInfo;
        TextView textFileName;
        
        // For reactions
        LinearLayout reactionsContainer;

        public MessageViewHolder(@NonNull View itemView, int viewType) {
            super(itemView);
            
            if (viewType == 2) {
                // System message layout
                textSystemMessage = itemView.findViewById(R.id.textSystemMessage);
                textSystemTime = itemView.findViewById(R.id.textSystemTime);
            } else if (viewType == 3) {
                // Date separator layout
                textDateSeparator = itemView.findViewById(R.id.textDateSeparator);
            } else {
                // Regular message layout
                textMessage = itemView.findViewById(R.id.textMessage);
                textTime    = itemView.findViewById(R.id.textMessageTime);

                // will be null for "me" layout, that's fine
                textSenderName = itemView.findViewById(R.id.textSenderName);
                
                // will be null for "other" layout, that's fine
                iconError = itemView.findViewById(R.id.iconError);
                textReadStatus = itemView.findViewById(R.id.textReadStatus);
                
                // File/image views (may be null if not in layout)
                imagePreview = itemView.findViewById(R.id.imagePreview);
                fileInfo = itemView.findViewById(R.id.fileInfo);
                textFileName = itemView.findViewById(R.id.textFileName);
                
                // Reactions container (may be null if not in layout)
                reactionsContainer = itemView.findViewById(R.id.reactionsContainer);
            }
        }
    }
    
    // Helper method to display reactions
    private void displayReactions(MessageViewHolder holder, ChatMessage msg) {
        if (holder.reactionsContainer == null) {
            return;
        }
        
        Map<String, List<String>> reactions = msg.getReactions();
        if (reactions == null || reactions.isEmpty()) {
            holder.reactionsContainer.setVisibility(View.GONE);
            return;
        }
        
        // Remove all existing views
        holder.reactionsContainer.removeAllViews();
        
        // Display each reaction (emoji + count)
        for (Map.Entry<String, List<String>> entry : reactions.entrySet()) {
            String emoji = entry.getKey();
            List<String> userIds = entry.getValue();
            
            if (userIds == null || userIds.isEmpty()) {
                continue;
            }
            
            // Create horizontal layout for this reaction (emoji + count)
            LinearLayout reactionLayout = new LinearLayout(holder.itemView.getContext());
            reactionLayout.setOrientation(LinearLayout.HORIZONTAL);
            reactionLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);
            
            // Add padding and margin
            int padding = (int) (4 * holder.itemView.getContext().getResources().getDisplayMetrics().density);
            reactionLayout.setPadding(padding, padding / 2, padding, padding / 2);
            
            int margin = (int) (2 * holder.itemView.getContext().getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            layoutParams.setMargins(0, 0, margin, 0);
            reactionLayout.setLayoutParams(layoutParams);
            
            // Background for reaction chip
            int cornerRadius = (int) (12 * holder.itemView.getContext().getResources().getDisplayMetrics().density);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
            bg.setCornerRadius(cornerRadius);
            bg.setColor(0xFFE8E8E8); // Light gray background
            reactionLayout.setBackground(bg);
            
            // Emoji TextView
            TextView emojiView = new TextView(holder.itemView.getContext());
            emojiView.setText(emoji);
            emojiView.setTextSize(14);
            emojiView.setPadding(padding / 2, 0, padding / 4, 0);
            reactionLayout.addView(emojiView);
            
            // Count TextView
            TextView countView = new TextView(holder.itemView.getContext());
            countView.setText(String.valueOf(userIds.size()));
            countView.setTextSize(12);
            countView.setTextColor(0xFF666666);
            countView.setPadding(0, 0, padding / 2, 0);
            reactionLayout.addView(countView);
            
            // Add to container
            holder.reactionsContainer.addView(reactionLayout);
        }
        
        // Set click listener
        if (reactionsClickListener != null) {
            holder.reactionsContainer.setOnClickListener(v -> {
                reactionsClickListener.onReactionsClick(msg);
            });
        }
        
        holder.reactionsContainer.setVisibility(View.VISIBLE);
    }
    
    // Helper method to get sender name from cache
    public String getSenderName(String userId) {
        return nameCache.get(userId);
    }
    
    // Helper method to get name cache (for ReactionsDetailAdapter)
    public Map<String, String> getNameCache() {
        return nameCache;
    }
    
    // Helper method to get current list (for scrolling to messages)
    public List<ChatItem> getCurrentList() {
        return differ.getCurrentList();
    }
}
