package com.example.workconnect.ui.employee;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.ViewGroup;

import com.example.workconnect.R;
import com.example.workconnect.adapters.MyRequestsAdapter;
import com.example.workconnect.adapters.OpenRequestsAdapter;
import com.example.workconnect.models.ShiftSwapOffer;
import com.example.workconnect.models.ShiftSwapRequest;
import com.example.workconnect.models.Team;
import com.example.workconnect.repository.ShiftSwapRepository;
import com.example.workconnect.repository.TeamRepository;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class ShiftReplacementActivity extends AppCompatActivity {

    private String companyId = "";
    private String myUid = "";
    private String myName = "";

    private Spinner spinnerTeam;
    private RecyclerView rvMy, rvOpen;
    private Button btnNew;

    // NEW UI pieces for collapsible "Your Requests"
    private View headerMy;
    private ImageButton btnToggleMy;
    private View mySectionContainer;
    private boolean myExpanded = true;

    private final TeamRepository teamRepo = new TeamRepository();
    private final ShiftSwapRepository swapRepo = new ShiftSwapRepository();

    private final List<Team> cachedTeams = new ArrayList<>();
    private String selectedTeamId = null;

    private MyRequestsAdapter myAdapter;
    private OpenRequestsAdapter openAdapter;

    // Track my requests (for quick duplicate checks in UI)
    private final List<ShiftSwapRequest> myRequestsCache = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shift_replacement);

        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        companyId = getIntent().getStringExtra("companyId");
        if (companyId == null) companyId = "";

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            myName = FirebaseAuth.getInstance().getCurrentUser().getEmail();
        }

        spinnerTeam = findViewById(R.id.spinner_team);
        rvMy = findViewById(R.id.rv_my_requests);
        rvOpen = findViewById(R.id.rv_open);
        btnNew = findViewById(R.id.btn_new_request);

        // Collapsible section views (from the new XML below)
        headerMy = findViewById(R.id.header_my_requests);
        btnToggleMy = findViewById(R.id.btn_toggle_my);
        mySectionContainer = findViewById(R.id.container_my_requests);

        View.OnClickListener toggle = v -> toggleMySection();
        headerMy.setOnClickListener(toggle);
        btnToggleMy.setOnClickListener(toggle);

        myAdapter = new MyRequestsAdapter(new MyRequestsAdapter.Listener() {
            @Override public void onCancel(ShiftSwapRequest r) {
                if (r == null || r.getId() == null || selectedTeamId == null) return;
                swapRepo.cancelRequest(companyId, selectedTeamId, r.getId(), (ok, msg) ->
                        Toast.makeText(ShiftReplacementActivity.this, msg, Toast.LENGTH_SHORT).show()
                );
            }

            @Override public void onDetails(ShiftSwapRequest r) {
                if (r == null || r.getId() == null || selectedTeamId == null) return;
                showRequestDetails(r);
            }
        });

        openAdapter = new OpenRequestsAdapter(new OpenRequestsAdapter.Listener() {
            @Override public void onOffer(ShiftSwapRequest r) {
                if (r == null || r.getId() == null || selectedTeamId == null) return;
                showMakeOfferDialog(r);
            }
        });

        rvMy.setLayoutManager(new LinearLayoutManager(this));
        rvMy.setAdapter(myAdapter);

        rvOpen.setLayoutManager(new LinearLayoutManager(this));
        rvOpen.setAdapter(openAdapter);

        btnNew.setOnClickListener(v -> {
            if (selectedTeamId == null) {
                Toast.makeText(this, "Pick a team first", Toast.LENGTH_SHORT).show();
                return;
            }
            showCreateRequestDialog();
        });

        bindTeams();
    }

    private void toggleMySection() {
        myExpanded = !myExpanded;
        mySectionContainer.setVisibility(myExpanded ? View.VISIBLE : View.GONE);
        btnToggleMy.setImageResource(myExpanded ? android.R.drawable.arrow_up_float : android.R.drawable.arrow_down_float);
    }

    private void bindTeams() {
        teamRepo.getTeamsForCompany(companyId).observe(this, teams -> {
            cachedTeams.clear();
            if (teams != null) cachedTeams.addAll(teams);

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
                        myAdapter.setItems(new ArrayList<>());
                        openAdapter.setItems(new ArrayList<>());
                        myRequestsCache.clear();
                        return;
                    }
                    selectedTeamId = cachedTeams.get(position - 1).getId();
                    bindListsForTeam();
                }

                @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
            });
        });
    }

    private void bindListsForTeam() {
        if (selectedTeamId == null) return;

        swapRepo.listenMyRequests(companyId, selectedTeamId, myUid).observe(this, list -> {
            List<ShiftSwapRequest> processed = expireIfNeeded(list);

            myRequestsCache.clear();
            if (processed != null) myRequestsCache.addAll(processed);

            myAdapter.setItems(processed);
        });

        swapRepo.listenOpenRequests(companyId, selectedTeamId, myUid).observe(this, list -> {
            openAdapter.setItems(expireIfNeeded(list));
        });
    }

    private List<ShiftSwapRequest> expireIfNeeded(List<ShiftSwapRequest> in) {
        if (in == null) return new ArrayList<>();
        String today = todayKey();

        List<ShiftSwapRequest> out = new ArrayList<>();
        for (ShiftSwapRequest r : in) {
            if (r == null) continue;
            if (r.getDateKey() != null && r.getStatus() != null && ShiftSwapRequest.OPEN.equals(r.getStatus())) {
                if (r.getDateKey().compareTo(today) <= 0) {
                    if (selectedTeamId != null && r.getId() != null) {
                        swapRepo.expireRequest(companyId, selectedTeamId, r.getId());
                    }
                    continue;
                }
            }
            out.add(r);
        }
        return out;
    }

    private boolean hasDuplicateMyRequest(String type, String dateKey, String templateId) {
        for (ShiftSwapRequest existing : myRequestsCache) {
            if (existing == null) continue;
            if (!TextUtils.equals(existing.getType(), type)) continue;
            if (!TextUtils.equals(existing.getDateKey(), dateKey)) continue;
            if (!TextUtils.equals(existing.getTemplateId(), templateId)) continue;

            String st = existing.getStatus();
            if (ShiftSwapRequest.OPEN.equals(st) || ShiftSwapRequest.PENDING_APPROVAL.equals(st)) {
                return true;
            }
        }
        return false;
    }

    private void showCreateRequestDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_create_swap_request, null);

        Spinner spType = view.findViewById(R.id.spinner_type);
        RecyclerView rv = view.findViewById(R.id.rv_upcoming_shifts);
        View tvEmpty = view.findViewById(R.id.tv_empty);
        android.widget.TextView tvSelected = view.findViewById(R.id.tv_selected);

        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item,
                new String[]{ShiftSwapRequest.GIVE_UP, ShiftSwapRequest.SWAP}
        );
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spType.setAdapter(typeAdapter);

        rv.setLayoutManager(new LinearLayoutManager(this));
        UpcomingShiftPickerAdapter pickerAdapter = new UpcomingShiftPickerAdapter(s -> {
            if (s == null) return;
            tvSelected.setText("Selected: " + s.dateKey + " / " + safe(s.templateTitle));
        });
        rv.setAdapter(pickerAdapter);

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("New Shift Replacement Request")
                .setView(view)
                .setNegativeButton("Cancel", (d,w) -> d.dismiss())
                .setPositiveButton("Submit", null)
                .create();

        swapRepo.listenMyUpcomingShifts(
                companyId,
                java.util.Collections.singletonList(selectedTeamId),
                myUid
        ).observe(this, list -> {
            pickerAdapter.setItems(list);
            boolean empty = (list == null || list.isEmpty());
            tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        });

        dlg.setOnShowListener(d -> {
            Button submit = dlg.getButton(AlertDialog.BUTTON_POSITIVE);
            submit.setOnClickListener(v -> {
                ShiftSwapRepository.UpcomingShift chosen = pickerAdapter.getSelected();
                if (chosen == null) {
                    Toast.makeText(this, "Pick a shift first", Toast.LENGTH_SHORT).show();
                    return;
                }

                String type = (String) spType.getSelectedItem();
                if (TextUtils.isEmpty(type)) type = ShiftSwapRequest.GIVE_UP;

                // UI-level duplicate check (fast)
                if (hasDuplicateMyRequest(type, chosen.dateKey, chosen.templateId)) {
                    Toast.makeText(this, "You already submitted this request for this shift", Toast.LENGTH_SHORT).show();
                    return;
                }

                ShiftSwapRequest r = new ShiftSwapRequest();
                r.setDateKey(chosen.dateKey);
                r.setTemplateId(chosen.templateId);
                r.setTemplateTitle(TextUtils.isEmpty(chosen.templateTitle) ? chosen.templateId : chosen.templateTitle);
                r.setRequesterUid(myUid);
                r.setRequesterName(myName);
                r.setType(type);

                swapRepo.createRequest(companyId, selectedTeamId, r, (ok, msg) ->
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                );

                dlg.dismiss();
            });
        });

        dlg.show();
    }

    private void showMakeOfferDialog(ShiftSwapRequest r) {
        if (r == null || selectedTeamId == null) return;

        // GIVE_UP: only if FREE that day + auto-send to manager approval
        if (ShiftSwapRequest.GIVE_UP.equals(r.getType())) {

            swapRepo.hasMyShiftOnDate(companyId, selectedTeamId, r.getDateKey(), myUid, (ok, msg) -> {
                if (!ok) {
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                    return;
                }
                if ("HAS_SHIFT".equals(msg)) {
                    Toast.makeText(this, "You already have a shift on that day.", Toast.LENGTH_SHORT).show();
                    return;
                }

                new AlertDialog.Builder(this)
                        .setMessage("Offer to take this shift?\n" + r.getDateKey() + " / " + r.getTemplateTitle())
                        .setNegativeButton("Cancel", (d,w)->d.dismiss())
                        .setPositiveButton("Offer", (d,w)-> {

                            ShiftSwapOffer o = new ShiftSwapOffer();
                            o.setOfferedByUid(myUid);
                            o.setOfferedByName(myName);
                            o.setOfferedDateKey(null);
                            o.setOfferedTemplateId(null);
                            o.setOfferedTemplateTitle(null);

                            swapRepo.makeOffer(companyId, selectedTeamId, r.getId(), o, (ok2, msg2) -> {
                                Toast.makeText(this, msg2, Toast.LENGTH_SHORT).show();
                                if (!ok2) return;

                                // Important: we need the offerId. makeOffer() sets o.id before write.
                                // It will be non-null here because we set it before doc.set in repo.
                                swapRepo.submitForApproval(companyId, selectedTeamId, r.getId(), o.getId(), (ok3, msg3) -> {
                                    if (!TextUtils.isEmpty(msg3)) {
                                        Toast.makeText(this, msg3, Toast.LENGTH_SHORT).show();
                                    }
                                });
                            });
                        })
                        .show();
            });

            return;
        }

        // SWAP: picker of MY upcoming shifts
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_make_offer_swap, null);

        RecyclerView rv = view.findViewById(R.id.rv_upcoming_shifts);
        View tvEmpty = view.findViewById(R.id.tv_empty);
        android.widget.TextView tvSelected = view.findViewById(R.id.tv_selected);

        rv.setLayoutManager(new LinearLayoutManager(this));
        UpcomingShiftPickerAdapter pickerAdapter = new UpcomingShiftPickerAdapter(s -> {
            if (s == null) return;
            tvSelected.setText("Selected: " + s.dateKey + " / " + safe(s.templateTitle));
        });
        rv.setAdapter(pickerAdapter);

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Make Swap Offer")
                .setView(view)
                .setNegativeButton("Cancel", (d,w)->d.dismiss())
                .setPositiveButton("Offer", null)
                .create();

        swapRepo.listenMyUpcomingShifts(
                companyId,
                java.util.Collections.singletonList(selectedTeamId),
                myUid
        ).observe(this, list -> {
            List<ShiftSwapRepository.UpcomingShift> filtered = new ArrayList<>();
            String today = todayKey();

            if (list != null) {
                for (ShiftSwapRepository.UpcomingShift s : list) {
                    if (s == null || s.dateKey == null) continue;
                    if (s.dateKey.compareTo(today) <= 0) continue;
                    if (s.dateKey.equals(r.getDateKey())) continue;
                    filtered.add(s);
                }
            }

            pickerAdapter.setItems(filtered);
            boolean empty = filtered.isEmpty();
            tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        });

        dlg.setOnShowListener(d -> {
            Button b = dlg.getButton(AlertDialog.BUTTON_POSITIVE);
            b.setOnClickListener(v -> {
                ShiftSwapRepository.UpcomingShift chosen = pickerAdapter.getSelected();
                if (chosen == null) {
                    Toast.makeText(this, "Pick one of your upcoming shifts", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (chosen.dateKey.compareTo(todayKey()) <= 0) {
                    Toast.makeText(this, "Offered shift must be future", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (chosen.dateKey.equals(r.getDateKey())) {
                    Toast.makeText(this, "Offer must be on a different day", Toast.LENGTH_SHORT).show();
                    return;
                }

                ShiftSwapOffer o = new ShiftSwapOffer();
                o.setOfferedByUid(myUid);
                o.setOfferedByName(myName);
                o.setOfferedDateKey(chosen.dateKey);
                o.setOfferedTemplateId(chosen.templateId);
                o.setOfferedTemplateTitle(TextUtils.isEmpty(chosen.templateTitle) ? chosen.templateId : chosen.templateTitle);

                swapRepo.makeOffer(companyId, selectedTeamId, r.getId(), o, (ok,msg) ->
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                );

                dlg.dismiss();
            });
        });

        dlg.show();
    }

    private String safe(String s) {
        return (s == null ? "" : s);
    }

    /** Minimal RecyclerView picker inside the dialog. */
    private static class UpcomingShiftPickerAdapter extends RecyclerView.Adapter<UpcomingShiftPickerAdapter.VH> {

        interface OnPick {
            void onPicked(ShiftSwapRepository.UpcomingShift shift);
        }

        private final List<ShiftSwapRepository.UpcomingShift> items = new ArrayList<>();
        private final OnPick onPick;
        private int selectedPos = -1;

        UpcomingShiftPickerAdapter(OnPick onPick) {
            this.onPick = onPick;
        }

        void setItems(List<ShiftSwapRepository.UpcomingShift> list) {
            items.clear();
            if (list != null) items.addAll(list);
            selectedPos = -1;
            notifyDataSetChanged();
        }

        ShiftSwapRepository.UpcomingShift getSelected() {
            if (selectedPos < 0 || selectedPos >= items.size()) return null;
            return items.get(selectedPos);
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            android.widget.TextView tv = new android.widget.TextView(parent.getContext());
            tv.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            int p = (int) (12 * parent.getContext().getResources().getDisplayMetrics().density);
            tv.setPadding(p, p, p, p);
            tv.setMinHeight((int) (48 * parent.getContext().getResources().getDisplayMetrics().density));
            tv.setTextSize(16f);
            return new VH(tv);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            ShiftSwapRepository.UpcomingShift s = items.get(position);
            String title = (s.templateTitle == null || s.templateTitle.trim().isEmpty()) ? s.templateId : s.templateTitle;
            h.tv.setText(s.dateKey + "  •  " + title);

            boolean selected = (position == selectedPos);
            h.tv.setAlpha(selected ? 1f : 0.75f);
            h.tv.setBackgroundColor(selected ? 0x22000000 : 0x00000000);

            h.tv.setOnClickListener(v -> {
                selectedPos = position;
                notifyDataSetChanged();
                if (onPick != null) onPick.onPicked(s);
            });
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            android.widget.TextView tv;
            VH(@NonNull View itemView) {
                super(itemView);
                tv = (android.widget.TextView) itemView;
            }
        }
    }

    private void showRequestDetails(ShiftSwapRequest r) {
        swapRepo.listenOffers(companyId, selectedTeamId, r.getId()).observe(this, offers -> {
            StringBuilder sb = new StringBuilder();
            sb.append("Shift: ").append(r.getDateKey()).append(" / ").append(r.getTemplateTitle()).append("\n");
            sb.append("Type: ").append(r.getType()).append("\n");
            sb.append("Status: ").append(r.getStatus()).append("\n\n");
            sb.append("Offers:\n");

            List<ShiftSwapOffer> list = (offers == null) ? new ArrayList<>() : offers;
            for (int i = 0; i < list.size(); i++) {
                ShiftSwapOffer o = list.get(i);
                sb.append(i + 1).append(") ").append(o.getOfferedByName() == null ? "Unknown" : o.getOfferedByName());
                if (!TextUtils.isEmpty(o.getOfferedDateKey())) {
                    sb.append(" offers ").append(o.getOfferedDateKey()).append(" / ").append(o.getOfferedTemplateTitle());
                } else {
                    sb.append(" (take shift)");
                }
                sb.append("\n");
            }

            AlertDialog.Builder b = new AlertDialog.Builder(this)
                    .setTitle("Request Details")
                    .setMessage(sb.toString())
                    .setNegativeButton("Close", (d,w)->d.dismiss());

            if (ShiftSwapRequest.OPEN.equals(r.getStatus()) && !list.isEmpty()) {
                b.setPositiveButton("Pick Offer", null);
            }

            AlertDialog dlg = b.create();
            dlg.setOnShowListener(dd -> {
                Button pos = dlg.getButton(AlertDialog.BUTTON_POSITIVE);
                if (pos == null) return;
                pos.setOnClickListener(v -> {
                    ShiftSwapOffer chosen = list.get(0);
                    swapRepo.submitForApproval(companyId, selectedTeamId, r.getId(), chosen.getId(), (ok,msg) ->
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    );
                    dlg.dismiss();
                });
            });

            dlg.show();
        });
    }

    private String todayKey() {
        Calendar c = Calendar.getInstance();
        int y = c.get(Calendar.YEAR);
        int m = c.get(Calendar.MONTH) + 1;
        int d = c.get(Calendar.DAY_OF_MONTH);
        String mm = (m < 10 ? "0" : "") + m;
        String dd = (d < 10 ? "0" : "") + d;
        return y + "-" + mm + "-" + dd;
    }
}
