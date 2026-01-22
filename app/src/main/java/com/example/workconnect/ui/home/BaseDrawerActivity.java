package com.example.workconnect.ui.home;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.LayoutRes;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.workconnect.R;
import com.example.workconnect.ui.auth.EditEmployeeProfileActivity;
import com.example.workconnect.ui.auth.LoginActivity;
import com.example.workconnect.ui.auth.PendingEmployeesActivity;
import com.example.workconnect.ui.auth.TeamsActivity;
import com.example.workconnect.ui.chat.ChatListActivity;
import com.example.workconnect.ui.shifts.MyShiftsActivity;
import com.example.workconnect.ui.shifts.ScheduleShiftsActivity;
import com.example.workconnect.ui.shifts.SwapApprovalsActivity;
import com.example.workconnect.ui.vacations.PendingVacationRequestsActivity;
import com.example.workconnect.ui.vacations.VacationRequestsActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Locale;

public abstract class BaseDrawerActivity extends AppCompatActivity {

    protected DrawerLayout drawerLayout;
    protected NavigationView navView;
    protected MaterialToolbar toolbar;

    protected FirebaseAuth mAuth;
    protected FirebaseFirestore db;

    protected String cachedCompanyId = null;
    protected boolean cachedIsManager = false;

    private ActionBarDrawerToggle toggle;

    @Override
    public void setContentView(@LayoutRes int layoutResID) {
        super.setContentView(layoutResID);

        // NOTE: Child layout must include these IDs: drawerLayout, navView, toolbar
        drawerLayout = findViewById(R.id.drawerLayout);
        navView = findViewById(R.id.navView);
        toolbar = findViewById(R.id.toolbar);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // NOTE: Prevent crashes if a screen forgot to include drawer views
        if (drawerLayout == null || navView == null || toolbar == null) {
            throw new IllegalStateException("Layout must include drawerLayout, navView, and toolbar");
        }

        setSupportActionBar(toolbar);

        // NOTE: Connect DrawerLayout with Toolbar to show hamburger icon
        toggle = new ActionBarDrawerToggle(
                this,
                drawerLayout,
                toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close
        );

        // NOTE: Force hamburger/arrow color (fixes black icon)
        toggle.getDrawerArrowDrawable().setColor(
                ContextCompat.getColor(this, R.color.primaryBlue)
        );

        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        // (optional) if you want black:
        toggle.getDrawerArrowDrawable().setColor(getResources().getColor(android.R.color.black));

        // NOTE: Hide management until role is loaded
        navView.getMenu().setGroupVisible(R.id.group_management, false);

        setupDrawerMenu();
        loadRoleAndCompanyStateForDrawer();
    }

    private void setupDrawerMenu() {
        navView.setNavigationItemSelectedListener(item -> {

            // ✅ FIX: If the item has a submenu (like "Management" or "Company settings"),
            // don't close the drawer. Let Android expand/collapse the submenu.
            if (item.hasSubMenu()) {
                return true;
            }

            int id = item.getItemId();

            // NOTE: Close drawer first, then navigate
            drawerLayout.closeDrawers();

            new Handler(Looper.getMainLooper()).post(() -> handleMenuClick(id));
            return true;
        });
    }

