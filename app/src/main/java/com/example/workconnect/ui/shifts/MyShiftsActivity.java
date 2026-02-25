package com.example.workconnect.ui.shifts;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.content.Intent;
import android.widget.Button;

import com.example.workconnect.R;
import com.example.workconnect.adapters.shifts.MyShiftsAdapter;
import com.example.workconnect.models.Team;
import com.example.workconnect.ui.home.BaseDrawerActivity;
import com.google.firebase.firestore.FirebaseFirestore;
import com.example.workconnect.repository.shifts.MyShiftsRepository;
import com.example.workconnect.repository.authAndUsers.TeamRepository;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MyShiftsActivity extends BaseDrawerActivity {

    private String companyId = "";

    private TextView tvRange;
    private ImageButton btnPrev;
    private ImageButton btnNext;

    private RecyclerView rv;
    private MyShiftsAdapter adapter;

    private final TeamRepository teamRepo = new TeamRepository();
    private final MyShiftsRepository myRepo = new MyShiftsRepository();

    private final List<Team> cachedTeams = new ArrayList<>();
    private final Map<String, String> teamIdToName = new HashMap<>();

    // Week window
    private Calendar weekStart; // Monday 00:00 of shown week
    private Calendar thisWeekStart; // used to limit "next"

    private Calendar maxWeekStart;

    // comes from BaseDrawerActivity via intent extra
    private String employmentType = ""; // "FULL_TIME" / "SHIFT_BASED" / null
    private final List<String> myTeamIds = new ArrayList<>();
    private Button btnAvailability;

    private String userUid = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_shifts);

        companyId = getIntent().getStringExtra("companyId");
        if (companyId == null) companyId = "";

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            userUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }

        tvRange = findViewById(R.id.tv_range);
        btnPrev = findViewById(R.id.btn_prev);
        btnNext = findViewById(R.id.btn_next);
        rv = findViewById(R.id.rv_my_shifts);
        btnAvailability = findViewById(R.id.btn_availability);

        adapter = new MyShiftsAdapter();
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        initWeekWindows();
        updateRangeHeader();
        updateNextEnabled();

        btnPrev.setOnClickListener(v -> {
            weekStart.add(Calendar.DAY_OF_MONTH, -7);
            updateRangeHeader();
            updateNextEnabled();
            listen();
        });

        btnAvailability.setOnClickListener(v -> {
            startActivity(new Intent(this, MyAvailabilityActivity.class)
                    .putExtra("companyId", companyId));
        });

        btnNext.setOnClickListener(v -> {
            // prevent going beyond max (one week ahead)
            if (weekStart.compareTo(maxWeekStart) >= 0) return;

            weekStart.add(Calendar.DAY_OF_MONTH, 7);
            if (weekStart.compareTo(maxWeekStart) > 0) weekStart = (Calendar) maxWeekStart.clone();

            updateRangeHeader();
            updateNextEnabled();
            listen();
        });


        // Load teams and then listen
        loadMyProfileThenListen();
    }

    private void initWeekWindows() {
        thisWeekStart = Calendar.getInstance();
        normalizeToSunday(thisWeekStart);

        maxWeekStart = (Calendar) thisWeekStart.clone();
        maxWeekStart.add(Calendar.DAY_OF_MONTH, 7); // allow 1 week ahead

        weekStart = (Calendar) thisWeekStart.clone(); // default = current week
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


    private void updateRangeHeader() {
        Calendar end = (Calendar) weekStart.clone();
        end.add(Calendar.DAY_OF_MONTH, 6);

        String startKey = toDateKey(weekStart);
        String endKey = toDateKey(end);

        tvRange.setText(startKey + " - " + endKey);
    }

    private void updateNextEnabled() {
        boolean enabled = weekStart.compareTo(maxWeekStart) < 0;
        btnNext.setEnabled(enabled);
        btnNext.setAlpha(enabled ? 1f : 0.35f);
    }


    private void bindTeamsThenListen() {
        teamRepo.getTeamsForCompany(companyId).observe(this, teams -> {
            cachedTeams.clear();
            teamIdToName.clear();

            if (teams != null) {
                for (Team t : teams) {
                    if (t.getId() == null) continue;

                    //only teams I'm in
                    if (!myTeamIds.isEmpty() && !myTeamIds.contains(t.getId())) continue;

                    cachedTeams.add(t);
                    teamIdToName.put(t.getId(), t.getName() == null ? t.getId() : t.getName());
                }
            }

            listen();
        });
    }


    private void listen() {
        if (companyId.trim().isEmpty()) {
            Toast.makeText(this, "Missing companyId", Toast.LENGTH_SHORT).show();
            return;
        }
        if (userUid.trim().isEmpty()) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> teamIds = new ArrayList<>();
        for (Team t : cachedTeams) {
            if (t.getId() != null) teamIds.add(t.getId());
        }

        List<String> dates = buildWeekDateKeys(weekStart);

        if ("FULL_TIME".equals(employmentType)) {
            myRepo.listenFullTimeForRange(companyId, teamIds, teamIdToName, dates)
                    .observe(this, shifts -> adapter.setItems(shifts));
        } else {
            myRepo.listenMyShiftsForRange(companyId, teamIds, teamIdToName, userUid, dates)
                    .observe(this, shifts -> adapter.setItems(shifts));
        }
    }


    private List<String> buildWeekDateKeys(Calendar startSunday) {
        ArrayList<String> out = new ArrayList<>();
        Calendar c = (Calendar) startSunday.clone();
        for (int i = 0; i < 7; i++) {
            out.add(toDateKey(c));
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        return out;
    }

    private String toDateKey(Calendar c) {
        int y = c.get(Calendar.YEAR);
        int m = c.get(Calendar.MONTH) + 1;
        int d = c.get(Calendar.DAY_OF_MONTH);

        String mm = (m < 10 ? "0" : "") + m;
        String dd = (d < 10 ? "0" : "") + d;

        return y + "-" + mm + "-" + dd;
    }
    private void loadMyProfileThenListen() {
        if (companyId.trim().isEmpty()) {
            Toast.makeText(this, "Missing companyId", Toast.LENGTH_SHORT).show();
            return;
        }
        if (userUid.trim().isEmpty()) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(userUid)
                .get()
                .addOnSuccessListener(doc -> {
                    employmentType = doc.getString("employmentType");
                    boolean isFullTime = "FULL_TIME".equals(employmentType);
                    btnAvailability.setVisibility(isFullTime ? View.GONE : View.VISIBLE);

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

                    bindTeamsThenListen();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show();
                    bindTeamsThenListen();
                });
    }


}
