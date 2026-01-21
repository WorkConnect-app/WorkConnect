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

public class PendingVacationRequestsAdapter extends RecyclerView.Adapter<PendingVacationRequestsAdapter.VH> {

    public interface Listener {
        void onApprove(VacationRequest req);
        void onReject(VacationRequest req);
    }

    private final Listener listener;
    private final List<VacationRequest> items = new ArrayList<>();

    public PendingVacationRequestsAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<VacationRequest> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_pending_vacation_request, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        VacationRequest r = items.get(position);

        h.tvDates.setText((r.getStartDate() != null ? r.getStartDate() : "") +
                " → " + (r.getEndDate() != null ? r.getEndDate() : ""));
        h.tvReason.setText(r.getReason() != null ? r.getReason() : "");
        h.tvEmployee.setText(
                r.getEmployeeName() != null && !r.getEmployeeName().isEmpty()
                        ? r.getEmployeeName()
                        : r.getEmployeeEmail()
        );

        h.btnReject.setOnClickListener(v -> listener.onReject(r));
        h.btnApprove.setOnClickListener(v -> listener.onApprove(r));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

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