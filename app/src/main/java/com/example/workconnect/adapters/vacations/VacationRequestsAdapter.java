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

/**
 * Adapter for showing vacation requests of the current user.
 * Displays date range and request status.
 */
public class VacationRequestsAdapter extends RecyclerView.Adapter<VacationRequestsAdapter.VH> {

    // List holding vacation requests
    private final List<VacationRequest> items = new ArrayList<>();

    // Date formatter for displaying start/end dates
    private final SimpleDateFormat df =
            new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    /**
     * Replace current data with new list.
     */
    public void submit(List<VacationRequest> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged(); // full refresh
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate single vacation request layout
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_vacation_request, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        // Get current request
        VacationRequest r = items.get(position);

        // Store id as tag (optional use)
        h.itemView.setTag(r.getId());

        // Format start and end dates
        String start = (r.getStartDate() != null)
                ? df.format(r.getStartDate())
                : "";
        String end = (r.getEndDate() != null)
                ? df.format(r.getEndDate())
                : "";

        h.tvVacationDate.setText(start + " → " + end);

        // Show status (APPROVED / REJECTED / PENDING)
        String status = (r.getStatus() != null)
                ? r.getStatus().toString()
                : "";
        h.tvVacationStatus.setText(status);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /**
     * ViewHolder holding references to item views.
     */
    static class VH extends RecyclerView.ViewHolder {

        TextView tvVacationDate, tvVacationStatus;

        VH(@NonNull View itemView) {
            super(itemView);
            tvVacationDate = itemView.findViewById(R.id.tv_vacation_date);
            tvVacationStatus = itemView.findViewById(R.id.tv_vacation_status);
        }
    }
}