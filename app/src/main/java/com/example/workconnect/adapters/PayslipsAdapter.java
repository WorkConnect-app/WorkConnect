package com.example.workconnect.adapters;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.workconnect.R;
import com.example.workconnect.models.Payslip;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class PayslipsAdapter extends RecyclerView.Adapter<PayslipsAdapter.VH> {

    private final Context context;
    private final List<Payslip> items = new ArrayList<>();

    public PayslipsAdapter(Context context) {
        this.context = context;
    }

    public void submit(List<Payslip> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_payslip, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Payslip p = items.get(position);

        String label = (p.getPrettyLabel() != null) ? p.getPrettyLabel() : (p.getPeriodKey() != null ? p.getPeriodKey() : "-");
        h.txtPeriod.setText(label);

        h.btnDownload.setEnabled(p.getDownloadUrl() != null && !p.getDownloadUrl().trim().isEmpty());

        h.btnDownload.setOnClickListener(v -> {
            if (p.getDownloadUrl() == null || p.getDownloadUrl().trim().isEmpty()) return;

            String fileName = p.getFileName();
            if (fileName == null || fileName.trim().isEmpty()) {
                fileName = label + ".pdf";
            }

            DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) return;

            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(p.getDownloadUrl()));
            req.setTitle("Salary slip " + label);
            req.setDescription("Downloading PDF");
            req.setMimeType("application/pdf");
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setAllowedOverMetered(true);
            req.setAllowedOverRoaming(true);

            // No special permission needed on modern Android for Downloads
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

            dm.enqueue(req);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView txtPeriod;
        MaterialButton btnDownload;

        VH(@NonNull View itemView) {
            super(itemView);
            txtPeriod = itemView.findViewById(R.id.txt_period);
            btnDownload = itemView.findViewById(R.id.btn_download);
        }
    }
}