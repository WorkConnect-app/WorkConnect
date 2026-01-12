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
 * Adapter for displaying pending employee accounts
 * waiting for manager approval.
 */

public class PendingEmployeesAdapter
        extends RecyclerView.Adapter<PendingEmployeesAdapter.PendingEmployeeViewHolder> {

    /**
     * Listener for approve / reject actions on a pending employee.
     */

    public interface OnEmployeeActionListener {
        void onApproveClicked(User employee);
        void onRejectClicked(User employee);
    }

    // Always keep a non-null list to avoid null checks and potential crashes.
    private final List<User> employees = new ArrayList<>();

    // Listener is provided by the hosting UI layer to handle actions.
    private final OnEmployeeActionListener listener;

    public PendingEmployeesAdapter(@NonNull OnEmployeeActionListener listener) {
        this.listener = listener;
    }

    /**
     * Updates the list of pending employees.
     * Each call to setEmployees represents the current state of the data.
     */
    public void setEmployees(List<User> newEmployees) {
        employees.clear();
        if (newEmployees != null) {
            employees.addAll(newEmployees);
        }
        notifyDataSetChanged(); // The data has changed - redraw the list.
    }


    // Creates a new row from the XML and returns a ViewHolder for reuse in the list.
    @NonNull
    @Override
    public PendingEmployeeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_pending_employee, parent, false);
        return new PendingEmployeeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PendingEmployeeViewHolder holder, int position) {
        // Bind the employee data to the row views.
        User employee = employees.get(position);
        holder.bind(employee, listener);
    }

    @Override
    public int getItemCount() {
        return employees.size();
    }

    /**
     * ViewHolder for a single pending employee row.
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
         * Binds employee data and handles button clicks.
         */
        void bind(@NonNull User employee, OnEmployeeActionListener listener) {
            // Display full name and email.
            String firstName = employee.getFirstName() != null ? employee.getFirstName() : "";
            String lastName = employee.getLastName() != null ? employee.getLastName() : "";
            tvName.setText((firstName + " " + lastName).trim());

            tvEmail.setText(employee.getEmail() != null ? employee.getEmail() : "");

            // Forward button clicks to the hosting screen via the listener.
            btnApprove.setOnClickListener(v -> listener.onApproveClicked(employee));
            btnReject.setOnClickListener(v -> listener.onRejectClicked(employee));
        }
    }
}
