package com.example.workconnect.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;


import com.example.workconnect.R;
import com.example.workconnect.models.User;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for displaying a list of pending employees in a RecyclerView.
 */
public class PendingEmployeesAdapter
        extends RecyclerView.Adapter<PendingEmployeesAdapter.PendingEmployeeViewHolder> {

    public interface OnEmployeeActionListener {
        void onApproveClicked(User employee);
        void onRejectClicked(User employee);
    }

    private final List<User> items = new ArrayList<>();
    private final OnEmployeeActionListener listener;

    public PendingEmployeesAdapter(OnEmployeeActionListener listener) {
        this.listener = listener;
    }

    public void setItems(List<User> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PendingEmployeeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_pending_employee_activity, parent, false);
        return new PendingEmployeeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PendingEmployeeViewHolder holder, int position) {
        User employee = items.get(position);
        holder.bind(employee, listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class PendingEmployeeViewHolder extends RecyclerView.ViewHolder {

        TextView tvName, tvEmail;
        Button btnApprove, btnReject;

        public PendingEmployeeViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_employee_name);
            tvEmail = itemView.findViewById(R.id.tv_employee_email);
            btnApprove = itemView.findViewById(R.id.btn_approve);
            btnReject = itemView.findViewById(R.id.btn_reject);
        }

        void bind(User employee, OnEmployeeActionListener listener) {
            tvName.setText(employee.getFullName());
            tvEmail.setText(employee.getEmail());

            btnApprove.setOnClickListener(v -> {
                if (listener != null) listener.onApproveClicked(employee);
            });

            btnReject.setOnClickListener(v -> {
                if (listener != null) listener.onRejectClicked(employee);
            });
        }
    }
}
