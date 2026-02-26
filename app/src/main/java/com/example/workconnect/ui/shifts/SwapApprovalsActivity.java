package com.example.workconnect.ui.shifts;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.workconnect.R;
import com.example.workconnect.adapters.shifts.SwapApprovalsAdapter;
import com.example.workconnect.models.ShiftSwapRequest;
import com.example.workconnect.models.Team;
import com.example.workconnect.repository.authAndUsers.TeamRepository;
import com.example.workconnect.repository.shifts.ShiftSwapRepository;
import com.example.workconnect.ui.home.BaseDrawerActivity;

import java.util.ArrayList;
import java.util.List;

public class SwapApprovalsActivity extends BaseDrawerActivity {

    private Spinner spinnerTeam;
    private RecyclerView rv;
    private TextView tvEmpty;

    private final TeamRepository teamRepo = new TeamRepository();
    private final ShiftSwapRepository swapRepo = new ShiftSwapRepository();

    private final List<Team> cachedTeams = new ArrayList<>();
    private String companyId;
    private String selectedTeamId;

    private SwapApprovalsAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_swap_approvals);

        if (toolbar != null) {
            toolbar.setTitle("Swap approvals");
        }


        companyId = getIntent().getStringExtra("companyId");
        if (companyId == null) companyId = "";
        companyId = companyId.trim();

        if (companyId.isEmpty()) {
            Toast.makeText(this, "Missing companyId (SwapApprovalsActivity)", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        spinnerTeam = findViewById(R.id.spinner_team);
        rv = findViewById(R.id.rv_pending);
        tvEmpty = findViewById(R.id.tv_empty);

        adapter = new SwapApprovalsAdapter(new SwapApprovalsAdapter.Listener() {
            @Override
            public void onApprove(ShiftSwapRequest r) {
                if (r == null || selectedTeamId == null) return;

                swapRepo.managerMarkApproved(
                        companyId,
                        selectedTeamId,
                        r.getId(),
                        (ok, msg) -> Toast.makeText(SwapApprovalsActivity.this, msg, Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void onReject(ShiftSwapRequest r) {
                if (r == null || selectedTeamId == null) return;

                swapRepo.managerRejectPending(
                        companyId,
                        selectedTeamId,
                        r.getId(),
                        (ok, msg) -> Toast.makeText(SwapApprovalsActivity.this, msg, Toast.LENGTH_SHORT).show()
                );
            }
        });

        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        bindTeams();
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

            ArrayAdapter<String> a = new ArrayAdapter<>(
                    this,
                    android.R.layout.simple_spinner_item,
                    labels
            );
            a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerTeam.setAdapter(a);

            spinnerTeam.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    if (position == 0) {
                        selectedTeamId = null;
                        adapter.setItems(new ArrayList<>());
                        tvEmpty.setVisibility(View.VISIBLE);
                        return;
                    }

                    selectedTeamId = cachedTeams.get(position - 1).getId();
                    listenPending();
                }

                @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });
        });
    }

    private void listenPending() {
        if (selectedTeamId == null) return;

        swapRepo.listenPendingApprovals(companyId, selectedTeamId)
                .observe(this, list -> {
                    if (list == null) list = new ArrayList<>();
                    adapter.setItems(list);
                    tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }
}