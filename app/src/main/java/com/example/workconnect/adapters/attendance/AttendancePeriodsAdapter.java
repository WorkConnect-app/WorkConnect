package com.example.workconnect.adapters.attendance;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.workconnect.R;
import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AttendancePeriodsAdapter
        extends RecyclerView.Adapter<AttendancePeriodsAdapter.VH> {

    private final List<Map<String, Object>> periods = new ArrayList<>();

    public void submit(List<Map<String, Object>> list) {
        periods.clear();
        if (list != null) periods.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_attendance_period, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Map<String, Object> p = periods.get(pos);

        Timestamp start = (Timestamp) p.get("startAt");
        Timestamp end = (Timestamp) p.get("endAt");

        h.start.setText("Start: " + (start == null ? "-" : start.toDate().toString()));
        h.end.setText(end == null
                ? "End: In progress"
                : "End: " + end.toDate().toString());
    }

    @Override
    public int getItemCount() {
        return periods.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView start, end;

        VH(@NonNull View v) {
            super(v);
            start = v.findViewById(R.id.txtStart);
            end = v.findViewById(R.id.txtEnd);
        }
    }
}
