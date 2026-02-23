package com.example.workconnect.ui.auth;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.workconnect.R;
import com.example.workconnect.models.ShiftTemplate;
import com.example.workconnect.models.Team;
import com.example.workconnect.models.User;
import com.example.workconnect.repository.authAndUsers.TeamRepository;
import com.example.workconnect.ui.shifts.ManageShiftTemplatesActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EditTeamActivity extends AppCompatActivity {

    private String companyId = "";
    private String teamId = "";

    private Button btnBack, btnSaveName, btnEditMembers, btnManageTemplates;
    private Button btnEditFullTimeTemplate;
    private TextInputEditText etTeamName;

    // NEW
    private Spinner spinnerPeriodType;
    private Button btnSavePeriodType;

    private final TeamRepository teamRepo = new TeamRepository();

    private Team currentTeam = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_team);

        companyId = getIntent().getStringExtra("companyId");
        teamId = getIntent().getStringExtra("teamId");
        if (companyId == null) companyId = "";
        if (teamId == null) teamId = "";

        btnBack = findViewById(R.id.btn_back);
        btnSaveName = findViewById(R.id.btn_save_name);
        btnEditMembers = findViewById(R.id.btn_edit_members);
        btnManageTemplates = findViewById(R.id.btn_manage_templates);
        etTeamName = findViewById(R.id.et_team_name);

        btnEditFullTimeTemplate = findViewById(R.id.btn_edit_full_time_template);

        // NEW
        spinnerPeriodType = findViewById(R.id.spinner_period_type);
        btnSavePeriodType = findViewById(R.id.btn_save_period_type);

        btnBack.setOnClickListener(v -> finish());

        bindPeriodTypeSpinner();

        FirebaseFirestore.getInstance()
                .collection("companies").document(companyId)
                .collection("teams").document(teamId)
                .addSnapshotListener((doc, e) -> {
                    if (e != null || doc == null || !doc.exists()) return;
                    Team t = doc.toObject(Team.class);
                    if (t == null) return;
                    t.setId(doc.getId());
                    currentTeam = t;

                    etTeamName.setText(t.getName() == null ? "" : t.getName());

                    // NEW
                    setPeriodTypeSelection(t.getPeriodType());
                });

        btnSaveName.setOnClickListener(v -> saveName());
        btnEditMembers.setOnClickListener(v -> showEditMembersDialog());

        btnManageTemplates.setOnClickListener(v -> {
            Intent i = new Intent(this, ManageShiftTemplatesActivity.class);
            i.putExtra("companyId", companyId);
            i.putExtra("teamId", teamId); // IMPORTANT
            startActivity(i);
        });


        if (btnEditFullTimeTemplate != null) {
            btnEditFullTimeTemplate.setOnClickListener(v -> showEditFullTimeTemplateDialog());
        }

        // NEW
        if (btnSavePeriodType != null) {
            btnSavePeriodType.setOnClickListener(v -> savePeriodType());
        }
    }

    // NEW
    private void bindPeriodTypeSpinner() {
        List<String> options = new ArrayList<>();
        options.add("WEEKLY");
        options.add("MONTHLY");

        ArrayAdapter<String> a = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                options
        );
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPeriodType.setAdapter(a);

        // default selection
        spinnerPeriodType.setSelection(0);
    }

    // NEW
    private void setPeriodTypeSelection(String periodType) {
        if (spinnerPeriodType == null) return;

        String pt = (periodType == null || periodType.trim().isEmpty()) ? "WEEKLY" : periodType.trim();
        if ("MONTHLY".equals(pt)) spinnerPeriodType.setSelection(1);
        else spinnerPeriodType.setSelection(0);
    }

    // NEW
    private void savePeriodType() {
        if (companyId.trim().isEmpty() || teamId.trim().isEmpty()) return;

        String selected = (String) spinnerPeriodType.getSelectedItem();
        if (selected == null || selected.trim().isEmpty()) selected = "WEEKLY";

        final String selectedFinal = selected;

        FirebaseFirestore.getInstance()
                .collection("companies").document(companyId)
                .collection("teams").document(teamId)
                .update("periodType", selectedFinal)
                .addOnSuccessListener(unused ->
                        Toast.makeText(this, "Saved schedule type: " + selectedFinal, Toast.LENGTH_SHORT).show()
                )
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed: " + (e.getMessage() == null ? "" : e.getMessage()), Toast.LENGTH_LONG).show()
                );
    }


    private void saveName() {
        if (currentTeam == null) return;
        String name = etTeamName.getText() == null ? "" : etTeamName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "Name required", Toast.LENGTH_SHORT).show();
            return;
        }
        teamRepo.updateTeamName(companyId, teamId, name, (success, msg) ->
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        );
    }

    private void showEditFullTimeTemplateDialog() {
        if (currentTeam == null) return;

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

        ShiftTemplate existing = currentTeam.getFullTimeTemplate();
        if (existing != null) {
            etTitle.setText(existing.getTitle() == null ? "" : existing.getTitle());
            spStart.setSelection(Math.max(0, existing.getStartHour()));
            spEnd.setSelection(Math.max(0, existing.getEndHour()));
        } else {
            etTitle.setText("Full-time");
            spStart.setSelection(9);
            spEnd.setSelection(17);
        }

        final AlertDialog templateDialog = new AlertDialog.Builder(this)
                .setTitle("Full-time template")
                .setView(view)
                .setPositiveButton("Next", null) // open days picker
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .create();

        templateDialog.setOnShowListener(d -> {
            Button b = templateDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            b.setOnClickListener(v -> {
                String title = etTitle.getText() == null ? "" : etTitle.getText().toString().trim();
                if (title.isEmpty()) {
                    Toast.makeText(this, "Title is required", Toast.LENGTH_SHORT).show();
                    return;
                }

                int start = Integer.parseInt((String) spStart.getSelectedItem());
                int end = Integer.parseInt((String) spEnd.getSelectedItem());

                if (start == end) {
                    Toast.makeText(this, "Start and end cannot be the same", Toast.LENGTH_SHORT).show();
                    return;
                }

                ShiftTemplate ft = new ShiftTemplate();
                ft.setId(null);
                ft.setTitle(title);
                ft.setStartHour(start);
                ft.setEndHour(end);
                ft.setEnabled(true);

                // ---- Days picker ----
                List<Integer> currentDays = currentTeam.getFullTimeDays();
                if (currentDays == null) currentDays = new ArrayList<>();

                final String[] dayLabels = new String[] {
                        "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
                };
                final int[] dayValues = new int[] {
                        Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                        Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
                };

                final boolean[] checked = new boolean[7];

                if (!currentDays.isEmpty()) {
                    for (int i = 0; i < 7; i++) checked[i] = currentDays.contains(dayValues[i]);
                } else {
                    // default: Sun-Thu
                    checked[0] = true;
                    checked[1] = true;
                    checked[2] = true;
                    checked[3] = true;
                    checked[4] = true;
                    checked[5] = false;
                    checked[6] = false;
                }

                AlertDialog daysDialog = new AlertDialog.Builder(this)
                        .setTitle("Full-time working days")
                        .setMultiChoiceItems(dayLabels, checked, (dd, which, isChecked) -> checked[which] = isChecked)
                        .setNegativeButton("Cancel", (dd, ww) -> dd.dismiss())
                        .setPositiveButton("Save", null) // IMPORTANT: override
                        .create();

                daysDialog.setOnShowListener(dd -> {
                    Button saveBtn = daysDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                    saveBtn.setOnClickListener(vv -> {
                        ArrayList<Integer> selectedDays = new ArrayList<>();
                        for (int i = 0; i < 7; i++) {
                            if (checked[i]) selectedDays.add(dayValues[i]);
                        }

                        if (selectedDays.isEmpty()) {
                            Toast.makeText(this, "Pick at least one day", Toast.LENGTH_SHORT).show();
                            return; // do NOT dismiss
                        }

                        Map<String, Object> update = new HashMap<>();
                        update.put("fullTimeTemplate", ft);
                        update.put("fullTimeDays", selectedDays);

                        FirebaseFirestore.getInstance()
                                .collection("companies").document(companyId)
                                .collection("teams").document(teamId)
                                .update(update)
                                .addOnSuccessListener(unused -> {
                                    Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
                                    daysDialog.dismiss();
                                    templateDialog.dismiss();
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(this, "Failed: " + (e.getMessage() == null ? "" : e.getMessage()),
                                                Toast.LENGTH_LONG).show()
                                );
                    });
                });

                daysDialog.show();
            });
        });

        templateDialog.show();
    }



    // unchanged
    private void showEditMembersDialog() {
        if (currentTeam == null) return;

        FirebaseFirestore.getInstance()
                .collection("users")
                .whereEqualTo("companyId", companyId)
                .whereEqualTo("status", "APPROVED")
                .get()
                .addOnSuccessListener(qs -> {

                    List<User> all = new ArrayList<>();
                    List<String> labels = new ArrayList<>();

                    for (var doc : qs.getDocuments()) {
                        User u = doc.toObject(User.class);
                        if (u == null) continue;
                        u.setUid(doc.getId());
                        all.add(u);

                        String name = (u.getFullName() != null && !u.getFullName().trim().isEmpty())
                                ? u.getFullName().trim()
                                : (u.getEmail() == null ? "Employee" : u.getEmail());

                        labels.add(name + " (" + (u.getEmail() == null ? "" : u.getEmail()) + ")");
                    }

                    Set<String> membersSet = new HashSet<>();
                    if (currentTeam.getMemberIds() != null) membersSet.addAll(currentTeam.getMemberIds());

                    boolean[] checked = new boolean[all.size()];
                    for (int i = 0; i < all.size(); i++) {
                        checked[i] = membersSet.contains(all.get(i).getUid());
                    }

                    final AlertDialog dlg = new AlertDialog.Builder(this)

                            .setTitle("Team members")
                            .setMultiChoiceItems(labels.toArray(new String[0]), checked,
                                    (d, which, isChecked) -> checked[which] = isChecked)
                            .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                            .setPositiveButton("Save", null)
                            .create();

                    dlg.setOnShowListener(d -> {
                        Button b = dlg.getButton(AlertDialog.BUTTON_POSITIVE);
                        b.setOnClickListener(v -> {
                            List<String> newMemberIds = new ArrayList<>();
                            for (int i = 0; i < checked.length; i++) {
                                if (checked[i]) newMemberIds.add(all.get(i).getUid());
                            }

                            if (newMemberIds.isEmpty()) {
                                Toast.makeText(this, "Team must have at least 1 member", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            teamRepo.setTeamMembers(companyId, teamId, newMemberIds, (success, msg) -> {
                                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                                if (success) dlg.dismiss();
                            });
                        });
                    });

                    dlg.show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed: " + (e.getMessage() == null ? "" : e.getMessage()), Toast.LENGTH_LONG).show()
                );
    }
}
