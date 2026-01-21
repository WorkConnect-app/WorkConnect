package com.example.workconnect.adapters.shifts;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.workconnect.R;
import com.example.workconnect.models.ShiftTemplate;

import java.util.ArrayList;
import java.util.List;

public class ShiftScheduleAdapter extends RecyclerView.Adapter<ShiftScheduleAdapter.VH> {

    public static class Row {
        public ShiftTemplate template;
        public List<String> assignedUserIds;
        public String assignedSummary; // NEW

        public Row(ShiftTemplate template,
                   List<String> assignedUserIds,
                   String assignedSummary) {
            this.template = template;
            this.assignedUserIds = assignedUserIds;
            this.assignedSummary = assignedSummary;
        }
    }



    public interface Listener {
        void onAssignClicked(ShiftTemplate t);
    }

    private final Listener listener;
    private final List<Row> rows = new ArrayList<>();

    public ShiftScheduleAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setRows(List<Row> list) {
        rows.clear();
        if (list != null) rows.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_shift_schedule_row, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Row r = rows.get(position);

        String title = r.template.getTitle() == null ? "(Untitled)" : r.template.getTitle();
        h.tvTitle.setText(title);
        h.tvTime.setText(r.template.getStartHour() + ":00 - " + r.template.getEndHour() + ":00");

        int count = (r.assignedUserIds == null) ? 0 : r.assignedUserIds.size();
        h.tvAssigned.setText(r.assignedSummary);

        h.btnAssign.setOnClickListener(v -> listener.onAssignClicked(r.template));
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvTime, tvAssigned;
        Button btnAssign;

        VH(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvTime = itemView.findViewById(R.id.tv_time);
            tvAssigned = itemView.findViewById(R.id.tv_assigned);
            btnAssign = itemView.findViewById(R.id.btn_assign);
        }
    }
}
