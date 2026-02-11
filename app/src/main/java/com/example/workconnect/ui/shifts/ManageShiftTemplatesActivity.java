package com.example.workconnect.ui.shifts;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.workconnect.R;
import com.example.workconnect.adapters.shifts.ShiftTemplateAdapter;
import com.example.workconnect.models.ShiftTemplate;
import com.example.workconnect.repository.ShiftRepository;

import java.util.ArrayList;
import java.util.List;

public class ManageShiftTemplatesActivity extends AppCompatActivity {

    private String companyId = "";
    private String teamId = "";

    private String teamNameExtra  = "";
    private final ShiftRepository shiftRepo = new ShiftRepository();

    private RecyclerView rv;
    private Button btnAdd;
    private Button btnBack;
    private TextView tvTitle;

    private ShiftTemplateAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_shift_templates);

        companyId = getIntent().getStringExtra("companyId");
        if (companyId == null) companyId = "";
        companyId = companyId.trim();

        teamId = getIntent().getStringExtra("teamId");
        if (teamId == null) teamId = "";
        teamId = teamId.trim();

        teamNameExtra = getIntent().getStringExtra("teamName");
        if (teamNameExtra == null) teamNameExtra = "";
        teamNameExtra = teamNameExtra.trim();


        if (companyId.isEmpty() || teamId.isEmpty()) {
            Toast.makeText(this, "Missing companyId/teamId", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        tvTitle = findViewById(R.id.tv_title);
        rv = findViewById(R.id.rv_templates);
        btnAdd = findViewById(R.id.btn_add_template);
        btnBack = findViewById(R.id.btn_back);

        adapter = new ShiftTemplateAdapter(new ShiftTemplateAdapter.Listener() {
            @Override
            public void onEdit(ShiftTemplate t) {
                showAddEditDialog(t);
            }

            @Override
            public void onDelete(ShiftTemplate t) {
                if (t == null || t.getId() == null) return;

                shiftRepo.deleteShiftTemplate(companyId, teamId, t.getId(), (success, msg) ->
                        Toast.makeText(ManageShiftTemplatesActivity.this, msg, Toast.LENGTH_LONG).show()
                );
            }

            @Override
            public void onToggleEnabled(ShiftTemplate t, boolean enabled) {
                if (t == null) return;

                boolean old = t.isEnabled();
                t.setEnabled(enabled);

                shiftRepo.updateShiftTemplate(companyId, teamId, t, (success, msg) -> {
                    if (!success) {
                        // revert UI + model if failed
                        t.setEnabled(old);
                        Toast.makeText(ManageShiftTemplatesActivity.this, msg, Toast.LENGTH_SHORT).show();
                        adapter.notifyDataSetChanged();
                    }
                });
            }
        });

        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());
        btnAdd.setOnClickListener(v -> showAddEditDialog(null));

        if (teamNameExtra != null && !teamNameExtra.trim().isEmpty())
            tvTitle.setText("Shift templates (" + teamNameExtra.trim() + ")");


        // Listen templates for THIS team directly
        shiftRepo.getShiftTemplates(companyId, teamId)
                .observe(this, templates -> adapter.setItems(templates));
    }

    private void showAddEditDialog(ShiftTemplate existing) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_shift_template, null);

        EditText etTitle = view.findViewById(R.id.et_title);
        Spinner spStart = view.findViewById(R.id.spinner_start);
        Spinner spEnd = view.findViewById(R.id.spinner_end);

        List<String> hours = new ArrayList<>();
        for (int i = 0; i < 24; i++) hours.add(String.valueOf(i));

        ArrayAdapter<String> hoursAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, hours);
        hoursAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spStart.setAdapter(hoursAdapter);
        spEnd.setAdapter(hoursAdapter);

        boolean isEdit = existing != null;

        if (isEdit) {
            etTitle.setText(existing.getTitle());
            spStart.setSelection(Math.max(0, existing.getStartHour()));
            spEnd.setSelection(Math.max(0, existing.getEndHour()));
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(isEdit ? "Edit shift template" : "Add shift template")
                .setView(view)
                .setPositiveButton(isEdit ? "Save" : "Add", null)
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .create();

        dialog.setOnShowListener(d -> {
            Button b = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            b.setOnClickListener(v -> {
                String title = etTitle.getText() == null ? "" : etTitle.getText().toString().trim();
                if (title.isEmpty()) {
                    Toast.makeText(this, "Title is required", Toast.LENGTH_LONG).show();
                    return;
                }

                int start = Integer.parseInt((String) spStart.getSelectedItem());
                int end = Integer.parseInt((String) spEnd.getSelectedItem());

                if (start == end) {
                    Toast.makeText(this, "Start and end cannot be the same", Toast.LENGTH_LONG).show();
                    return;
                }

                if (!isEdit) {
                    ShiftTemplate t = new ShiftTemplate(null, title, start, end, true);
                    shiftRepo.addShiftTemplate(companyId, teamId, t, (success, msg) ->
                            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    );
                } else {
                    existing.setTitle(title);
                    existing.setStartHour(start);
                    existing.setEndHour(end);
                    shiftRepo.updateShiftTemplate(companyId, teamId, existing, (success, msg) ->
                            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    );
                }

                dialog.dismiss();
            });
        });

        dialog.show();
    }
}
