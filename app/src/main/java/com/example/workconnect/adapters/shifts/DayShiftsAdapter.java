package com.example.workconnect.adapters.shifts;

import android.text.TextUtils;
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
import java.util.Map;

public class DayShiftsAdapter extends RecyclerView.Adapter<DayShiftsAdapter.ShiftViewHolder> {

    public static class Row {
        // Common
        public String shiftId;
        public String title;

        // Availability mode
        public String status; // "CAN" / "PREFER_NOT" / "CANT" / null

        // Manager mode
        public ShiftTemplate template; // if present, enable Assign button
        public int startHour;
        public int endHour;
        public List<String> assignedUserIds;
        public Map<String, String> uidToName;

        public Row(String shiftId, String title, String status) {
            this.shiftId = shiftId;
            this.title = title;
            this.status = status;

            this.template = null;
            this.startHour = 0;
            this.endHour = 0;
            this.assignedUserIds = null;
            this.uidToName = null;
        }
    }

    public interface Listener {
        // Availability flow uses this
        void onShiftClicked(String shiftId);

        // Manager flow uses this
        void onAssignClicked(ShiftTemplate template);
    }

    private final List<Row> shifts = new ArrayList<>();
    private final Listener listener;

    public DayShiftsAdapter(List<Row> shifts, Listener listener) {
        if (shifts != null) this.shifts.addAll(shifts);
        this.listener = listener;
    }

    public void setRows(List<Row> newRows) {
        shifts.clear();
        if (newRows != null) shifts.addAll(newRows);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ShiftViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_day_shift_row, parent, false);
        return new ShiftViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ShiftViewHolder h, int position) {
        Row r = shifts.get(position);

        h.tvTitle.setText(r.title == null ? "Shift" : r.title);

        boolean isManagerMode = (r.template != null);

        // --- Availability mode UI ---
        if (!isManagerMode) {
            h.btnAssign.setVisibility(View.GONE);
            h.tvAssigned.setVisibility(View.GONE);
            h.tvTime.setVisibility(View.GONE);

            String label = (r.status == null) ? "Status: (not set)" : ("Status: " + r.status);
            h.tvStatus.setText(label);
            h.tvStatus.setVisibility(View.VISIBLE);

            h.itemView.setOnClickListener(v -> {
                if (listener != null && r.shiftId != null) listener.onShiftClicked(r.shiftId);
            });

            return;
        }

        // --- Manager mode UI ---
        h.itemView.setOnClickListener(null);

        h.tvStatus.setVisibility(View.GONE);

        h.tvTime.setVisibility(View.VISIBLE);
        h.tvTime.setText(formatHour(r.startHour) + " - " + formatHour(r.endHour));

        h.tvAssigned.setVisibility(View.VISIBLE);
        h.tvAssigned.setText(buildAssignedSummary(r.assignedUserIds, r.uidToName));

        h.btnAssign.setVisibility(View.VISIBLE);
        h.btnAssign.setOnClickListener(v -> {
            if (listener != null) listener.onAssignClicked(r.template);
        });
    }

    private String formatHour(int h) {
        String hh = (h < 10 ? "0" : "") + h;
        return hh + ":00";
    }

    private String buildAssignedSummary(List<String> uids, Map<String, String> uidToName) {
        int n = (uids == null) ? 0 : uids.size();
        if (n == 0) return "Assigned: 0";

        ArrayList<String> names = new ArrayList<>();
        for (String uid : uids) {
            String name = (uidToName == null) ? null : uidToName.get(uid);
            if (name == null || name.trim().isEmpty()) name = uid;
            names.add(name);
        }

        int previewCount = Math.min(3, names.size());
        String preview = TextUtils.join(", ", names.subList(0, previewCount));
        if (names.size() > previewCount) preview = preview + ", +" + (names.size() - previewCount);
        return "Assigned: " + n + " (" + preview + ")";
    }

    @Override
    public int getItemCount() {
        return shifts.size();
    }

    static class ShiftViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvStatus;
        TextView tvTime;
        TextView tvAssigned;
        Button btnAssign;

        public ShiftViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_shift_title);
            tvStatus = itemView.findViewById(R.id.tv_shift_status);
            tvTime = itemView.findViewById(R.id.tv_shift_time);
            tvAssigned = itemView.findViewById(R.id.tv_assigned);
            btnAssign = itemView.findViewById(R.id.btn_assign);
        }
    }
}
