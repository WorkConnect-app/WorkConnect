package com.example.workconnect.adapters.payslips;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.workconnect.R;
import com.example.workconnect.models.Payslip;
import com.google.android.material.button.MaterialButton;

import android.util.Base64;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class PayslipsManagerAdapter extends RecyclerView.Adapter<PayslipsManagerAdapter.VH> {

    public interface OnPayslipClick {
        void onClick(Payslip payslip);
    }

    private final Context context;
    private final List<Payslip> items = new ArrayList<>();
    private final OnPayslipClick onDownload;
    private final OnPayslipClick onDelete;

    public PayslipsManagerAdapter(Context context, OnPayslipClick onDownload, OnPayslipClick onDelete) {
        this.context = context;
        this.onDownload = onDownload;
        this.onDelete = onDelete;
    }

    public void submit(List<Payslip> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_payslip_manager, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Payslip p = items.get(position);
        String label = p.getPrettyLabel() != null ? p.getPrettyLabel() : (p.getPeriodKey() != null ? p.getPeriodKey() : "-");
        h.txtPeriod.setText(label);

        boolean canDownload = p.getPdfBase64() != null && !p.getPdfBase64().trim().isEmpty();
        h.btnDownload.setEnabled(canDownload);

        h.btnDownload.setOnClickListener(v -> onDownload.onClick(p));
        h.btnDelete.setOnClickListener(v -> onDelete.onClick(p));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView txtPeriod;
        MaterialButton btnDownload, btnDelete;

        VH(@NonNull View itemView) {
            super(itemView);
            txtPeriod = itemView.findViewById(R.id.txt_period);
            btnDownload = itemView.findViewById(R.id.btn_download);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }
    }

    public static boolean downloadPdf(Context context, Payslip p) {
        if (p == null || p.getPdfBase64() == null || p.getPdfBase64().trim().isEmpty()) return false;

        String label = p.getPrettyLabel() != null ? p.getPrettyLabel() : (p.getPeriodKey() != null ? p.getPeriodKey() : "payslip");
        String fileName = p.getFileName();
        if (fileName == null || fileName.trim().isEmpty()) fileName = label + ".pdf";
        if (!fileName.toLowerCase().endsWith(".pdf")) fileName = fileName + ".pdf";

        try {
            byte[] bytes = Base64.decode(p.getPdfBase64(), Base64.DEFAULT);
            return writePdfToDownloads(context, fileName, bytes);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean writePdfToDownloads(Context context, String fileName, byte[] bytes) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentResolver resolver = context.getContentResolver();

                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) return false;

                try (OutputStream os = resolver.openOutputStream(uri)) {
                    if (os == null) return false;
                    os.write(bytes);
                    os.flush();
                }
                return true;
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) dir.mkdirs();
                File out = new File(dir, fileName);
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    fos.write(bytes);
                    fos.flush();
                }
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }
}