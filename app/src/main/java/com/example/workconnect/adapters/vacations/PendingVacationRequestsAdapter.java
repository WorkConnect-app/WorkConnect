package com.example.workconnect.adapters.vacations;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.workconnect.R;
import com.example.workconnect.models.VacationRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for showing pending vacation requests (for manager approval).
 * Displays employee name, dates and reason, with approve/reject actions.
 */
public class PendingVacationRequestsAdapter
        extends RecyclerView.Adapter<PendingVacationRequestsAdapter.VH> {

    // Listener to handle approve/reject actions
    public interface Listener {
        void onApprove(VacationRequest req);
        void onReject(VacationRequest req);
    }

    private final Listener listener;

    // Local list holding current pending requests
    private final List<VacationRequest> items = new ArrayList<>();

    public PendingVacationRequestsAdapter(Listener listener) {
        this.listener = listener;
    }

    /**
     * Replace adapter data with new list of requests.
     */
    public void submit(List<VacationRequest> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged(); // simple full refresh
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate layout for single pending vacation request
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_pending_vacation_request, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        // Get current request
        VacationRequest r = items.get(position);

        // Store request id as tag (optional usage)
        h.itemView.setTag(r.getId());

        // Display date range (start → end)
        h.tvDates.setText(
                (r.getStartDate() != null ? r.getStartDate() : "") +
                        " → " +
                        (r.getEndDate() != null ? r.getEndDate() : "")
        );

        // Display reason (if exists)
        h.tvReason.setText(r.getReason() != null ? r.getReason() : "");

        // Prefer employee name, fallback to email if name missing
        h.tvEmployee.setText(
                r.getEmployeeName() != null && !r.getEmployeeName().isEmpty()
                        ? r.getEmployeeName()
                        : r.getEmployeeEmail()
        );

        // Approve button action
        h.btnApprove.setOnClickListener(v -> {
            if (listener != null) listener.onApprove(r);
        });

        // Reject button action
        h.btnReject.setOnClickListener(v -> {
            if (listener != null) listener.onReject(r);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /**
     * ViewHolder holding references to item views.
     */
    static class VH extends RecyclerView.ViewHolder {

        TextView tvEmployee, tvDates, tvReason;
        Button btnApprove, btnReject;

        VH(@NonNull View itemView) {
            super(itemView);
            tvEmployee = itemView.findViewById(R.id.tv_employee);
            tvDates = itemView.findViewById(R.id.tv_dates);
            tvReason = itemView.findViewById(R.id.tv_reason);
            btnApprove = itemView.findViewById(R.id.btn_approve);
            btnReject = itemView.findViewById(R.id.btn_reject);
        }
    }
}