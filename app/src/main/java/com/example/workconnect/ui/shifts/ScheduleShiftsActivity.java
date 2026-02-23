package com.example.workconnect.ui.shifts;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.workconnect.R;
import com.example.workconnect.adapters.shifts.CalendarAdapter;
import com.example.workconnect.adapters.shifts.DayShiftsAdapter;
import com.example.workconnect.models.ShiftAssignment;
import com.example.workconnect.models.ShiftTemplate;
import com.example.workconnect.models.Team;
import com.example.workconnect.models.User;
import com.example.workconnect.repository.shifts.AvailabilityRepository;
import com.example.workconnect.repository.authAndUsers.EmployeeRepository;
import com.example.workconnect.repository.shifts.ShiftAssignmentRepository;
import com.example.workconnect.repository.shifts.ShiftRepository;
import com.example.workconnect.repository.authAndUsers.TeamRepository;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

public class ScheduleShiftsActivity extends AppCompatActivity {

    private String companyId = "";

    private Spinner spinnerTeam;
    private Button btnEditTemplates;
    private ImageButton btnBack;

    private ImageButton btnPrevMonth;
    private ImageButton btnNextMonth;
    private TextView tvMonth;

    private RecyclerView rvCalendar;
    private CalendarAdapter calendarAdapter;

    private final TeamRepository teamRepo = new TeamRepository();
    private final ShiftRepository shiftRepo = new ShiftRepository();
    private final ShiftAssignmentRepository assignmentRepo = new ShiftAssignmentRepository();
    private final EmployeeRepository employeeRepo = new EmployeeRepository();
    private final AvailabilityRepository availabilityRepo = new AvailabilityRepository();

    private final List<Team> cachedTeams = new ArrayList<>();
    private final List<User> cachedEmployees = new ArrayList<>();

    private String selectedTeamId = null;

    private String selectedTeamName = null;

    // month anchor = first day of displayed month
    private Calendar monthAnchor;

