package com.example.workconnect.ui.payslips;

import android.app.AlertDialog;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.workconnect.R;
import com.example.workconnect.models.Payslip;
import com.example.workconnect.repository.payslips.PayslipRepository;
import com.example.workconnect.ui.home.BaseDrawerActivity;
import com.example.workconnect.adapters.payslips.PayslipsManagerAdapter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import java.util.*;

public class UploadSalarySlipsActivity extends BaseDrawerActivity {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final PayslipRepository payslipRepo = new PayslipRepository();

    private TextInputLayout tilEmployee;
    private MaterialAutoCompleteTextView actEmployee;

    private RecyclerView rvPayslips;
    private TextView tvEmpty;
    private MaterialButton btnUploadNew;

    private PayslipsManagerAdapter adapter;
    private ListenerRegistration payslipListener;

    // employee picker
    private final List<EmployeeOption> employeeOptions = new ArrayList<>();
    private ArrayAdapter<EmployeeOption> employeeAdapter;

    private @Nullable String selectedEmployeeUid = null;
    private @Nullable String selectedEmployeeLabel = null;

    private Uri pickedPdfUri = null;
    private String pickedPdfName = null;

    private final ActivityResultLauncher<String[]> pickPdfLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;
                pickedPdfUri = uri;

                // Persist permission so upload works after rotation etc.
                try {
                    getContentResolver().takePersistableUriPermission(uri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {}

                pickedPdfName = queryFileName(uri);
                Toast.makeText(this, "Selected: " + (pickedPdfName == null ? "PDF" : pickedPdfName), Toast.LENGTH_SHORT).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload_salary_slips);

        tilEmployee = findViewById(R.id.til_employee);
        actEmployee = findViewById(R.id.act_employee);

        rvPayslips = findViewById(R.id.rv_manager_payslips);
        tvEmpty = findViewById(R.id.tv_manager_payslips_empty);
        btnUploadNew = findViewById(R.id.btn_upload_new_payslip);

        adapter = new PayslipsManagerAdapter(this,
                payslip -> {
                    // download
                    boolean ok = PayslipsManagerAdapter.downloadPdf(this, payslip);
                    Toast.makeText(this, ok ? "Downloaded" : "Download failed", Toast.LENGTH_SHORT).show();
                },
                payslip -> {
                    // delete
                    confirmDelete(payslip);
                });

        rvPayslips.setLayoutManager(new LinearLayoutManager(this));
        rvPayslips.setAdapter(adapter);

        employeeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        actEmployee.setAdapter(employeeAdapter);
        actEmployee.setOnClickListener(v -> actEmployee.showDropDown());

        actEmployee.setOnItemClickListener((parent, view, position, id) -> {
            EmployeeOption opt = employeeAdapter.getItem(position);
            if (opt == null) {
                Toast.makeText(this, "Failed to select employee.", Toast.LENGTH_SHORT).show();
                selectedEmployeeUid = null;
                selectedEmployeeLabel = null;
                clearPayslipsUI();
                return;
            }

            selectedEmployeeUid = opt.uid;
            selectedEmployeeLabel = opt.label;

            attachPayslipListener(selectedEmployeeUid);
        });

        btnUploadNew.setOnClickListener(v -> {
            if (selectedEmployeeUid == null) {
                Toast.makeText(this, "Choose an employee first", Toast.LENGTH_SHORT).show();
                return;
            }
            showUploadDialog();
        });

        // Wait until BaseDrawerActivity caches company/user state
        // then load employees
        tryLoadEmployees();
    }

    @Override
    protected void onCompanyStateLoaded() {
        super.onCompanyStateLoaded();
        tryLoadEmployees();
    }

