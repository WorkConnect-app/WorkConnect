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
 * Adapter for displaying group members in a RecyclerView.
 * Supports multi-selection of members (used when adding members to a group).
 * Selected members are tracked in the selectedUids list.
 */
public class GroupMemberAdapter extends RecyclerView.Adapter<GroupMemberAdapter.VH> {

    private final List<User> users;
    // List of selected user IDs (shared reference, modified by toggle method)
    private final List<String> selectedUids;

    public GroupMemberAdapter(List<User> users, List<String> selectedUids) {
        this.users = users;
        this.selectedUids = selectedUids;
        // Enable stable IDs to help RecyclerView maintain selection state during updates
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        User u = users.get(position);
        return (u.getUid() != null) ? u.getUid().hashCode() : position;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_group_member, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        User u = users.get(position);
        String uid = u.getUid();

        // Build display name with fallback logic:
        // 1. Use fullName if available
        // 2. Otherwise combine firstName + lastName
        // 3. Fallback to uid if no name is available
        String name = com.example.workconnect.utils.UserUtils.getDisplayName(u, uid);

        holder.tvName.setText(name);

        // Display email as subtitle, fallback to uid if email is not available
        String sub = u.getEmail();
        if (sub == null || sub.trim().isEmpty()) sub = uid;
        holder.tvSub.setText(sub);

        // Update selection state based on selectedUids list
        boolean isSelected = uid != null && selectedUids.contains(uid);
        holder.itemView.setSelected(isSelected);

        // Handle click to toggle selection
        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;

            toggle(uid);
            notifyItemChanged(pos);
        });
    }

    /**
     * Toggle selection state for a user.
     * If the user is already selected, remove them from the list.
     * Otherwise, add them to the selected list.
     */
    private void toggle(String uid) {
        if (uid == null) return;

        if (selectedUids.contains(uid)) {
            selectedUids.remove(uid);
        } else {
            selectedUids.add(uid);
        }
    }



    @Override
    public int getItemCount() {
        return users.size();
    }

    /**
     * ViewHolder for group member items.
     * Holds references to the name and subtitle (email) TextViews.
     */
    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvSub;

        VH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_member_name);
            tvSub  = itemView.findViewById(R.id.tv_member_sub);
        }
    }
}

