package com.example.workconnect.ui.shifts;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.workconnect.R;
import com.example.workconnect.adapters.shifts.ShiftTemplateAdapter;
import com.example.workconnect.models.ShiftTemplate;
import com.example.workconnect.models.Team;
import com.example.workconnect.repository.ShiftRepository;
import com.example.workconnect.repository.TeamRepository;

import java.util.ArrayList;
import java.util.List;

public class ManageShiftTemplatesActivity extends AppCompatActivity {

    private String companyId;

    private final TeamRepository teamRepo = new TeamRepository();
    private final ShiftRepository shiftRepo = new ShiftRepository();

    private final List<Team> cachedTeams = new ArrayList<>();

    private Spinner spinnerTeam;
    private RecyclerView rv;
    private Button btnAdd;
    private Button btnBack;

    private ShiftTemplateAdapter adapter;

    private String selectedTeamId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_shift_templates);

        companyId = getIntent().getStringExtra("companyId");
        if (companyId == null) companyId = "";

        spinnerTeam = findViewById(R.id.spinner_team_select);
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
                if (selectedTeamId == null) return;
                shiftRepo.deleteShiftTemplate(companyId, selectedTeamId, t.getId(), (success, msg) ->
                        Toast.makeText(ManageShiftTemplatesActivity.this, msg, Toast.LENGTH_LONG).show()
                );
            }

            @Override
            public void onToggleEnabled(ShiftTemplate t, boolean enabled) {
                if (selectedTeamId == null) return;
                t.setEnabled(enabled);
                shiftRepo.updateShiftTemplate(companyId, selectedTeamId, t, (success, msg) -> { });
            }
        });

        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());
        btnAdd.setOnClickListener(v -> showAddEditDialog(null));

        // Load teams for spinner
        teamRepo.getTeamsForCompany(companyId).observe(this, teams -> {
            cachedTeams.clear();
            if (teams != null) cachedTeams.addAll(teams);
            bindTeamsSpinner();
        });
    }

    private void bindTeamsSpinner() {
        List<String> labels = new ArrayList<>();
        labels.add("Select team");
        for (Team t : cachedTeams) labels.add(t.getName() == null ? "(Unnamed)" : t.getName());

        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTeam.setAdapter(a);

        spinnerTeam.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    selectedTeamId = null;
                    adapter.setItems(new ArrayList<>());
                    return;
                }
                Team t = cachedTeams.get(position - 1);
                selectedTeamId = t.getId();
                observeTemplatesForSelectedTeam();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
    }

    private void observeTemplatesForSelectedTeam() {
        if (selectedTeamId == null) return;
        shiftRepo.getShiftTemplates(companyId, selectedTeamId).observe(this, templates -> adapter.setItems(templates));
    }

    private void showAddEditDialog(ShiftTemplate existing) {
        if (selectedTeamId == null) {
            Toast.makeText(this, "Select a team first", Toast.LENGTH_LONG).show();
            return;
        }

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
                    shiftRepo.addShiftTemplate(companyId, selectedTeamId, t, (success, msg) ->
                            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    );
                } else {
                    existing.setTitle(title);
                    existing.setStartHour(start);
                    existing.setEndHour(end);
                    shiftRepo.updateShiftTemplate(companyId, selectedTeamId, existing, (success, msg) ->
                            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    );
                }

                dialog.dismiss();
            });
        });

        dialog.show();
    }
}
