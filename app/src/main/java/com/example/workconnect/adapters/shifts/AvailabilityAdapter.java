package com.example.workconnect.adapters.shifts;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.workconnect.R;

import java.util.ArrayList;
import java.util.List;

public class AvailabilityAdapter extends RecyclerView.Adapter<AvailabilityAdapter.VH> {

    public static class Row {
        public String teamId;
        public String teamName;
        public String dateKey;     // yyyy-MM-dd
        public String shiftId;
        public String shiftLabel;  // e.g. "Morning (08-16)"
        public String status;      // null or "CAN"/"PREFER_NOT"/"CANT"

        public Row(String teamId, String teamName, String dateKey,
                   String shiftId, String shiftLabel, String status) {
            this.teamId = teamId;
            this.teamName = teamName;
            this.dateKey = dateKey;
            this.shiftId = shiftId;
            this.shiftLabel = shiftLabel;
            this.status = status;
        }
    }

    public interface Listener {
        void onStatusChanged(String teamId, String dateKey, String shiftId, String newStatus);
    }

    private final Listener listener;
    private final List<Row> rows = new ArrayList<>();

    public AvailabilityAdapter(Listener listener) {
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
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_availability_row, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Row r = rows.get(position);

        h.tvDate.setText(r.dateKey);
        h.tvTeam.setText(r.teamName == null ? r.teamId : r.teamName);
        h.tvShift.setText(r.shiftLabel == null ? r.shiftId : r.shiftLabel);

        h.rg.setOnCheckedChangeListener(null);

        h.rg.clearCheck();
        if ("CAN".equals(r.status)) h.rbCan.setChecked(true);
        else if ("PREFER_NOT".equals(r.status)) h.rbPreferNot.setChecked(true);
        else if ("CANT".equals(r.status)) h.rbCant.setChecked(true);

        h.rg.setOnCheckedChangeListener((group, checkedId) -> {
            String status = null;
            if (checkedId == R.id.rb_can) status = "CAN";
            else if (checkedId == R.id.rb_prefer_not) status = "PREFER_NOT";
            else if (checkedId == R.id.rb_cant) status = "CANT";

            if (status != null) {
                r.status = status;
                listener.onStatusChanged(r.teamId, r.dateKey, r.shiftId, status);
            }
        });
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvDate, tvTeam, tvShift;
        RadioGroup rg;
        RadioButton rbCan, rbPreferNot, rbCant;

        VH(@NonNull View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tv_date);
            tvTeam = itemView.findViewById(R.id.tv_team);
            tvShift = itemView.findViewById(R.id.tv_shift);

            rg = itemView.findViewById(R.id.rg_status);
            rbCan = itemView.findViewById(R.id.rb_can);
            rbPreferNot = itemView.findViewById(R.id.rb_prefer_not);
            rbCant = itemView.findViewById(R.id.rb_cant);
        }
    }
}
