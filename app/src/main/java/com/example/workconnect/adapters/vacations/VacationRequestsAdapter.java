package com.example.workconnect.adapters.vacations;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.workconnect.R;
import com.example.workconnect.models.VacationRequest;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class VacationRequestsAdapter extends RecyclerView.Adapter<VacationRequestsAdapter.VH> {

    private final List<VacationRequest> items = new ArrayList<>();
    private final SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    public void submit(List<VacationRequest> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_vacation_request, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        VacationRequest r = items.get(position);

        String start = (r.getStartDate() != null) ? df.format(r.getStartDate()) : "";
        String end = (r.getEndDate() != null) ? df.format(r.getEndDate()) : "";
        h.tvVacationDate.setText(start + " → " + end);

        String status = (r.getStatus() != null) ? r.getStatus().toString() : "";
        h.tvVacationStatus.setText(status);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvVacationDate, tvVacationStatus;

        VH(@NonNull View itemView) {
            super(itemView);
            tvVacationDate = itemView.findViewById(R.id.tv_vacation_date);
            tvVacationStatus = itemView.findViewById(R.id.tv_vacation_status);
        }
    }
}
