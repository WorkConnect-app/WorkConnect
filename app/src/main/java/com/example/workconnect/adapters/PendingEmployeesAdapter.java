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
 * Adapter for displaying pending employee accounts waiting for manager approval.
 * Only responsible for binding UI and forwarding clicks to the hosting screen.
 */
public class PendingEmployeesAdapter
        extends RecyclerView.Adapter<PendingEmployeesAdapter.PendingEmployeeViewHolder> {

    /**
     * Listener for approve / reject actions on a pending employee.
     * Implemented by the hosting Activity/Fragment.
     */
    public interface OnEmployeeActionListener {
        void onApproveClicked(User employee);
        void onRejectClicked(User employee);
    }

    // Keep a non-null list to avoid null checks and potential crashes.
    private final List<User> employees = new ArrayList<>();

    // Listener provided by UI layer to handle user actions.
    private final OnEmployeeActionListener listener;

    // Used to disable approve/reject buttons while an operation is running.
    private boolean buttonsEnabled = true;

    public PendingEmployeesAdapter(@NonNull OnEmployeeActionListener listener) {
        this.listener = listener;
    }

    /**
     * Enables/disables action buttons to prevent double clicks while processing.
     */
    public void setButtonsEnabled(boolean enabled) {
        this.buttonsEnabled = enabled;
        notifyDataSetChanged(); // simple approach; good enough for this screen
    }

    /**
     * Updates the list of pending employees.
     */
    public void setEmployees(List<User> newEmployees) {
        employees.clear();
        if (newEmployees != null) {
            employees.addAll(newEmployees);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PendingEmployeeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate a row layout for a pending employee item
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_pending_employee, parent, false);
        return new PendingEmployeeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PendingEmployeeViewHolder holder, int position) {
        User employee = employees.get(position);
        holder.bind(employee, listener, buttonsEnabled);
    }

    @Override
    public int getItemCount() {
        return employees.size();
    }

    /**
     * Each ViewHolder represents one employee in the list.
     */
    static class PendingEmployeeViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvName;
        private final TextView tvEmail;
        private final Button btnApprove;
        private final Button btnReject;

        public PendingEmployeeViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_employee_name);
            tvEmail = itemView.findViewById(R.id.tv_employee_email);
            btnApprove = itemView.findViewById(R.id.btn_approve);
            btnReject = itemView.findViewById(R.id.btn_reject);
        }

        /**
         * Binds employee data and forwards button clicks to the listener.
         */
        void bind(@NonNull User employee, OnEmployeeActionListener listener, boolean buttonsEnabled) {
            // Display full name
            String firstName = employee.getFirstName() != null ? employee.getFirstName() : "";
            String lastName = employee.getLastName() != null ? employee.getLastName() : "";
            tvName.setText((firstName + " " + lastName).trim());

            // Display email
            tvEmail.setText(employee.getEmail() != null ? employee.getEmail() : "");

            // Disable buttons if a Firestore action is currently running
            btnApprove.setEnabled(buttonsEnabled);
            btnReject.setEnabled(buttonsEnabled);

            // Forward clicks
            btnApprove.setOnClickListener(v -> listener.onApproveClicked(employee));
            btnReject.setOnClickListener(v -> listener.onRejectClicked(employee));
        }
    }
}
