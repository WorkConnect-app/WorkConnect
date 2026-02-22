package com.example.workconnect.adapters.chats;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.workconnect.R;
import com.example.workconnect.models.User;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GroupInfoMemberAdapter extends RecyclerView.Adapter<GroupInfoMemberAdapter.VH> {

    public interface OnSelectionChangedListener {
        void onSelectionChanged(Set<String> selectedUids);
    }

    private final List<User> members;
    private final OnSelectionChangedListener listener;

    // Multi-select support
    private final Set<String> selectedUids = new HashSet<>();

    public GroupInfoMemberAdapter(List<User> members, OnSelectionChangedListener listener) {
        this.members = members;
        this.listener = listener;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        User u = members.get(position);
        return (u != null && u.getUid() != null) ? u.getUid().hashCode() : position;
    }

    public Set<String> getSelectedUids() {
        return selectedUids;
    }

    public void clearSelection() {
        selectedUids.clear();
        notifyDataSetChanged();
        if (listener != null) listener.onSelectionChanged(selectedUids);
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
        User u = members.get(position);
        String uid = (u != null) ? u.getUid() : null;

        String name = com.example.workconnect.utils.UserUtils.getDisplayName(u, 
            (u != null && u.getEmail() != null) ? u.getEmail() : uid);

        holder.name.setText(name);
        holder.sub.setText((u != null && u.getEmail() != null) ? u.getEmail() : uid);

        // Apply selected state
        boolean isSelected = uid != null && selectedUids.contains(uid);
        holder.itemView.setSelected(isSelected);

        // Consume click to handle selection
        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            if (uid == null) return;

            if (selectedUids.contains(uid)) {
                selectedUids.remove(uid);   // Deselect
            } else {
                selectedUids.add(uid);      // Select
            }

            notifyItemChanged(pos);
            if (listener != null) listener.onSelectionChanged(selectedUids);
        });

        // Avoid long-click opening context menu
        holder.itemView.setOnLongClickListener(v -> true);
    }

    @Override
    public int getItemCount() {
        return members.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView name, sub;
        VH(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tv_member_name);
            sub  = itemView.findViewById(R.id.tv_member_sub);
        }
    }
}
