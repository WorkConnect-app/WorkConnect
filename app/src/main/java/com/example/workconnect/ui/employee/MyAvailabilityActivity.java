package com.example.workconnect.ui.employee;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
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
import com.example.workconnect.adapters.CalendarAdapter;
import com.example.workconnect.adapters.DayShiftsAdapter;
import com.example.workconnect.models.ShiftTemplate;
import com.example.workconnect.models.Team;
import com.example.workconnect.repository.AvailabilityRepository;
import com.example.workconnect.repository.TeamRepository;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class MyAvailabilityActivity extends AppCompatActivity {

    private String companyId = "";
    private String userUid = "";

    private TextView tvTitle;
    private TextView tvRange;
    private ImageButton btnBack;
    private ImageButton btnPrev;
    private ImageButton btnNext;
    private Spinner spinnerTeam;

    private RecyclerView rvCalendar;
    private CalendarAdapter calendarAdapter;

    private final AvailabilityRepository prefRepo = new AvailabilityRepository();
    private final TeamRepository teamRepo = new TeamRepository();

    private final List<String> myTeamIds = new ArrayList<>();
    private final List<Team> cachedTeams = new ArrayList<>();

    private Team selectedTeam = null;

    private final List<ShiftTemplate> selectedTeamTemplates = new ArrayList<>();
    private final List<String> currentDateKeys = new ArrayList<>();

    // key: teamId|dateKey|shiftId -> status
    private final Map<String, String> currentStatusMap = new HashMap<>();

    // period anchor:
    // MONTHLY = first day of shown month
    // WEEKLY = monday of shown week
    private Calendar periodAnchor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_availability);

        companyId = getIntent().getStringExtra("companyId");
        if (companyId == null) companyId = "";

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            userUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }

        tvTitle = findViewById(R.id.tv_title);
        tvRange = findViewById(R.id.tv_range);
        btnBack = findViewById(R.id.btn_back);
        btnPrev = findViewById(R.id.btn_prev);
        btnNext = findViewById(R.id.btn_next);
        spinnerTeam = findViewById(R.id.spinner_team);
        rvCalendar = findViewById(R.id.rv_calendar);

        btnBack.setOnClickListener(v -> finish());

        calendarAdapter = new CalendarAdapter(cell -> {
            if (cell == null || cell.isPlaceholder) return;
            if (selectedTeam == null || selectedTeam.getId() == null) return;
            openDayBottomSheet(cell.dateKey);
        });

        rvCalendar.setLayoutManager(new GridLayoutManager(this, 7));
        rvCalendar.setAdapter(calendarAdapter);

        periodAnchor = Calendar.getInstance();

        btnPrev.setOnClickListener(v -> {
            if (selectedTeam == null) return;
            if (isMonthly(selectedTeam)) {
                periodAnchor.add(Calendar.MONTH, -1);
                periodAnchor.set(Calendar.DAY_OF_MONTH, 1);
            } else {
                normalizeToSunday(periodAnchor);
                periodAnchor.add(Calendar.DAY_OF_MONTH, -7);
            }
            rebuildPeriodAndListen();
        });

        btnNext.setOnClickListener(v -> {
            if (selectedTeam == null) return;
            if (isMonthly(selectedTeam)) {
                periodAnchor.add(Calendar.MONTH, 1);
                periodAnchor.set(Calendar.DAY_OF_MONTH, 1);
            } else {
                normalizeToSunday(periodAnchor);
                periodAnchor.add(Calendar.DAY_OF_MONTH, 7);
            }
            rebuildPeriodAndListen();
        });

        loadMyProfileThenBindTeamsSpinner();
    }

    private boolean isMonthly(Team t) {
        String p = (t == null) ? null : t.getPeriodType();
        return "MONTHLY".equalsIgnoreCase(p);
    }

    private void loadMyProfileThenBindTeamsSpinner() {
        if (companyId.trim().isEmpty() || userUid.trim().isEmpty()) {
            Toast.makeText(this, "Missing company/user", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(userUid)
                .get()
                .addOnSuccessListener(doc -> {
                    myTeamIds.clear();
                    Object raw = doc.get("teamIds");
                    if (raw instanceof List) {
                        for (Object o : (List<?>) raw) {
                            if (o != null) {
                                String id = String.valueOf(o).trim();
                                if (!id.isEmpty()) myTeamIds.add(id);
                            }
                        }
                    }
                    bindTeamsSpinner();
                })
                .addOnFailureListener(e -> bindTeamsSpinner());
    }

    private void bindTeamsSpinner() {
        teamRepo.getTeamsForCompany(companyId).observe(this, teams -> {
            cachedTeams.clear();

            if (teams != null) {
                for (Team t : teams) {
                    if (t.getId() == null) continue;
                    if (!myTeamIds.isEmpty() && !myTeamIds.contains(t.getId())) continue;
                    cachedTeams.add(t);
                }
            }

            List<String> labels = new ArrayList<>();
            labels.add("Select team");
            for (Team t : cachedTeams) {
                String name = (t.getName() == null || t.getName().trim().isEmpty()) ? t.getId() : t.getName();
                String period = isMonthly(t) ? "MONTHLY" : "WEEKLY";
                labels.add(name + " (" + period + ")");
            }

            ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
            a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerTeam.setAdapter(a);

            spinnerTeam.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (position == 0) {
                        selectedTeam = null;
                        tvTitle.setText("Availability");
                        tvRange.setText("");
                        calendarAdapter.setItems(new ArrayList<>());
                        btnPrev.setEnabled(false);
                        btnNext.setEnabled(false);
                        return;
                    }

                    selectedTeam = cachedTeams.get(position - 1);

                    // default period = next month / next week
                    periodAnchor = Calendar.getInstance();
                    if (isMonthly(selectedTeam)) {
                        periodAnchor.set(Calendar.DAY_OF_MONTH, 1);
                        periodAnchor.add(Calendar.MONTH, 1);
                    } else {
                        normalizeToSunday(periodAnchor);
                        periodAnchor.add(Calendar.DAY_OF_MONTH, 7);
                    }

                    btnPrev.setEnabled(true);
                    btnNext.setEnabled(true);

                    rebuildPeriodAndListen();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) { }
            });
        });
    }

    private void rebuildPeriodAndListen() {
        if (selectedTeam == null || selectedTeam.getId() == null) return;

        currentDateKeys.clear();

        if (isMonthly(selectedTeam)) {
            periodAnchor.set(Calendar.DAY_OF_MONTH, 1);
            currentDateKeys.addAll(buildDateKeysForMonth(periodAnchor));
            tvRange.setText(formatMonthTitle(periodAnchor));
        } else {
            normalizeToSunday(periodAnchor);
            currentDateKeys.addAll(buildWeekDateKeys(periodAnchor));
            String start = currentDateKeys.get(0);
            String end = currentDateKeys.get(currentDateKeys.size() - 1);
            tvRange.setText(start + " - " + end);
        }

        String teamName = (selectedTeam.getName() == null) ? selectedTeam.getId() : selectedTeam.getName();
        tvTitle.setText("Availability (" + teamName + ")");

        loadShiftTemplatesForSelectedTeamThenListenPrefs();
    }

    private void loadShiftTemplatesForSelectedTeamThenListenPrefs() {
        selectedTeamTemplates.clear();
        currentStatusMap.clear();

        String teamId = selectedTeam.getId();

        FirebaseFirestore.getInstance()
                .collection("companies").document(companyId)
                .collection("teams").document(teamId)
                .collection("shiftTemplates")
                .get()
                .addOnSuccessListener(qs -> {
                    if (qs != null) {
                        for (com.google.firebase.firestore.DocumentSnapshot d : qs.getDocuments()) {
                            ShiftTemplate st = d.toObject(ShiftTemplate.class);
                            if (st != null) {
                                if (st.getId() == null || st.getId().trim().isEmpty()) st.setId(d.getId());
                                if (!st.isEnabled()) continue;
                                selectedTeamTemplates.add(st);
                            }
                        }
                    }
                    listenMyPrefsAndRenderCalendar();
                })
                .addOnFailureListener(e -> {
                    calendarAdapter.setItems(new ArrayList<>());
                    Toast.makeText(this, "Failed to load shift templates", Toast.LENGTH_SHORT).show();
                });
    }

    private void listenMyPrefsAndRenderCalendar() {
        if (selectedTeam == null || selectedTeam.getId() == null) return;

        String teamId = selectedTeam.getId();

        HashSet<String> shiftIdsSet = new HashSet<>();
        for (ShiftTemplate st : selectedTeamTemplates) {
            if (st != null && st.getId() != null && !st.getId().trim().isEmpty()) shiftIdsSet.add(st.getId());
        }
        List<String> shiftIds = new ArrayList<>(shiftIdsSet);

        if (shiftIds.isEmpty() || currentDateKeys.isEmpty()) {
            calendarAdapter.setItems(new ArrayList<>());
            return;
        }

        List<String> teamIds = new ArrayList<>();
        teamIds.add(teamId);

        prefRepo.listenMyAvailability(companyId, teamIds, currentDateKeys, shiftIds, userUid)
                .observe(this, map -> {
                    currentStatusMap.clear();
                    if (map != null) currentStatusMap.putAll(map);
                    rebuildCalendarCells();
                });
    }

    private void rebuildCalendarCells() {
        if (selectedTeam == null || selectedTeam.getId() == null) return;

        List<CalendarAdapter.DayCell> cells = new ArrayList<>();

        if (isMonthly(selectedTeam)) {
            Calendar first = (Calendar) periodAnchor.clone();
            first.set(Calendar.DAY_OF_MONTH, 1);

            // Sunday-first offset
            int dayOfWeek = first.get(Calendar.DAY_OF_WEEK); // Sun=1..Sat=7
            int offset = dayOfWeek - Calendar.SUNDAY;        // Sun->0 ... Sat->6
            for (int i = 0; i < offset; i++) {
                cells.add(new CalendarAdapter.DayCell(true, null, 0, ""));
            }


            int maxDay = first.getActualMaximum(Calendar.DAY_OF_MONTH);
            Calendar c = (Calendar) first.clone();

            for (int day = 1; day <= maxDay; day++) {
                c.set(Calendar.DAY_OF_MONTH, day);
                String dateKey = toDateKey(c);

                cells.add(new CalendarAdapter.DayCell(
                        false,
                        dateKey,
                        day,
                        buildDaySummaryText(dateKey)
                ));
            }

            // pad to full weeks (optional)
            while (cells.size() % 7 != 0) {
                cells.add(new CalendarAdapter.DayCell(true, null, 0, ""));
            }

        } else {
            // WEEKLY: exactly 7 cells (no placeholders)
            Calendar monday = (Calendar) periodAnchor.clone();
            normalizeToSunday(monday);

            Calendar c = (Calendar) monday.clone();
            for (int i = 0; i < 7; i++) {
                String dateKey = toDateKey(c);
                int dayNum = c.get(Calendar.DAY_OF_MONTH);

                cells.add(new CalendarAdapter.DayCell(
                        false,
                        dateKey,
                        dayNum,
                        buildDaySummaryText(dateKey)
                ));

                c.add(Calendar.DAY_OF_MONTH, 1);
            }
        }

        calendarAdapter.setItems(cells);
    }

    private String buildDaySummaryText(String dateKey) {
        if (selectedTeam == null || selectedTeam.getId() == null) return "";
        String teamId = selectedTeam.getId();

        int can = 0, pref = 0, cant = 0, none = 0;

        for (ShiftTemplate st : selectedTeamTemplates) {
            if (st == null || st.getId() == null) continue;
            String key = teamId + "|" + dateKey + "|" + st.getId();
            String status = currentStatusMap.get(key);

            if (status == null) none++;
            else if ("CAN".equals(status)) can++;
            else if ("PREFER_NOT".equals(status)) pref++;
            else if ("CANT".equals(status)) cant++;
            else none++;
        }

        // short & readable in tiny cells
        // Example: "C2 P1 X0"  (C=CAN, P=PREFER_NOT, X=CANT)
        return "C" + can + " P" + pref + " X" + cant;
    }


    private String shortShiftName(ShiftTemplate st) {
        String t = st.getTitle();
        if (t == null || t.trim().isEmpty()) return "Shift";
        t = t.trim();
        if (t.length() <= 8) return t;
        return t.substring(0, 8);
    }

    private void openDayBottomSheet(String dateKey) {
        if (selectedTeam == null || selectedTeam.getId() == null) return;

        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View v = getLayoutInflater().inflate(R.layout.bottomsheet_day_shifts, null);
        sheet.setContentView(v);

        TextView tvDayTitle = v.findViewById(R.id.tv_day_title);

        Button btnAllCan = v.findViewById(R.id.btn_all_can);
        Button btnAllPreferNot = v.findViewById(R.id.btn_all_prefer_not);
        Button btnAllCant = v.findViewById(R.id.btn_all_cant);
        Button btnAllClear = v.findViewById(R.id.btn_all_clear);

        RecyclerView rv = v.findViewById(R.id.rv_day_shifts);
        rv.setLayoutManager(new LinearLayoutManager(this));

        DayShiftsAdapter.Listener listener = new DayShiftsAdapter.Listener() {
            @Override
            public void onShiftClicked(String shiftId) {
                // open per-shift editor
                ShiftTemplate template = null;
                for (ShiftTemplate st : selectedTeamTemplates) {
                    if (st != null && st.getId() != null && st.getId().equals(shiftId)) {
                        template = st;
                        break;
                    }
                }
                if (template == null) return;
                openShiftEditorBottomSheet(sheet, dateKey, template);
            }

            @Override
            public void onAssignClicked(ShiftTemplate template) {
                // Not used in availability flow
            }
        };

        DayShiftsAdapter dayAdapter = new DayShiftsAdapter(new ArrayList<>(), listener);
        rv.setAdapter(dayAdapter);

        tvDayTitle.setText(dateKey);
        dayAdapter.setRows(buildDayShiftRows(dateKey));

        btnAllCan.setOnClickListener(x ->
                applyToAllShifts(dateKey, "CAN", () -> dayAdapter.setRows(buildDayShiftRows(dateKey)))
        );

        btnAllPreferNot.setOnClickListener(x ->
                applyToAllShifts(dateKey, "PREFER_NOT", () -> dayAdapter.setRows(buildDayShiftRows(dateKey)))
        );

        btnAllCant.setOnClickListener(x ->
                applyToAllShifts(dateKey, "CANT", () -> dayAdapter.setRows(buildDayShiftRows(dateKey)))
        );

        btnAllClear.setOnClickListener(x ->
                clearAllShifts(dateKey, () -> dayAdapter.setRows(buildDayShiftRows(dateKey)))
        );

        sheet.show();
    }


    private List<DayShiftsAdapter.Row> buildDayShiftRows(String dateKey) {
        List<DayShiftsAdapter.Row> rows = new ArrayList<>();
        String teamId = selectedTeam.getId();

        for (ShiftTemplate st : selectedTeamTemplates) {
            if (st == null || st.getId() == null) continue;

            String key = teamId + "|" + dateKey + "|" + st.getId();
            String status = currentStatusMap.get(key);

            String title = (st.getTitle() == null || st.getTitle().trim().isEmpty()) ? "Shift" : st.getTitle().trim();

            rows.add(new DayShiftsAdapter.Row(st.getId(), title, status));
        }

        return rows;
    }


    private void openShiftEditorBottomSheet(BottomSheetDialog parentSheet, String dateKey, ShiftTemplate template) {
        if (selectedTeam == null || selectedTeam.getId() == null) return;

        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View v = getLayoutInflater().inflate(R.layout.bottomsheet_shift_editor, null);
        sheet.setContentView(v);

        TextView tvTitle = v.findViewById(R.id.tv_shift_editor_title);
        android.widget.RadioGroup rg = v.findViewById(R.id.rg_shift);
        android.widget.RadioButton rbCan = v.findViewById(R.id.rb_can);
        android.widget.RadioButton rbPreferNot = v.findViewById(R.id.rb_prefer_not);
        android.widget.RadioButton rbCant = v.findViewById(R.id.rb_cant);

        Button btnSave = v.findViewById(R.id.btn_save);
        Button btnClear = v.findViewById(R.id.btn_clear);

        String shiftLabel = buildShiftLabel(template);
        tvTitle.setText(dateKey + " • " + shiftLabel);

        String teamId = selectedTeam.getId();
        String key = teamId + "|" + dateKey + "|" + template.getId();
        String current = currentStatusMap.get(key);

        rg.clearCheck();
        if ("CAN".equals(current)) rbCan.setChecked(true);
        else if ("PREFER_NOT".equals(current)) rbPreferNot.setChecked(true);
        else if ("CANT".equals(current)) rbCant.setChecked(true);

        btnSave.setOnClickListener(x -> {
            String status = null;
            int checked = rg.getCheckedRadioButtonId();
            if (checked == R.id.rb_can) status = "CAN";
            else if (checked == R.id.rb_prefer_not) status = "PREFER_NOT";
            else if (checked == R.id.rb_cant) status = "CANT";

            if (status == null) {
                Toast.makeText(this, "Pick a status", Toast.LENGTH_SHORT).show();
                return;
            }

            prefRepo.setMyAvailability(companyId, teamId, dateKey, template.getId(), userUid, status, (success, msg) -> {
                if (!success) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            });

            sheet.dismiss();
            // parent sheet stays open; live listener updates UI
        });

        btnClear.setOnClickListener(x -> {
            prefRepo.clearMyAvailability(companyId, teamId, dateKey, template.getId(), userUid, (success, msg) -> {
                if (!success) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            });
            sheet.dismiss();
        });

        sheet.show();
    }

    private void applyToAllShifts(String dateKey, String status, Runnable after) {
        if (selectedTeam == null || selectedTeam.getId() == null) return;

        String teamId = selectedTeam.getId();
        int[] remaining = new int[]{0};

        for (ShiftTemplate st : selectedTeamTemplates) {
            if (st == null || st.getId() == null) continue;
            remaining[0]++;

            prefRepo.setMyAvailability(companyId, teamId, dateKey, st.getId(), userUid, status, (success, msg) -> {
                remaining[0]--;
                if (remaining[0] <= 0 && after != null) after.run();
            });
        }

        if (remaining[0] == 0 && after != null) after.run();
    }

    private void clearAllShifts(String dateKey, Runnable after) {
        if (selectedTeam == null || selectedTeam.getId() == null) return;

        String teamId = selectedTeam.getId();
        int[] remaining = new int[]{0};

        for (ShiftTemplate st : selectedTeamTemplates) {
            if (st == null || st.getId() == null) continue;
            remaining[0]++;

            prefRepo.clearMyAvailability(companyId, teamId, dateKey, st.getId(), userUid, (success, msg) -> {
                remaining[0]--;
                if (remaining[0] <= 0 && after != null) after.run();
            });
        }

        if (remaining[0] == 0 && after != null) after.run();
    }

    private List<String> buildWeekDateKeys(Calendar sunday) {
        ArrayList<String> out = new ArrayList<>();
        Calendar c = (Calendar) sunday.clone();
        for (int i = 0; i < 7; i++) {
            out.add(toDateKey(c));
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        return out;
    }

    private List<String> buildDateKeysForMonth(Calendar firstOfMonth) {
        ArrayList<String> out = new ArrayList<>();
        Calendar c = (Calendar) firstOfMonth.clone();
        c.set(Calendar.DAY_OF_MONTH, 1);

        int month = c.get(Calendar.MONTH);
        while (c.get(Calendar.MONTH) == month) {
            out.add(toDateKey(c));
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        return out;
    }

    private String formatMonthTitle(Calendar c) {
        int month0 = c.get(Calendar.MONTH);
        int year = c.get(Calendar.YEAR);
        String monthName = new DateFormatSymbols().getMonths()[month0];
        return monthName + " " + year;
    }

    private void normalizeToSunday(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);

        int day = c.get(Calendar.DAY_OF_WEEK); // Sun=1..Sat=7
        int diff = Calendar.SUNDAY - day;
        c.add(Calendar.DAY_OF_MONTH, diff);
    }


    private String toDateKey(Calendar c) {
        int y = c.get(Calendar.YEAR);
        int m = c.get(Calendar.MONTH) + 1;
        int d = c.get(Calendar.DAY_OF_MONTH);

        String mm = (m < 10 ? "0" : "") + m;
        String dd = (d < 10 ? "0" : "") + d;

        return y + "-" + mm + "-" + dd;
    }

    private String buildShiftLabel(ShiftTemplate st) {
        String title = st.getTitle() == null || st.getTitle().trim().isEmpty() ? "Shift" : st.getTitle().trim();
        int startH = st.getStartHour();
        int endH = st.getEndHour();

        String start = (startH < 10 ? "0" : "") + startH + ":00";
        String end = (endH < 10 ? "0" : "") + endH + ":00";
        return title + " (" + start + "-" + end + ")";
    }
}
