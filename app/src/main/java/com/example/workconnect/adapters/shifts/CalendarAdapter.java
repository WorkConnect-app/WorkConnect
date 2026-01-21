package com.example.workconnect.adapters.shifts;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.workconnect.R;

import java.util.ArrayList;
import java.util.List;

public class CalendarAdapter extends RecyclerView.Adapter<CalendarAdapter.VH> {

    public static class DayCell {
        public boolean isPlaceholder;   // true = empty padding cell (not clickable)
        public String dateKey;          // yyyy-MM-dd (null for placeholder)
        public int dayNumber;           // 1..31 (0 for placeholder)
        public String summary;          // optional text (availability)
        public boolean inMonth;         // for monthly grids (dim prev/next month cells)
        public Integer badgeCount;      // optional (manager)

        // Availability style constructor (matches your MyAvailabilityActivity usage)
        public DayCell(boolean isPlaceholder, String dateKey, int dayNumber, String summary) {
            this.isPlaceholder = isPlaceholder;
            this.dateKey = dateKey;
            this.dayNumber = dayNumber;
            this.summary = summary;
            this.inMonth = true;
            this.badgeCount = null;
        }

        // Manager month-grid style constructor (prev/next month cells are dimmed, but still real dates)
        public DayCell(String dateKey, int dayNumber, boolean inMonth) {
            this.isPlaceholder = false;
            this.dateKey = dateKey;
            this.dayNumber = dayNumber;
            this.summary = "";
            this.inMonth = inMonth;
            this.badgeCount = null;
        }
    }

    public interface Listener {
        void onDayClicked(DayCell cell);
    }

    private final Listener listener;
    private final List<DayCell> items = new ArrayList<>();

    public CalendarAdapter(Listener listener) {
        this.listener = listener;
    }

    // Compatibility with your activity code
    public void setItems(List<DayCell> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    // Compatibility if any older code uses setDays / setCells
    public void setDays(List<DayCell> list) { setItems(list); }
    public void setCells(List<DayCell> list) { setItems(list); }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_calendar_day, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        DayCell c = items.get(position);

        if (c == null || c.isPlaceholder || c.dateKey == null) {
            h.tvDayNumber.setText("");
            h.itemView.setAlpha(0f);
            h.itemView.setOnClickListener(null);
            h.tvSummary.setVisibility(View.GONE);
            h.tvBadge.setVisibility(View.GONE);
            return;
        }

        h.tvDayNumber.setText(String.valueOf(c.dayNumber));

        // dim previous/next month cells in month view
        h.itemView.setAlpha(c.inMonth ? 1.0f : 0.35f);

        // summary (availability calendar)
        if (c.summary != null && !c.summary.trim().isEmpty()) {
            h.tvSummary.setText(c.summary);
            h.tvSummary.setVisibility(View.VISIBLE);
        } else {
            h.tvSummary.setVisibility(View.GONE);
        }

        // badge (manager)
        if (c.badgeCount != null && c.badgeCount > 0) {
            h.tvBadge.setText(String.valueOf(c.badgeCount));
            h.tvBadge.setVisibility(View.VISIBLE);
        } else {
            h.tvBadge.setVisibility(View.GONE);
        }

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onDayClicked(c);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvDayNumber;
        TextView tvSummary;
        TextView tvBadge;

        VH(@NonNull View itemView) {
            super(itemView);
            tvDayNumber = itemView.findViewById(R.id.tv_day_number);
            tvSummary = itemView.findViewById(R.id.tv_summary);
            tvBadge = itemView.findViewById(R.id.tv_badge);
        }
    }
}
