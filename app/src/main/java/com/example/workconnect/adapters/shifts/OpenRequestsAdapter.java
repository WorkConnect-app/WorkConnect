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

public class OpenRequestsAdapter extends RecyclerView.Adapter<OpenRequestsAdapter.VH> {

    public interface Listener {
        void onOffer(ShiftSwapRequest r);
    }

    private final Listener listener;
    private final List<ShiftSwapRequest> items = new ArrayList<>();

    public OpenRequestsAdapter(Listener l) { this.listener = l; }

    public void setItems(List<ShiftSwapRequest> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_swap_request_open, parent, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int position) {
        ShiftSwapRequest r = items.get(position);
        h.tvTitle.setText(r.getDateKey() + " / " + r.getTemplateTitle());
        h.tvSub.setText("By: " + (r.getRequesterName() == null ? "Unknown" : r.getRequesterName()) + " • Type: " + r.getType());
        h.btnOffer.setOnClickListener(v -> listener.onOffer(r));
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvSub;
        Button btnOffer;

        VH(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvSub = itemView.findViewById(R.id.tv_sub);
            btnOffer = itemView.findViewById(R.id.btn_offer);
        }
    }
}
