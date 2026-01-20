package com.example.workconnect.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.workconnect.R;
import com.example.workconnect.ui.chat.ChatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageInfoAdapter extends RecyclerView.Adapter<MessageInfoAdapter.ParticipantViewHolder> {
    
    private final List<ChatActivity.ParticipantReadStatus> participants;
    private final ChatMessageAdapter adapter;
    
    public MessageInfoAdapter(List<ChatActivity.ParticipantReadStatus> participants, ChatMessageAdapter adapter) {
        this.participants = participants;
        this.adapter = adapter;
    }
    
    @NonNull
    @Override
    public ParticipantViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message_info_participant, parent, false);
        return new ParticipantViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ParticipantViewHolder holder, int position) {
        ChatActivity.ParticipantReadStatus status = participants.get(position);
        
        // Get participant name
        String name = adapter.getSenderName(status.userId);
        if (name == null || name.isEmpty()) {
            name = status.userId;
        }
        holder.textParticipantName.setText(name);
        
        // Set status (Sent or Read)
        if (status.isRead) {
            holder.textParticipantStatus.setText("✓✓");
            holder.textParticipantStatus.setTextColor(holder.itemView.getContext().getColor(R.color.readCheckmarkBlue));
        } else {
            holder.textParticipantStatus.setText("✓");
            holder.textParticipantStatus.setTextColor(holder.itemView.getContext().getColor(android.R.color.darker_gray));
        }
        
        // Show read time if available
        if (status.readAt != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            holder.textReadTime.setText(sdf.format(status.readAt));
            holder.textReadTime.setVisibility(View.VISIBLE);
        } else {
            holder.textReadTime.setVisibility(View.GONE);
        }
    }
    
    @Override
    public int getItemCount() {
        return participants != null ? participants.size() : 0;
    }
    
    static class ParticipantViewHolder extends RecyclerView.ViewHolder {
        TextView textParticipantName;
        TextView textParticipantStatus;
        TextView textReadTime;
        
        ParticipantViewHolder(@NonNull View itemView) {
            super(itemView);
            textParticipantName = itemView.findViewById(R.id.textParticipantName);
            textParticipantStatus = itemView.findViewById(R.id.textParticipantStatus);
            textReadTime = itemView.findViewById(R.id.textReadTime);
        }
    }
}
