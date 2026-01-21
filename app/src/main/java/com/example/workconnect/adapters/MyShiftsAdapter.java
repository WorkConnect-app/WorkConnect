package com.example.workconnect.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.workconnect.R;
import com.example.workconnect.models.MyShiftItem;

import java.util.ArrayList;
import java.util.List;

public class MyShiftsAdapter extends RecyclerView.Adapter<MyShiftsAdapter.VH> {

    private final List<MyShiftItem> items = new ArrayList<>();

    public void setItems(List<MyShiftItem> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_my_shift, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        MyShiftItem s = items.get(position);

        h.tvDate.setText(s.getDateKey());
        h.tvTeam.setText(s.getTeamName());

        String title = s.getTemplateTitle() == null ? "Shift" : s.getTemplateTitle();
        h.tvTitle.setText(title);

        h.tvTime.setText(s.getStartHour() + ":00 - " + s.getEndHour() + ":00");
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvDate, tvTeam, tvTitle, tvTime;

        VH(@NonNull View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tv_date);
            tvTeam = itemView.findViewById(R.id.tv_team);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvTime = itemView.findViewById(R.id.tv_time);
        }
    }
}
