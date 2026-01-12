package com.example.workconnect.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.workconnect.models.MyShiftItem;
import com.example.workconnect.models.ShiftAssignment;
import com.example.workconnect.models.ShiftTemplate;
import com.example.workconnect.models.Team;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MyShiftsRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // Cache templates per team to convert templateId -> title/time
    private final Map<String, Map<String, ShiftTemplate>> teamTemplateCache = new HashMap<>();

    public LiveData<List<MyShiftItem>> listenMyShiftsForRange(
            @NonNull String companyId,
            @NonNull List<String> teamIds,
            @NonNull Map<String, String> teamIdToName,
            @NonNull String userUid,
            @NonNull List<String> dateKeysInRange // list of yyyy-MM-dd
    ) {
        MutableLiveData<List<MyShiftItem>> live = new MutableLiveData<>(new ArrayList<>());

        if (teamIds.isEmpty() || dateKeysInRange.isEmpty()) {
            live.postValue(new ArrayList<>());
            return live;
        }

        // We will attach one snapshot listener per (teamId, dateKey) for simplicity.
        // For weekly view this is small and stable.
        final Map<String, List<MyShiftItem>> bucket = new HashMap<>();
        final int totalListeners = teamIds.size() * dateKeysInRange.size();
        final int[] readyCount = {0};

        for (String teamId : teamIds) {
            // Ensure templates are cached/listened for this team
            listenTemplatesForTeam(companyId, teamId);

            for (String dateKey : dateKeysInRange) {
                db.collection("companies").document(companyId)
                        .collection("teams").document(teamId)
                        .collection("assignments").document(dateKey)
                        .collection("items") // IMPORTANT: must match your assignmentRepo structure
                        .addSnapshotListener((snap, e) -> {
                            String key = teamId + "|" + dateKey;

                            if (e != null || snap == null) {
                                bucket.put(key, new ArrayList<>());
                            } else {
                                List<MyShiftItem> mine = new ArrayList<>();

                                for (DocumentSnapshot doc : snap.getDocuments()) {
                                    ShiftAssignment a = doc.toObject(ShiftAssignment.class);
                                    if (a == null) continue;

                                    String uid = a.getUserId();
                                    if (uid == null || !uid.equals(userUid)) continue;

                                    String templateId = a.getTemplateId();
                                    ShiftTemplate t = getTemplateFromCache(teamId, templateId);

                                    String teamName = teamIdToName.get(teamId);
                                    if (teamName == null) teamName = teamId;

                                    String title = (t != null && t.getTitle() != null) ? t.getTitle() : "Shift";
                                    int start = (t != null) ? t.getStartHour() : 0;
                                    int end = (t != null) ? t.getEndHour() : 0;

                                    mine.add(new MyShiftItem(
                                            dateKey,
                                            teamId,
                                            teamName,
                                            templateId,
                                            title,
                                            start,
                                            end
                                    ));
                                }
                                bucket.put(key, mine);
                            }

                            // first wave: count readiness so UI gets initial data quickly
                            if (readyCount[0] < totalListeners) readyCount[0]++;

                            // Merge all buckets into one list and post
                            ArrayList<MyShiftItem> merged = new ArrayList<>();
                            for (List<MyShiftItem> list : bucket.values()) merged.addAll(list);

                            // Sort by date then start hour (simple stable ordering)
                            merged.sort((a, b) -> {
                                int d = safeStr(a.getDateKey()).compareTo(safeStr(b.getDateKey()));
                                if (d != 0) return d;
                                int s = Integer.compare(a.getStartHour(), b.getStartHour());
                                if (s != 0) return s;
                                return safeStr(a.getTeamName()).compareTo(safeStr(b.getTeamName()));
                            });

                            live.postValue(merged);
                        });
            }
        }

        return live;
    }

    private void listenTemplatesForTeam(String companyId, String teamId) {
        if (teamTemplateCache.containsKey(teamId)) return;

        teamTemplateCache.put(teamId, new HashMap<>());

        db.collection("companies").document(companyId)
                .collection("teams").document(teamId)
                .collection("shiftTemplates")
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) return;

                    Map<String, ShiftTemplate> map = teamTemplateCache.get(teamId);
                    if (map == null) return;

                    map.clear();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        ShiftTemplate t = doc.toObject(ShiftTemplate.class);
                        if (t != null) {
                            t.setId(doc.getId());
                            map.put(doc.getId(), t);
                        }
                    }
                });
    }

    private ShiftTemplate getTemplateFromCache(String teamId, String templateId) {
        if (templateId == null) return null;
        Map<String, ShiftTemplate> map = teamTemplateCache.get(teamId);
        if (map == null) return null;
        return map.get(templateId);
    }
    public LiveData<List<MyShiftItem>> listenFullTimeForRange(
            @NonNull String companyId,
            @NonNull List<String> teamIds,
            @NonNull Map<String, String> teamIdToName,
            @NonNull List<String> dateKeysInRange
    ) {
        MutableLiveData<List<MyShiftItem>> live = new MutableLiveData<>(new ArrayList<>());

        if (teamIds.isEmpty() || dateKeysInRange.isEmpty()) {
            live.postValue(new ArrayList<>());
            return live;
        }

        final Map<String, List<MyShiftItem>> bucket = new HashMap<>();

        for (String teamId : teamIds) {
            FirebaseFirestore.getInstance()
                    .collection("companies").document(companyId)
                    .collection("teams").document(teamId)
                    .addSnapshotListener((doc, e) -> {

                        if (e != null || doc == null || !doc.exists()) {
                            bucket.put(teamId, new ArrayList<>());
                        } else {
                            Team t = doc.toObject(Team.class);
                            ShiftTemplate ft = (t != null) ? t.getFullTimeTemplate() : null;

                            // ✅ days-of-week filter (1=Sun .. 7=Sat)
                            List<Integer> allowedDays = (t != null) ? t.getFullTimeDays() : null;

                            String teamName = teamIdToName.get(teamId);
                            if (teamName == null) teamName = teamId;

                            ArrayList<MyShiftItem> list = new ArrayList<>();

                            if (ft != null && ft.isEnabled()) {
                                String title = (ft.getTitle() == null || ft.getTitle().trim().isEmpty())
                                        ? "Full-time"
                                        : ft.getTitle();

                                int start = ft.getStartHour();
                                int end = ft.getEndHour();

                                for (String dateKey : dateKeysInRange) {
                                    if (allowedDays != null && !allowedDays.isEmpty()) {
                                        int dow = dayOfWeekFromDateKey(dateKey); // 1..7
                                        if (!allowedDays.contains(dow)) continue; // skip disallowed days
                                    }

                                    list.add(new MyShiftItem(
                                            dateKey,
                                            teamId,
                                            teamName,
                                            "FULL_TIME",
                                            title,
                                            start,
                                            end
                                    ));
                                }
                            }

                            bucket.put(teamId, list);
                        }

                        ArrayList<MyShiftItem> merged = new ArrayList<>();
                        for (List<MyShiftItem> l : bucket.values()) merged.addAll(l);

                        merged.sort((a, b) -> {
                            int d = safeStr(a.getDateKey()).compareTo(safeStr(b.getDateKey()));
                            if (d != 0) return d;
                            int s = Integer.compare(a.getStartHour(), b.getStartHour());
                            if (s != 0) return s;
                            return safeStr(a.getTeamName()).compareTo(safeStr(b.getTeamName()));
                        });

                        live.postValue(merged);
                    });
        }

        return live;
    }

    private int dayOfWeekFromDateKey(String dateKey) {
        // dateKey = yyyy-MM-dd
        if (dateKey == null) return Calendar.SUNDAY;

        String[] p = dateKey.split("-");
        if (p.length != 3) return Calendar.SUNDAY;

        int y, m, d;
        try {
            y = Integer.parseInt(p[0]);
            m = Integer.parseInt(p[1]); // 1..12
            d = Integer.parseInt(p[2]);
        } catch (Exception ex) {
            return Calendar.SUNDAY;
        }

        Calendar c = Calendar.getInstance();
        c.set(Calendar.YEAR, y);
        c.set(Calendar.MONTH, m - 1);
        c.set(Calendar.DAY_OF_MONTH, d);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);

        return c.get(Calendar.DAY_OF_WEEK); // 1..7 (Sun..Sat)
    }

    private String safeStr(String s) { return s == null ? "" : s; }
}
