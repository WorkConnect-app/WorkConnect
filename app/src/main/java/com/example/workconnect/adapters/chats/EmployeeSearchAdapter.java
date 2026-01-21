package com.example.workconnect.adapters.chats;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.workconnect.R;
import com.example.workconnect.models.User;

import java.util.List;

/**
 * Adapter for displaying employee search results in a RecyclerView.
 * Used in the chat list screen to show users that can be selected to start a conversation.
 */
public class EmployeeSearchAdapter extends RecyclerView.Adapter<EmployeeSearchAdapter.VH> {

    /**
     * Callback interface for handling user selection clicks.
     */
    public interface OnUserClick {
        void onClick(User user);
    }

    private final List<User> users;
    private final OnUserClick listener;

    public EmployeeSearchAdapter(List<User> users, OnUserClick listener) {
        this.users = users;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_employee_search, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        User u = users.get(position);
        String displayName;

        // Build display name with fallback logic:
        // 1. Use fullName if available
        // 2. Otherwise combine firstName + lastName
        // 3. Fallback to "Employee" if no name is available
        if (u.getFullName() != null && !u.getFullName().trim().isEmpty()) {
            displayName = u.getFullName().trim();
        } else {
            String first = u.getFirstName() != null ? u.getFirstName().trim() : "";
            String last  = u.getLastName()  != null ? u.getLastName().trim()  : "";
            displayName = (first + " " + last).trim();
            if (displayName.isEmpty()) displayName = "Employee";
        }

        holder.name.setText(displayName);
        holder.email.setText(u.getEmail() != null ? u.getEmail() : "");

        // Handle click to start conversation with selected user
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(u);
        });
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    /**
     * ViewHolder for employee search items.
     * Holds references to the name and email TextViews.
     */
    static class VH extends RecyclerView.ViewHolder {
        TextView name, email;
        
        VH(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tv_employee_name);
            email = itemView.findViewById(R.id.tv_employee_email);
        }
    }
}

