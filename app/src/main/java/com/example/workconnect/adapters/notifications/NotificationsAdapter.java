package com.example.workconnect.adapters.notifications;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.workconnect.R;
import com.example.workconnect.models.AppNotification;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for showing app notifications inside RecyclerView.
 * Responsible only for binding data to the item view.
 */
public class NotificationsAdapter extends RecyclerView.Adapter<NotificationsAdapter.VH> {

    // Listener for click events on a notification
    public interface Listener {
        void onClick(AppNotification n);
    }

    private final Listener listener;

    // Local list holding current notifications
    private final List<AppNotification> items = new ArrayList<>();

    public NotificationsAdapter(Listener listener) {
        this.listener = listener;
    }

    /**
     * Replace current list with new notifications.
     */
    public void submit(List<AppNotification> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged(); // simple refresh (no DiffUtil for now)
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate single notification layout
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        // Get current notification
        AppNotification n = items.get(position);

        // Bind title and body text
        h.tvTitle.setText(n.getTitle());
        h.tvBody.setText(n.getBody());

        // Show unread indicator only if notification is not read
        h.tvUnread.setVisibility(n.isRead() ? View.INVISIBLE : View.VISIBLE);

        // Handle click event
        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(n);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /**
     * ViewHolder class holding references to item views.
     */
    static class VH extends RecyclerView.ViewHolder {

        TextView tvTitle, tvBody, tvUnread;

        VH(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_notif_title);
            tvBody = itemView.findViewById(R.id.tv_notif_body);
            tvUnread = itemView.findViewById(R.id.tv_unread_dot);
        }
    }
}