    // cached for selectedTeam + currently-opened day bottom sheet
    private List<ShiftTemplate> currentTemplates = new ArrayList<>();
    private List<ShiftAssignment> currentAssignmentsForDay = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedule_shifts);

        companyId = getIntent().getStringExtra("companyId");
        if (companyId == null) companyId = "";

        spinnerTeam = findViewById(R.id.spinner_team_select);
        btnEditTemplates = findViewById(R.id.btn_edit_templates);
        btnBack = findViewById(R.id.btn_back);

        btnPrevMonth = findViewById(R.id.btn_prev_month);
        btnNextMonth = findViewById(R.id.btn_next_month);
        tvMonth = findViewById(R.id.tv_month);

        rvCalendar = findViewById(R.id.rv_calendar);

        btnBack.setOnClickListener(v -> finish());

        btnEditTemplates.setOnClickListener(v -> {
            if (selectedTeamId == null) {
                Toast.makeText(this, "Select a team first", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent i = new Intent(this, ManageShiftTemplatesActivity.class);
            i.putExtra("companyId", companyId);
            i.putExtra("teamId", selectedTeamId);
            i.putExtra("teamId", selectedTeamId);
            startActivity(i);
        });

        monthAnchor = Calendar.getInstance();
        monthAnchor.set(Calendar.DAY_OF_MONTH, 1);
        normalizeTime(monthAnchor);

        calendarAdapter = new CalendarAdapter(cell -> {
            if (cell == null || cell.isPlaceholder || cell.dateKey == null) return;

            if (selectedTeamId == null) {
                Toast.makeText(this, "Select a team first", Toast.LENGTH_SHORT).show();
                return;
            }
            openDayBottomSheet(cell.dateKey);
        });

        rvCalendar.setLayoutManager(new GridLayoutManager(this, 7));
        rvCalendar.setAdapter(calendarAdapter);

        btnPrevMonth.setOnClickListener(v -> {
            monthAnchor.add(Calendar.MONTH, -1);
            monthAnchor.set(Calendar.DAY_OF_MONTH, 1);
            normalizeTime(monthAnchor);
            renderMonth();
        });

        btnNextMonth.setOnClickListener(v -> {
            monthAnchor.add(Calendar.MONTH, 1);
            monthAnchor.set(Calendar.DAY_OF_MONTH, 1);
            normalizeTime(monthAnchor);
            renderMonth();
        });

        bindTeams();
        renderMonth();
    }

    private void bindTeams() {
        teamRepo.getTeamsForCompany(companyId).observe(this, teams -> {
            cachedTeams.clear();
            if (teams != null) cachedTeams.addAll(teams);

            List<String> labels = new ArrayList<>();
            labels.add("Select team");
            for (Team t : cachedTeams) {
                labels.add(t.getName() == null ? "(Unnamed)" : t.getName());
            }

            ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
            a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerTeam.setAdapter(a);

            spinnerTeam.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                    if (position == 0) {
                        selectedTeamId = null;
                        selectedTeamName = null;
                        cachedEmployees.clear();
                        currentTemplates = new ArrayList<>();
                        return;
                    }

                    Team chosen = cachedTeams.get(position - 1);

                    // IMPORTANT:
                    // If your Team model has getId(), replace this with chosen.getId()
                    selectedTeamId = chosen.getId();
                    selectedTeamName = chosen.getName();

                    listenEmployeesInTeam();
                    listenTemplatesInTeam();
                }

                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) { }
            });
        });
    }

    private void listenEmployeesInTeam() {
        if (selectedTeamId == null) return;
        employeeRepo.listenApprovedEmployeesForTeam(companyId, selectedTeamId).observe(this, emps -> {
            cachedEmployees.clear();
            if (emps != null) cachedEmployees.addAll(emps);
        });
    }

    private void listenTemplatesInTeam() {
        if (selectedTeamId == null) return;
        shiftRepo.getShiftTemplates(companyId, selectedTeamId).observe(this, templates -> {
            currentTemplates = (templates == null) ? new ArrayList<>() : templates;
        });
    }

    private void renderMonth() {
        tvMonth.setText(formatMonthTitle(monthAnchor));
        calendarAdapter.setItems(buildMonthCells(monthAnchor));
    }

    private List<CalendarAdapter.DayCell> buildMonthCells(Calendar firstOfMonth) {
        ArrayList<CalendarAdapter.DayCell> out = new ArrayList<>();

        Calendar c = (Calendar) firstOfMonth.clone();
        c.set(Calendar.DAY_OF_MONTH, 1);
        normalizeTime(c);

        int firstDayOfWeek = c.get(Calendar.DAY_OF_WEEK); // Sun=1..Sat=7
        int offset = (firstDayOfWeek - Calendar.SUNDAY + 7) % 7; // Sun-first: Sun->0 ... Sat->6

        Calendar start = (Calendar) c.clone();
        start.add(Calendar.DAY_OF_MONTH, -offset);

        Calendar iter = (Calendar) start.clone();
        int month = c.get(Calendar.MONTH);

        // 6 rows * 7 days = 42 cells
        for (int i = 0; i < 42; i++) {
            boolean inMonth = (iter.get(Calendar.MONTH) == month);
            String dateKey = toDateKey(iter);
            int dayNum = iter.get(Calendar.DAY_OF_MONTH);

            out.add(new CalendarAdapter.DayCell(dateKey, dayNum, inMonth));

            iter.add(Calendar.DAY_OF_MONTH, 1);
        }

        return out;
    }


    private void openDayBottomSheet(String dateKey) {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        android.view.View v = getLayoutInflater().inflate(R.layout.bottomsheet_day_shifts, null);
        sheet.setContentView(v);

        // HIDE availability-only bulk actions for manager screen
        android.view.View bulk = v.findViewById(R.id.layout_availability_bulk_actions);
        if (bulk != null) bulk.setVisibility(android.view.View.GONE);

        TextView tvTitle = v.findViewById(R.id.tv_day_title);
        RecyclerView rv = v.findViewById(R.id.rv_day_shifts);
        tvTitle.setText(dateKey);

        // IMPORTANT: declare ref BEFORE listener uses it
        final DayShiftsAdapter[] dayAdapterRef = new DayShiftsAdapter[1];

        DayShiftsAdapter.Listener listener = new DayShiftsAdapter.Listener() {
            @Override
            public void onShiftClicked(String shiftId) {
                // not used in manager screen
            }

            @Override
            public void onAssignClicked(ShiftTemplate template) {
                if (template == null || template.getId() == null) return;

                showAssignDialogForDay(template, dateKey, () -> {
                    if (dayAdapterRef[0] != null) {
                        listenAssignmentsForDay(dateKey, dayAdapterRef[0]);
                    }
                });
            }
        };

        dayAdapterRef[0] = new DayShiftsAdapter(new ArrayList<>(), listener);

        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(dayAdapterRef[0]);

        listenAssignmentsForDay(dateKey, dayAdapterRef[0]);

        sheet.show();
    }


