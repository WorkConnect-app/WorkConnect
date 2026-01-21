package com.example.workconnect.adapters.shifts;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.workconnect.R;
import com.example.workconnect.models.ShiftSwapRequest;

import java.util.ArrayList;
import java.util.List;

public class MyRequestsAdapter extends RecyclerView.Adapter<MyRequestsAdapter.VH> {

    public interface Listener {
        void onCancel(ShiftSwapRequest r);
        void onDetails(ShiftSwapRequest r);
    }

    private final Listener listener;
    private final List<ShiftSwapRequest> items = new ArrayList<>();

    public MyRequestsAdapter(Listener l) { this.listener = l; }

    public void setItems(List<ShiftSwapRequest> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_swap_request_my, parent, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int position) {
        ShiftSwapRequest r = items.get(position);

        h.tvTitle.setText(r.getDateKey() + " / " + r.getTemplateTitle() + " (" + r.getType() + ")");
        h.tvStatus.setText("Status: " + r.getStatus());

        h.btnDetails.setOnClickListener(v -> listener.onDetails(r));

        boolean canCancel = ShiftSwapRequest.OPEN.equals(r.getStatus());
        h.btnCancel.setEnabled(canCancel);
        h.btnCancel.setAlpha(canCancel ? 1f : 0.4f);
        h.btnCancel.setOnClickListener(v -> listener.onCancel(r));
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvStatus;
        Button btnDetails, btnCancel;

        VH(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvStatus = itemView.findViewById(R.id.tv_status);
            btnDetails = itemView.findViewById(R.id.btn_details);
            btnCancel = itemView.findViewById(R.id.btn_cancel);
        }
    }
}