    private void handleMenuClick(int id) {

        // Profile
        if (id == R.id.nav_profile) {
            if (!(this instanceof HomeActivity)) {
                startActivity(new Intent(this, HomeActivity.class));
            }
            return;
        }

        // Vacations
        if (id == R.id.nav_vacations) {
            if (!(this instanceof VacationRequestsActivity)) {
                startActivity(new Intent(this, VacationRequestsActivity.class));
            }
            return;
        }

        // Chat
        if (id == R.id.nav_chat) {
            Intent i = new Intent(this, ChatListActivity.class);
            if (cachedCompanyId != null) i.putExtra("companyId", cachedCompanyId);
            startActivity(i);
            return;
        }

        // Management items - only for managers
        if (id == R.id.nav_approve_users) {
            if (!cachedIsManager) return;
            Intent i = new Intent(this, PendingEmployeesActivity.class);
            if (cachedCompanyId != null) i.putExtra("companyId", cachedCompanyId);
            startActivity(i);
            return;
        }

        if (id == R.id.nav_vacation_requests) {
            if (!cachedIsManager) return;
            Intent i = new Intent(this, PendingVacationRequestsActivity.class);
            if (cachedCompanyId != null) i.putExtra("companyId", cachedCompanyId);
            startActivity(i);
            return;
        }

        if (id == R.id.nav_shifts) {
            openMyShifts();
            return;
        }

        if (id == R.id.nav_shift_replacement) {
            openShiftReplacement();
            return;
        }

        if (id == R.id.nav_manage_shifts) {
            if (!cachedIsManager) return;
            Intent i = new Intent(this, ScheduleShiftsActivity.class);
            if (cachedCompanyId != null) i.putExtra("companyId", cachedCompanyId);
            startActivity(i);
            return;
        }

        if (id == R.id.nav_swap_approvals) {
            if (!cachedIsManager) return;
            Intent i = new Intent(this, SwapApprovalsActivity.class);
            if (cachedCompanyId != null) i.putExtra("companyId", cachedCompanyId);
            startActivity(i);
            return;
        }

        // Company settings -> submenu items
        if (id == R.id.nav_company_groups) {
            if (!cachedIsManager) return;
            Intent i = new Intent(this, TeamsActivity.class);
            if (cachedCompanyId != null) i.putExtra("companyId", cachedCompanyId);
            startActivity(i);
            return;
        }

        if (id == R.id.nav_edit_employee) {
            if (!cachedIsManager) return;
            Intent i = new Intent(this, EditEmployeeProfileActivity.class);
            if (cachedCompanyId != null) i.putExtra("companyId", cachedCompanyId);
            startActivity(i);
            return;
        }

        // ✅ FIX: handle nav_company_settings_general (inner item)
        if (id == R.id.nav_company_settings_general) {
            if (!cachedIsManager) return;
            Toast.makeText(this, "TODO: Company settings screen", Toast.LENGTH_SHORT).show();
            return;
        }

        // Logout
        if (id == R.id.nav_logout) {
            FirebaseAuth.getInstance().signOut();
            Intent i = new Intent(this, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            finish();
            return;
        }

        // Placeholder items
        if (id == R.id.nav_attendance || id == R.id.nav_tasks || id == R.id.nav_video
                || id == R.id.nav_manage_attendance || id == R.id.nav_salary_slips) {
            Toast.makeText(this, "TODO", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadRoleAndCompanyStateForDrawer() {
        if (mAuth.getCurrentUser() == null) return;

        String uid = mAuth.getCurrentUser().getUid();

        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc == null || !doc.exists()) return;

                    String role = doc.getString("role");
                    role = role == null ? "" : role.toLowerCase(Locale.ROOT);
                    cachedIsManager = role.equals("manager");

                    cachedCompanyId = doc.getString("companyId");
                    if (cachedCompanyId != null && cachedCompanyId.trim().isEmpty()) cachedCompanyId = null;

                    // NOTE: Show management group only for managers
                    navView.getMenu().setGroupVisible(R.id.group_management, cachedIsManager);

                    // NOTE: Update header (name + company)
                    updateDrawerHeader(doc.getString("fullName"), doc.getString("companyName"));
                });
    }

    protected void updateDrawerHeader(String fullName, String companyName) {
        View header = navView.getHeaderView(0);
        if (header == null) return;

        TextView tvName = header.findViewById(R.id.tv_header_name);
        TextView tvCompany = header.findViewById(R.id.tv_header_company);

        if (tvName != null) tvName.setText(fullName == null || fullName.trim().isEmpty() ? "-" : fullName.trim());
        if (tvCompany != null) tvCompany.setText(companyName == null || companyName.trim().isEmpty() ? "-" : companyName.trim());
    }

    private void openMyShifts() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    String companyId = doc.getString("companyId");

                    Intent i = new Intent(this, MyShiftsActivity.class);
                    i.putExtra("companyId", companyId == null ? "" : companyId);
                    startActivity(i);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show()
                );
    }

    private void openShiftReplacement() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    String empType = doc.getString("employmentType");
                    if (empType == null) empType = "";

                    String cId = doc.getString("companyId");
                    if (cId == null) cId = "";

                    // optional: update cached company for chat etc.
                    cachedCompanyId = cId.isEmpty() ? cachedCompanyId : cId;

                    if ("FULL_TIME".equals(empType)) {
                        new android.app.AlertDialog.Builder(this)
                                .setTitle("Shift Replacement")
                                .setMessage("FULL_TIME workers should speak to their manager regarding shift issues")
                                .setPositiveButton("OK", (d, w) -> d.dismiss())
                                .show();
                        return;
                    }

                    Intent i = new Intent(this, com.example.workconnect.ui.shifts.ShiftReplacementActivity.class);
                    i.putExtra("companyId", cId);
                    startActivity(i);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show()
                );
    }
}
