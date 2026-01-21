package com.example.workconnect.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.workconnect.models.ShiftTemplate;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class ShiftRepository {

    public interface SimpleCallback {
        void onComplete(boolean success, String message);
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public LiveData<List<ShiftTemplate>> getShiftTemplates(String companyId, String teamId) {
        MutableLiveData<List<ShiftTemplate>> live = new MutableLiveData<>(new ArrayList<>());

        db.collection("companies").document(companyId)
                .collection("teams").document(teamId)
                .collection("shiftTemplates")
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) {
                        live.postValue(new ArrayList<>());
                        return;
                    }

                    List<ShiftTemplate> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        ShiftTemplate t = doc.toObject(ShiftTemplate.class);
                        if (t != null) {
                            t.setId(doc.getId());
                            list.add(t);
                        }
                    }
                    live.postValue(list);
                });

        return live;
    }

    public void addShiftTemplate(String companyId, String teamId, ShiftTemplate template, SimpleCallback cb) {
        String id = db.collection("companies").document(companyId)
                .collection("teams").document(teamId)
                .collection("shiftTemplates").document().getId();

        template.setId(id);

        db.collection("companies").document(companyId)
                .collection("teams").document(teamId)
                .collection("shiftTemplates").document(id)
                .set(template)
                .addOnCompleteListener(t -> cb.onComplete(t.isSuccessful(), t.isSuccessful() ? "Added" : "Failed to add"));
    }

    public void updateShiftTemplate(String companyId, String teamId, ShiftTemplate template, SimpleCallback cb) {
        if (template.getId() == null) {
            cb.onComplete(false, "Template id is missing");
            return;
        }

        db.collection("companies").document(companyId)
                .collection("teams").document(teamId)
                .collection("shiftTemplates").document(template.getId())
                .set(template)
                .addOnCompleteListener(t -> cb.onComplete(t.isSuccessful(), t.isSuccessful() ? "Updated" : "Failed to update"));
    }

    public void deleteShiftTemplate(String companyId, String teamId, String templateId, SimpleCallback cb) {
        db.collection("companies").document(companyId)
                .collection("teams").document(teamId)
                .collection("shiftTemplates").document(templateId)
                .delete()
                .addOnCompleteListener(t -> cb.onComplete(t.isSuccessful(), t.isSuccessful() ? "Deleted" : "Failed to delete"));
    }
}
