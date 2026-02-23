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

public class NotificationsAdapter extends RecyclerView.Adapter<NotificationsAdapter.VH> {

    public interface Listener {
        void onClick(AppNotification n);
    }

    private final Listener listener;
    private final List<AppNotification> items = new ArrayList<>();

    public NotificationsAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<AppNotification> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        AppNotification n = items.get(position);

        h.tvTitle.setText(n.getTitle());
        h.tvBody.setText(n.getBody());

        // unread indicator (if you still use it)
        h.tvUnread.setVisibility(n.isRead() ? View.INVISIBLE : View.VISIBLE);

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(n);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

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