//    private void listenAssignmentsForDay(String dateKey, DayShiftsAdapter dayAdapter) {
//        if (selectedTeamId == null) return;
//
//        assignmentRepo.listenAssignmentsForDate(companyId, selectedTeamId, dateKey).observe(this, assigns -> {
//            currentAssignmentsForDay = (assigns == null) ? new ArrayList<>() : assigns;
//
//            // uid -> employmentType
//            HashMap<String, String> uidToEmp = new HashMap<>();
//            // uid -> name
//            HashMap<String, String> uidToName = new HashMap<>();
//
//            for (User u : cachedEmployees) {
//                if (u.getUid() == null) continue;
//
//                uidToEmp.put(u.getUid(), u.getEmploymentType());
//
//                String name = (u.getFullName() != null && !u.getFullName().trim().isEmpty())
//                        ? u.getFullName().trim()
//                        : (u.getEmail() != null ? u.getEmail() : "Unknown");
//                uidToName.put(u.getUid(), name);
//            }
//
//            // templateId -> list of assigned uids (SHIFT_BASED only)
//            HashMap<String, List<String>> templateToUids = new HashMap<>();
//            for (ShiftAssignment a : currentAssignmentsForDay) {
//                if (a.getTemplateId() == null || a.getUserId() == null) continue;
//
//                String empType = uidToEmp.get(a.getUserId());
//                if ("FULL_TIME".equals(empType)) continue;
//
//                if (!templateToUids.containsKey(a.getTemplateId())) {
//                    templateToUids.put(a.getTemplateId(), new ArrayList<>());
//                }
//                templateToUids.get(a.getTemplateId()).add(a.getUserId());
//            }
//
//            List<DayShiftsAdapter.Row> rows = new ArrayList<>();
//            for (ShiftTemplate t : currentTemplates) {
//                if (t == null || t.getId() == null) continue;
//
//                List<String> assigned = templateToUids.get(t.getId());
//                if (assigned == null) assigned = new ArrayList<>();
//
//                DayShiftsAdapter.Row r = new DayShiftsAdapter.Row(
//                        t.getId(),
//                        (t.getTitle() == null ? "Shift" : t.getTitle()),
//                        null
//                );
//
//                // manager-mode fields
//                r.template = t;
//                r.startHour = t.getStartHour();
//                r.endHour = t.getEndHour();
//                r.assignedUserIds = assigned;
//                r.uidToName = uidToName;
//
//                rows.add(r);
//            }
//
//            dayAdapter.setRows(rows);
//        });
//    }

    // REFACTORED: listenAssignmentsForDay
    private void listenAssignmentsForDay(String dateKey, DayShiftsAdapter dayAdapter) {
        if (selectedTeamId == null) return;

        assignmentRepo.listenAssignmentsForDate(companyId, selectedTeamId, dateKey).observe(this, assigns -> {
            List<ShiftAssignment> assignments = (assigns == null) ? new ArrayList<>() : assigns;
            currentAssignmentsForDay = assignments;

            // Logic extracted to a helper method for cleaner structure
            List<DayShiftsAdapter.Row> rows = buildRowsForDate(assignments);

            dayAdapter.setRows(rows);
        });
    }

    // NEW HELPER METHOD (Improves Readability & Modularity)
    private List<DayShiftsAdapter.Row> buildRowsForDate(List<ShiftAssignment> assignments) {
        // 1. Pre-process Employee Data (Maps)
        HashMap<String, String> uidToEmp = new HashMap<>();
        HashMap<String, String> uidToName = new HashMap<>();

        for (User u : cachedEmployees) {
            if (u.getUid() == null) continue;
            uidToEmp.put(u.getUid(), u.getEmploymentType());
            String name = (u.getFullName() != null && !u.getFullName().trim().isEmpty())
                    ? u.getFullName().trim() : "Unknown";
            uidToName.put(u.getUid(), name);
        }

        // 2. Map Assignments to Templates
        HashMap<String, List<String>> templateToUids = new HashMap<>();
        for (ShiftAssignment a : assignments) {
            if (a.getTemplateId() == null || a.getUserId() == null) continue;

            // Use Constant instead of "FULL_TIME"
            if ("FULL_TIME".equals(uidToEmp.get(a.getUserId()))) continue;

            if (!templateToUids.containsKey(a.getTemplateId())) {
                templateToUids.put(a.getTemplateId(), new ArrayList<>());
            }
            templateToUids.get(a.getTemplateId()).add(a.getUserId());
        }

        // 3. Build View Rows
        List<DayShiftsAdapter.Row> rows = new ArrayList<>();
        for (ShiftTemplate t : currentTemplates) {
            if (t == null || t.getId() == null) continue;

            List<String> assigned = templateToUids.getOrDefault(t.getId(), new ArrayList<>());

            DayShiftsAdapter.Row r = new DayShiftsAdapter.Row(
                    t.getId(),
                    (t.getTitle() == null ? "Shift" : t.getTitle()),
                    null
            );
            r.template = t;
            r.startHour = t.getStartHour();
            r.endHour = t.getEndHour();
            r.assignedUserIds = assigned;
            r.uidToName = uidToName;

            rows.add(r);
        }
        return rows;
    }

    private void showAssignDialogForDay(ShiftTemplate template, String dateKey, Runnable afterSaved) {
        if (selectedTeamId == null) return;
        if (template == null || template.getId() == null) return;

        String shiftId = template.getId();

        availabilityRepo.getAvailabilityForShift(companyId, selectedTeamId, dateKey, shiftId, uidToStatus -> {

            List<String> labels = new ArrayList<>();
            List<String> uids = new ArrayList<>();

            HashMap<String, String> uidToEmp = new HashMap<>();
            HashMap<String, String> uidToName = new HashMap<>();

            for (User u : cachedEmployees) {
                if (u.getUid() == null) continue;
                uidToEmp.put(u.getUid(), u.getEmploymentType());

                String name = (u.getFullName() != null && !u.getFullName().trim().isEmpty())
                        ? u.getFullName().trim()
                        : (u.getEmail() != null ? u.getEmail() : "Unknown");
                uidToName.put(u.getUid(), name);
            }

            for (User u : cachedEmployees) {
                String empType = u.getEmploymentType();
                if ("FULL_TIME".equals(empType)) continue;

                String uid = u.getUid();
                if (uid == null || uid.trim().isEmpty()) continue;

                String status = uidToStatus.get(uid); // CAN / PREFER_NOT / CANT / null

                // HARD RULE: do not show CANT rows
                if ("CANT".equals(status)) continue;

                String name = uidToName.get(uid);
                if (name == null) name = "Unknown";

                String statusLabel;
                if (status == null) statusLabel = "NO RESPONSE";
                else if ("CAN".equals(status)) statusLabel = "CAN";
                else if ("PREFER_NOT".equals(status)) statusLabel = "PREFER NOT";
                else statusLabel = status;

                labels.add(name + " (" + statusLabel + ")");
                uids.add(uid);
            }

            // currently assigned to THIS template
            List<String> currentlyAssigned = new ArrayList<>();
            for (ShiftAssignment a : currentAssignmentsForDay) {
                if (shiftId.equals(a.getTemplateId()) && a.getUserId() != null) {
                    currentlyAssigned.add(a.getUserId());
                }
            }

            boolean[] checked = new boolean[uids.size()];
            for (int i = 0; i < uids.size(); i++) {
                checked[i] = currentlyAssigned.contains(uids.get(i));
            }

            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("Assign: " + (template.getTitle() == null ? "" : template.getTitle()))
                    .setMultiChoiceItems(labels.toArray(new String[0]), checked, (d, which, isChecked) -> {
                        String uid = uids.get(which);
                        String st = uidToStatus.get(uid);

                        if ("PREFER_NOT".equals(st) && isChecked) {
                            Toast.makeText(this, "This worker prefers not to work this shift", Toast.LENGTH_SHORT).show();
                        }
                        checked[which] = isChecked;
                    })
                    .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                    .setPositiveButton("Save", null)
                    .create();

            dialog.setOnShowListener(d -> {
                Button btnSave = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                btnSave.setOnClickListener(v -> {

                    List<String> selected = new ArrayList<>();
                    for (int i = 0; i < checked.length; i++) {
                        if (checked[i]) selected.add(uids.get(i));
                    }

                    // uid -> assignedTemplateId (first match)
                    HashMap<String, String> uidToAssignedTemplate = new HashMap<>();
                    for (ShiftAssignment a : currentAssignmentsForDay) {
                        if (a.getUserId() == null || a.getTemplateId() == null) continue;
                        uidToAssignedTemplate.put(a.getUserId(), a.getTemplateId());
                    }

                    String conflictUid = null;
                    String conflictTemplateId = null;

                    for (String uid : selected) {
                        String assignedTemplateId = uidToAssignedTemplate.get(uid);
                        if (assignedTemplateId != null && !assignedTemplateId.equals(shiftId)) {
                            conflictUid = uid;
                            conflictTemplateId = assignedTemplateId;
                            break;
                        }
                    }

                    if (conflictUid != null) {
                        String finalConflictUid = conflictUid;
                        String finalConflictTemplateId = conflictTemplateId;

                        new AlertDialog.Builder(this)
                                .setMessage("the worker is already assigned do you want to swap him to this shift")
                                .setNegativeButton("No", (dd, ww) -> {
                                    selected.remove(finalConflictUid);
                                    saveAssignmentsForTemplate(dateKey, template, selected, afterSaved, dialog);
                                })
                                .setPositiveButton("Yes", (dd, ww) -> {
                                    swapUserToThisTemplate(dateKey, finalConflictUid, finalConflictTemplateId, template, selected, afterSaved, dialog);
                                })
                                .show();
                        return;
                    }

                    saveAssignmentsForTemplate(dateKey, template, selected, afterSaved, dialog);
                });
            });

            dialog.show();
        });
    }

    private void saveAssignmentsForTemplate(String dateKey,
                                            ShiftTemplate template,
                                            List<String> selectedUids,
                                            Runnable afterSaved,
                                            AlertDialog dialogToClose) {

        assignmentRepo.setAssignmentsForTemplate(
                companyId,
                selectedTeamId,
                dateKey,
                template,
                selectedUids,
                (success, msg) -> {
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                    if (afterSaved != null) afterSaved.run();
                }
        );

        dialogToClose.dismiss();
    }

    private void swapUserToThisTemplate(String dateKey,
                                        String uid,
                                        String fromTemplateId,
                                        ShiftTemplate toTemplate,
                                        List<String> selectedUidsForToTemplate,
                                        Runnable afterSaved,
                                        AlertDialog dialogToClose) {

        ShiftTemplate fromTemplate = null;
        for (ShiftTemplate t : currentTemplates) {
            if (t != null && t.getId() != null && t.getId().equals(fromTemplateId)) {
                fromTemplate = t;
                break;
            }
        }

        List<String> fromAssigned = new ArrayList<>();
        for (ShiftAssignment a : currentAssignmentsForDay) {
            if (a.getTemplateId() != null && a.getTemplateId().equals(fromTemplateId) && a.getUserId() != null) {
                fromAssigned.add(a.getUserId());
            }
        }
        fromAssigned.remove(uid);

        if (fromTemplate != null) {
            ShiftTemplate finalFromTemplate = fromTemplate;
            assignmentRepo.setAssignmentsForTemplate(
                    companyId,
                    selectedTeamId,
                    dateKey,
                    finalFromTemplate,
                    fromAssigned,
                    (success, msg) -> saveAssignmentsForTemplate(dateKey, toTemplate, selectedUidsForToTemplate, afterSaved, dialogToClose)
            );
        } else {
            saveAssignmentsForTemplate(dateKey, toTemplate, selectedUidsForToTemplate, afterSaved, dialogToClose);
        }
    }

    private void normalizeTime(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }

    private String toDateKey(Calendar c) {
        int y = c.get(Calendar.YEAR);
        int m = c.get(Calendar.MONTH) + 1;
        int d = c.get(Calendar.DAY_OF_MONTH);
        String mm = (m < 10 ? "0" : "") + m;
        String dd = (d < 10 ? "0" : "") + d;
        return y + "-" + mm + "-" + dd;
    }

    private String formatMonthTitle(Calendar c) {
        int month0 = c.get(Calendar.MONTH);
        int year = c.get(Calendar.YEAR);
        String monthName = new DateFormatSymbols().getMonths()[month0];
        return monthName + " " + year;
    }
}
