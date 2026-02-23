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
import com.example.workconnect.models.Team;
import com.example.workconnect.repository.authAndUsers.TeamRepository;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;

public class TeamsActivity extends AppCompatActivity {

    private String companyId = "";

    private Button btnBack, btnCreate, btnEdit;

    private final TeamRepository teamRepo = new TeamRepository();

    private final List<Team> cachedTeams = new ArrayList<>();
    private String myUid = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teams);

        companyId = getIntent().getStringExtra("companyId");
        if (companyId == null) companyId = "";

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }

        btnBack = findViewById(R.id.btn_back);
        btnCreate = findViewById(R.id.btn_create_team);
        btnEdit = findViewById(R.id.btn_edit_team);

        btnBack.setOnClickListener(v -> finish());

        teamRepo.getTeamsForCompany(companyId).observe(this, teams -> {
            cachedTeams.clear();
            if (teams != null) cachedTeams.addAll(teams);
        });

        btnCreate.setOnClickListener(v -> showCreateDialog());
        btnEdit.setOnClickListener(v -> showPickTeamDialog());
    }

    private void showCreateDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_create_team, null);
        EditText etName = view.findViewById(R.id.et_team_name);

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Create team")
                .setView(view)
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .setPositiveButton("Create", null)
                .create();

        dlg.setOnShowListener(d -> {
            Button b = dlg.getButton(AlertDialog.BUTTON_POSITIVE);
            b.setOnClickListener(v -> {
                String name = etName.getText() == null ? "" : etName.getText().toString().trim();
                if (name.isEmpty()) {
                    Toast.makeText(this, "Team name required", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (companyId.trim().isEmpty() || myUid.trim().isEmpty()) {
                    Toast.makeText(this, "Missing company/user", Toast.LENGTH_SHORT).show();
                    return;
                }

                teamRepo.createTeam(companyId, name, myUid, (success, msg, teamId) -> {
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                    if (success && teamId != null) {
                        Intent i = new Intent(this, EditTeamActivity.class);
                        i.putExtra("companyId", companyId);
                        i.putExtra("teamId", teamId);
                        startActivity(i);
                    }
                });

                dlg.dismiss();
            });
        });

        dlg.show();
    }

    private void showPickTeamDialog() {
        if (cachedTeams.isEmpty()) {
            Toast.makeText(this, "No teams yet", Toast.LENGTH_SHORT).show();
            return;
        }

        // Only teams where manager is a member
        List<Team> mine = new ArrayList<>();
        for (Team t : cachedTeams) {
            if (t.getMemberIds() != null && t.getMemberIds().contains(myUid)) mine.add(t);
        }
        if (mine.isEmpty()) {
            Toast.makeText(this, "You are not in any team yet", Toast.LENGTH_SHORT).show();
            return;
        }

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_pick_team, null);
        Spinner sp = view.findViewById(R.id.spinner_team);

        List<String> labels = new ArrayList<>();
        for (Team t : mine) labels.add(t.getName() == null ? "(Unnamed)" : t.getName());

        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(a);

        new AlertDialog.Builder(this)
                .setTitle("Pick team")
                .setView(view)
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .setPositiveButton("Edit", (d, w) -> {
                    int pos = sp.getSelectedItemPosition();
                    Team picked = mine.get(pos);
                    Intent i = new Intent(this, EditTeamActivity.class);
                    i.putExtra("companyId", companyId);
                    i.putExtra("teamId", picked.getId());
                    startActivity(i);
                })
                .show();
    }
}
