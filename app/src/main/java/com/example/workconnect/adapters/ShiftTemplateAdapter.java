package com.example.workconnect.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.workconnect.R;
import com.example.workconnect.models.ShiftTemplate;

import java.util.ArrayList;
import java.util.List;

public class ShiftTemplateAdapter extends RecyclerView.Adapter<ShiftTemplateAdapter.VH> {

    public interface Listener {
        void onEdit(ShiftTemplate t);
        void onDelete(ShiftTemplate t);
        void onToggleEnabled(ShiftTemplate t, boolean enabled);
    }

    private final Listener listener;
    private final List<ShiftTemplate> items = new ArrayList<>();

    public ShiftTemplateAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<ShiftTemplate> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_shift_template, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        ShiftTemplate t = items.get(position);

        String title = t.getTitle() == null ? "(Untitled)" : t.getTitle();
        h.tvTitle.setText(title);
        h.tvTime.setText(t.getStartHour() + ":00 - " + t.getEndHour() + ":00");

        h.swEnabled.setOnCheckedChangeListener(null);
        h.swEnabled.setChecked(t.isEnabled());
        h.swEnabled.setOnCheckedChangeListener((btn, checked) -> listener.onToggleEnabled(t, checked));

        h.btnEdit.setOnClickListener(v -> listener.onEdit(t));
        h.btnDelete.setOnClickListener(v -> listener.onDelete(t));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvTime;
        Switch swEnabled;
        ImageButton btnEdit, btnDelete;

        VH(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvTime = itemView.findViewById(R.id.tv_time);
            swEnabled = itemView.findViewById(R.id.sw_enabled);
            btnEdit = itemView.findViewById(R.id.btn_edit);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }
    }
}
