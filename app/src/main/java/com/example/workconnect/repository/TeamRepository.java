package com.example.workconnect.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.workconnect.models.Team;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

public class TeamRepository {

    public interface CreateTeamCallback {
        void onComplete(boolean success, String message, String teamId);
    }

    public interface SimpleCallback {
        void onComplete(boolean success, String message);
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public LiveData<List<Team>> getTeamsForCompany(String companyId) {
        MutableLiveData<List<Team>> live = new MutableLiveData<>(new ArrayList<>());

        db.collection("companies")
                .document(companyId)
                .collection("teams")
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) {
                        live.postValue(new ArrayList<>());
                        return;
                    }

                    List<Team> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        Team t = doc.toObject(Team.class);
                        t.setId(doc.getId());
                        if (t != null) {
                            t.setId(doc.getId());
                            list.add(t);
                        }
                    }
                    live.postValue(list);
                });

        return live;
    }

    public void createTeam(String companyId, String teamName, String creatorUid, CreateTeamCallback cb) {
        String teamId = db.collection("companies")
                .document(companyId)
                .collection("teams")
                .document()
                .getId();

        HashMap<String, Object> data = new HashMap<>();
        data.put("companyId", companyId);
        data.put("name", teamName);
        data.put("description", "");
        data.put("periodType", "WEEKLY");

        // per-team full time template (can be null until manager sets it)
        data.put("fullTimeTemplate", null);

        ArrayList<String> members = new ArrayList<>();
        members.add(creatorUid);
        data.put("memberIds", members);

        WriteBatch batch = db.batch();
        ArrayList<Integer> days = new ArrayList<>();
        days.add(Calendar.SUNDAY);
        days.add(Calendar.MONDAY);
        days.add(Calendar.TUESDAY);
        days.add(Calendar.WEDNESDAY);
        days.add(Calendar.THURSDAY);
        data.put("fullTimeDays", days);
        // teams/{teamId}
        var teamRef = db.collection("companies").document(companyId)
                .collection("teams").document(teamId);
        batch.set(teamRef, data);

        // users/{creatorUid}.teamIds += teamId
        var userRef = db.collection("users").document(creatorUid);
        batch.update(userRef, "teamIds", FieldValue.arrayUnion(teamId));

        batch.commit()
                .addOnSuccessListener(unused -> cb.onComplete(true, "Team created", teamId))
                .addOnFailureListener(e -> cb.onComplete(false, "Failed to create team", null));
    }

    public void updateTeamName(String companyId, String teamId, String newName, SimpleCallback cb) {
        db.collection("companies").document(companyId)
                .collection("teams").document(teamId)
                .update("name", newName)
                .addOnSuccessListener(unused -> cb.onComplete(true, "Updated"))
                .addOnFailureListener(e -> cb.onComplete(false, "Failed"));
    }

    /**
     * Sets the team member list and syncs users' teamIds.
     * - team.memberIds becomes newMemberIds
     * - for each user:
     *     if added -> arrayUnion(teamId)
     *     if removed -> arrayRemove(teamId)
     */
    public void setTeamMembers(String companyId, String teamId, List<String> newMemberIds, SimpleCallback cb) {
        var teamRef = db.collection("companies").document(companyId)
                .collection("teams").document(teamId);

        teamRef.get().addOnSuccessListener(doc -> {

            List<String> old = (List<String>) doc.get("memberIds");
            if (old == null) old = new ArrayList<>();

            // compute diffs
            ArrayList<String> added = new ArrayList<>();
            ArrayList<String> removed = new ArrayList<>();

            for (String uid : newMemberIds) {
                if (!old.contains(uid)) added.add(uid);
            }
            for (String uid : old) {
                if (!newMemberIds.contains(uid)) removed.add(uid);
            }

            WriteBatch batch = db.batch();

            batch.update(teamRef, "memberIds", new ArrayList<>(newMemberIds));

            for (String uid : added) {
                batch.update(db.collection("users").document(uid), "teamIds", FieldValue.arrayUnion(teamId));
            }
            for (String uid : removed) {
                batch.update(db.collection("users").document(uid), "teamIds", FieldValue.arrayRemove(teamId));
            }

            batch.commit()
                    .addOnSuccessListener(unused -> cb.onComplete(true, "Saved"))
                    .addOnFailureListener(e -> cb.onComplete(false, "Failed to save members"));

        }).addOnFailureListener(e -> cb.onComplete(false, "Failed to load team"));
    }
}