    private void tryLoadEmployees() {
        if (cachedCompanyId == null || cachedCompanyId.trim().isEmpty()) return;

        // optional guard: only managers
        if (!cachedIsManager) {
            Toast.makeText(this, "Managers only", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadCompanyEmployees(cachedCompanyId);
    }

    private void loadCompanyEmployees(String companyId) {
        db.collection("users")
                .whereEqualTo("companyId", companyId)
                .whereEqualTo("status", "APPROVED")
                .get()
                .addOnSuccessListener(qs -> {
                    employeeOptions.clear();

                    for (DocumentSnapshot d : qs.getDocuments()) {
                        String uid = d.getId();
                        String fullName = safe(d.getString("fullName"));
                        String email = safe(d.getString("email"));

                        String label;
                        if (!fullName.equals("-") && !email.equals("-")) label = fullName + " (" + email + ")";
                        else if (!fullName.equals("-")) label = fullName;
                        else if (!email.equals("-")) label = email;
                        else label = uid;

                        employeeOptions.add(new EmployeeOption(uid, label));
                    }

                    // sort alphabetically by label
                    Collections.sort(employeeOptions, Comparator.comparing(o -> o.label.toLowerCase(Locale.US)));

                    employeeAdapter.clear();
                    employeeAdapter.addAll(employeeOptions);
                    employeeAdapter.notifyDataSetChanged();

                    if (employeeOptions.isEmpty()) {
                        Toast.makeText(this, "No employees found", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load employees: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void attachPayslipListener(String employeeUid) {
        detachPayslipListener(); // prevent multiple active listeners

        payslipListener = payslipRepo.listenPayslips(employeeUid, new PayslipRepository.PayslipListCallback() {
            @Override
            public void onUpdate(List<Payslip> payslips) {
                adapter.submit(payslips);
                boolean empty = payslips == null || payslips.isEmpty();
                tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                rvPayslips.setVisibility(empty ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onError(Exception e) {
                tvEmpty.setVisibility(View.VISIBLE);
                rvPayslips.setVisibility(View.GONE);
            }
        });
    }

    private void detachPayslipListener() {
        if (payslipListener != null) {
            payslipListener.remove();
            payslipListener = null;
        }
    }

    private void clearPayslipsUI() {
        adapter.submit(new ArrayList<>());
        tvEmpty.setVisibility(View.VISIBLE);
        rvPayslips.setVisibility(View.GONE);
    }

    private void confirmDelete(Payslip p) {
        new AlertDialog.Builder(this)
                .setTitle("Delete payslip")
                .setMessage("Delete salary slip " + p.getPrettyLabel() + " for " + (selectedEmployeeLabel == null ? "this employee" : selectedEmployeeLabel) + "?")
                .setPositiveButton("Delete", (d, w) -> {
                    payslipRepo.deletePayslip(p, new PayslipRepository.PayslipActionCallback() {
                        @Override
                        public void onComplete(PayslipRepository.Result result) {
                            if (result == PayslipRepository.Result.DELETED) {
                                Toast.makeText(UploadSalarySlipsActivity.this, "Deleted", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(UploadSalarySlipsActivity.this, "Delete failed", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onError(Exception e) {
                            Toast.makeText(UploadSalarySlipsActivity.this, "Delete failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showUploadDialog() {
        pickedPdfUri = null;
        pickedPdfName = null;

        Calendar cal = Calendar.getInstance();
        int defaultMonth = cal.get(Calendar.MONTH) + 1;
        int defaultYear = cal.get(Calendar.YEAR);

        View view = getLayoutInflater().inflate(R.layout.dialog_upload_payslip, null);
        MaterialAutoCompleteTextView actMonth = view.findViewById(R.id.act_month);
        EditText etYear = view.findViewById(R.id.et_year);
        MaterialButton btnChoosePdf = view.findViewById(R.id.btn_choose_pdf);
        TextView tvChosen = view.findViewById(R.id.tv_chosen_pdf);

        // Month dropdown
        List<String> months = new ArrayList<>();
        for (int i = 1; i <= 12; i++) months.add(String.valueOf(i));
        ArrayAdapter<String> mAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, months);
        actMonth.setAdapter(mAdapter);
        actMonth.setText(String.valueOf(defaultMonth), false);

        etYear.setInputType(InputType.TYPE_CLASS_NUMBER);
        etYear.setText(String.valueOf(defaultYear));

        btnChoosePdf.setOnClickListener(v -> {
            pickPdfLauncher.launch(new String[]{"application/pdf"});
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Upload salary slip")
                .setView(view)
                .setPositiveButton("Upload", null) // override below
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String mStr = actMonth.getText() == null ? "" : actMonth.getText().toString().trim();
                String yStr = etYear.getText() == null ? "" : etYear.getText().toString().trim();

                int month, year;
                try {
                    month = Integer.parseInt(mStr);
                    year = Integer.parseInt(yStr);
                } catch (Exception ex) {
                    Toast.makeText(this, "Enter valid month/year", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (month < 1 || month > 12) {
                    Toast.makeText(this, "Month must be 1-12", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (year < 2000 || year > 2100) {
                    Toast.makeText(this, "Year must be 2000-2100", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (pickedPdfUri == null) {
                    Toast.makeText(this, "Choose a PDF first", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (selectedEmployeeUid == null || cachedCompanyId == null) {
                    Toast.makeText(this, "Employee/company not ready", Toast.LENGTH_SHORT).show();
                    return;
                }

                String uploaderUid = FirebaseAuth.getInstance().getUid();
                if (uploaderUid == null) {
                    Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show();
                    return;
                }

                // update chosen text if we have it
                tvChosen.setText(pickedPdfName == null ? "PDF selected" : pickedPdfName);

                final int MAX_PDF_BYTES = 700 * 1024; // ~700KB raw pdf (safe under Firestore 1MB after base64)

                byte[] pdfBytes;
                try {
                    pdfBytes = readBytesWithLimit(pickedPdfUri, MAX_PDF_BYTES);
                    if (pdfBytes == null) {
                        Toast.makeText(this, "PDF is too large (max ~700KB on free plan).", Toast.LENGTH_LONG).show();
                        return;
                    }
                } catch (Exception ex) {
                    Toast.makeText(this, "Failed to read PDF: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
                    return;
                }

                String base64 = Base64.encodeToString(pdfBytes, Base64.NO_WRAP);

                payslipRepo.uploadPayslipBase64(
                        selectedEmployeeUid,
                        cachedCompanyId,
                        year,
                        month,
                        base64,
                        pdfBytes.length,
                        pickedPdfName,
                        uploaderUid,
                        new PayslipRepository.PayslipActionCallback() {
                            @Override
                            public void onComplete(PayslipRepository.Result result) {
                                if (result == PayslipRepository.Result.ALREADY_EXISTS) {
                                    Toast.makeText(UploadSalarySlipsActivity.this,
                                            "Payslip already exists for " + String.format(Locale.US, "%04d-%02d", year, month),
                                            Toast.LENGTH_LONG).show();
                                } else if (result == PayslipRepository.Result.UPLOADED) {
                                    Toast.makeText(UploadSalarySlipsActivity.this,
                                            "Uploaded for " + String.format(Locale.US, "%04d-%02d", year, month),
                                            Toast.LENGTH_SHORT).show();
                                    dialog.dismiss();
                                } else {
                                    Toast.makeText(UploadSalarySlipsActivity.this, "Upload failed", Toast.LENGTH_SHORT).show();
                                }
                            }

                            @Override
                            public void onError(Exception e) {
                                Toast.makeText(UploadSalarySlipsActivity.this, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        }
                );
            });
        });

        dialog.show();

        // update chosen label after picker: simplest approach is polling on resume of dialog is messy.
        // We'll just show selection toast + keep internal vars.
    }

    private String safe(String s) {
        if (s == null) return "-";
        String t = s.trim();
        return t.isEmpty() ? "-" : t;
    }

    private @Nullable String queryFileName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor == null) return null;
            int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (nameIndex < 0) return null;
            if (cursor.moveToFirst()) return cursor.getString(nameIndex);
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        detachPayslipListener();
    }

    private static class EmployeeOption {
        final String uid;
        final String label;

        EmployeeOption(String uid, String label) {
            this.uid = uid;
            this.label = label;
        }

        @Override
        public String toString() {
            return label; // what the dropdown displays
        }
    }
    private byte[] readBytesWithLimit(Uri uri, int maxBytes) throws Exception {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) return null;

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            int read;
            int total = 0;

            while ((read = is.read(buf)) != -1) {
                total += read;
                if (total > maxBytes) return null; // too big
                bos.write(buf, 0, read);
            }
            return bos.toByteArray();
        }
    }
}