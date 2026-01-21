package com.example.workconnect.adapters;

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

public class SwapApprovalsAdapter extends RecyclerView.Adapter<SwapApprovalsAdapter.VH> {

    public interface Listener {
        void onApprove(ShiftSwapRequest r);
        void onReject(ShiftSwapRequest r);
    }

    private final Listener listener;
    private final List<ShiftSwapRequest> items = new ArrayList<>();

    public SwapApprovalsAdapter(Listener l) { this.listener = l; }

    public void setItems(List<ShiftSwapRequest> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_swap_pending, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        ShiftSwapRequest r = items.get(position);

        h.tvTitle.setText(r.getDateKey() + " / " + r.getTemplateTitle());
        h.tvSub.setText("By: " + (r.getRequesterName() == null ? "Unknown" : r.getRequesterName())
                + " • " + r.getType());

        h.btnApprove.setOnClickListener(v -> listener.onApprove(r));
        h.btnReject.setOnClickListener(v -> listener.onReject(r));
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvSub;
        Button btnApprove, btnReject;

        VH(@NonNull View v) {
            super(v);
            tvTitle = v.findViewById(R.id.tv_title);
            tvSub = v.findViewById(R.id.tv_sub);
            btnApprove = v.findViewById(R.id.btn_approve);
            btnReject = v.findViewById(R.id.btn_reject);
        }
    }
}
