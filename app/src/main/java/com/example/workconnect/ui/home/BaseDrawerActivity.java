package com.example.workconnect.ui.home;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.workconnect.R;
import com.example.workconnect.ui.attendance.AttendanceActivity;
import com.example.workconnect.models.Call;
import com.example.workconnect.repository.CallRepository;
import com.example.workconnect.ui.chat.CallActivity;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.firestore.ListenerRegistration;
import com.example.workconnect.ui.auth.EditEmployeeProfileActivity;
import com.example.workconnect.ui.auth.LoginActivity;
import com.example.workconnect.ui.auth.PendingEmployeesActivity;
import com.example.workconnect.ui.auth.TeamsActivity;
import com.example.workconnect.ui.chat.ChatListActivity;
import com.example.workconnect.ui.notifications.NotificationsActivity;
import com.example.workconnect.ui.shifts.MyShiftsActivity;
import com.example.workconnect.ui.shifts.ScheduleShiftsActivity;
import com.example.workconnect.ui.shifts.ShiftReplacementActivity;
import com.example.workconnect.ui.shifts.SwapApprovalsActivity;
import com.example.workconnect.ui.vacations.PendingVacationRequestsActivity;
import com.example.workconnect.ui.vacations.VacationRequestsActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.badge.BadgeUtils;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Locale;
import android.view.Menu;
import android.view.MenuItem;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.annotation.Nullable;

import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.badge.BadgeUtils;
import com.google.firebase.firestore.ListenerRegistration;

/**
 * Base activity that provides:
 * - Navigation drawer
 * - Top toolbar with notifications badge
 * - Incoming call listener (global)
 *
 * All main screens inherit from this class.
 */

public abstract class BaseDrawerActivity extends AppCompatActivity {

    protected DrawerLayout drawerLayout;
    protected NavigationView navView;
    protected MaterialToolbar toolbar;

    protected FirebaseAuth mAuth;
    protected FirebaseFirestore db;

    // Cached user state (loaded once for drawer configuration)
    protected String cachedCompanyId = null;
    protected boolean cachedIsManager = false;
    protected String cachedEmploymentType = "";

    private ActionBarDrawerToggle toggle;
    
    // Incoming call management
    private CallRepository callRepository;
    private ListenerRegistration incomingCallListener;
    private BottomSheetDialog currentIncomingCallDialog;

    // 🔔 Notifications badge
    @Nullable private BadgeDrawable notifBadge;
    @Nullable private ListenerRegistration notifBadgeListener;

    private int lastUnreadCount = -1; // so we detect changes
    private boolean firstBadgeLoad = true;


    // Child layouts must include drawerLayout, navView and toolbar
    // This enforces a consistent layout structure across all screens
    @Override
    public void setContentView(@LayoutRes int layoutResID) {
        super.setContentView(layoutResID);

        drawerLayout = findViewById(R.id.drawerLayout);
        navView = findViewById(R.id.navView);
        toolbar = findViewById(R.id.toolbar);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        callRepository = new CallRepository();

        // Prevent crashes if a screen forgot to include drawer views
        if (drawerLayout == null || navView == null || toolbar == null) {
            throw new IllegalStateException("Layout must include drawerLayout, navView, and toolbar");
        }

        setSupportActionBar(toolbar);

        // Client-side status handling to avoid composite index
        // Calls are auto-cleaned after ending, so stale docs are not an issue
        setupIncomingCallListener();

        // Connect DrawerLayout with Toolbar to show hamburger icon
        toggle = new ActionBarDrawerToggle(
                this,
                drawerLayout,
                toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close
        );

        toggle.getDrawerArrowDrawable().setColor(
                ContextCompat.getColor(this, R.color.black)
        );

        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        // Hide management until role is loaded
        navView.getMenu().setGroupVisible(R.id.group_management, false);

        setupDrawerMenu();
        loadRoleAndCompanyStateForDrawer();
    }

    @androidx.annotation.OptIn(
            markerClass = com.google.android.material.badge.ExperimentalBadgeUtils.class
    )
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.topbar_menu, menu);

        notifBadge = BadgeDrawable.create(this);
        notifBadge.setVisible(false);

        BadgeUtils.attachBadgeDrawable(notifBadge, toolbar, R.id.action_notifications);

        startUnreadBadgeListener();
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_notifications) {
            startActivity(new Intent(this, com.example.workconnect.ui.notifications.NotificationsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Realtime listener for unread notifications count (badge update)
    private void startUnreadBadgeListener() {
        if (mAuth.getCurrentUser() == null) return;

        String uid = mAuth.getCurrentUser().getUid();
        android.util.Log.d("BaseDrawer", "BaseDrawer uid=" + uid);

        if (notifBadgeListener != null) {
            notifBadgeListener.remove();
            notifBadgeListener = null;
        }

        notifBadgeListener = db.collection("users")
                .document(uid)
                .collection("notifications")
                .whereEqualTo("read", false)
                .addSnapshotListener((snap, e) -> {
                    if (notifBadge == null) return;

                    if (e != null || snap == null) {
                        notifBadge.clearNumber();
                        notifBadge.setVisible(false);
                        return;
                    }

                    int count = snap.size();
                    android.util.Log.d("BaseDrawer", "unreadCount=" + count);

                    if (count <= 0) {
                        notifBadge.clearNumber();
                        notifBadge.setVisible(false);
                    } else {
                        notifBadge.setNumber(Math.min(count, 99));
                        notifBadge.setVisible(true);
                    }

                    if (!firstBadgeLoad && lastUnreadCount >= 0 && count > lastUnreadCount) {
                        animateBell();
                    }

                    firstBadgeLoad = false;
                    lastUnreadCount = count;
                });
    }

    // Small animation when unread count increases
    private void animateBell() {
        if (toolbar == null) return;

        View bellView = toolbar.findViewById(R.id.action_notifications);
        // sometimes menu item view isn't directly found; fallback to whole toolbar
        View target = (bellView != null) ? bellView : toolbar;

        Animation anim = AnimationUtils.loadAnimation(this, R.anim.bell_bounce);
        target.startAnimation(anim);
    }


    private void setupDrawerMenu() {
        navView.setNavigationItemSelectedListener(item -> {

            if (item.hasSubMenu()) {
                return true;
            }

            int id = item.getItemId();

            // Close drawer first, then navigate
            drawerLayout.closeDrawers();

            new Handler(Looper.getMainLooper()).post(() -> handleMenuClick(id));
            return true;
        });
    }

    // Centralized navigation handling for drawer items
    // Uses cached role/company state to control access
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
            Intent i = new Intent(this, MyShiftsActivity.class);
            if (cachedCompanyId != null) i.putExtra("companyId", cachedCompanyId);
            i.putExtra("employmentType", cachedEmploymentType);
            startActivity(i);
            return;
        }

        if (id == R.id.nav_shift_replacement) {
            Intent i = new Intent(this, ShiftReplacementActivity.class);
            if (cachedCompanyId != null) i.putExtra("companyId", cachedCompanyId);
            startActivity(i);
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

        // handle nav_company_settings_general (inner item)
        if (id == R.id.nav_company_settings_general) {
            if (!cachedIsManager) return;
            Intent i = new Intent(this, com.example.workconnect.ui.company.CompanySettingsActivity.class);
            if (cachedCompanyId != null) i.putExtra("companyId", cachedCompanyId);
            startActivity(i);
            return;
        }

        // Logout
        if (id == R.id.nav_logout) {
            stopAllListeners();
            FirebaseAuth.getInstance().signOut();
            Intent i = new Intent(this, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            finish();
            return;
        }

        // Attendance
        if (id == R.id.nav_attendance) {
            Intent i = new Intent(this, AttendanceActivity.class);
            if (cachedCompanyId != null) i.putExtra("companyId", cachedCompanyId);
            startActivity(i);
            return;
        }

        // Placeholder items
        if (id == R.id.nav_manage_attendance || id == R.id.nav_salary_slips) {
            Toast.makeText(this, "TODO", Toast.LENGTH_SHORT).show();
        }
    }


    // Loads user role + company info once to configure drawer UI
    // Avoids repeated Firestore calls when navigating
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

                    cachedEmploymentType = doc.getString("employmentType");
                    if (cachedEmploymentType == null) cachedEmploymentType = "";

                    // show management
                    navView.getMenu().setGroupVisible(R.id.group_management, cachedIsManager);

                    // header set name immediately
                    String fullName = doc.getString("fullName");
                    updateDrawerHeader(fullName, "-");

                    // fetch company name by companyId
                    if (cachedCompanyId != null) {
                        db.collection("companies")
                                .document(cachedCompanyId)
                                .get()
                                .addOnSuccessListener(cDoc -> {
                                    String companyName = null;
                                    if (cDoc != null && cDoc.exists()) {
                                        companyName = cDoc.getString("name");
                                        if (companyName == null) companyName = cDoc.getString("companyName");
                                    }
                                    updateDrawerHeader(fullName, companyName);
                                })
                                .addOnFailureListener(e -> updateDrawerHeader(fullName, "-"));
                    }

                    onCompanyStateLoaded();
                });
    }

    protected void onCompanyStateLoaded() {
        // subclasses may override
    }

    protected void updateDrawerHeader(String fullName, String companyName) {
        View header = navView.getHeaderView(0);
        if (header == null) return;

        TextView tvName = header.findViewById(R.id.tv_header_name);
        TextView tvCompany = header.findViewById(R.id.tv_header_company);

        if (tvName != null) tvName.setText(fullName == null || fullName.trim().isEmpty() ? "-" : fullName.trim());
        if (tvCompany != null) tvCompany.setText(companyName == null || companyName.trim().isEmpty() ? "-" : companyName.trim());
    }

    // Start global incoming-call listener
    private void setupIncomingCallListener() {
        if (mAuth.getCurrentUser() == null) return;
        
        String currentUserId = mAuth.getCurrentUser().getUid();
        
        // Listen to all calls where user is a participant (no status filter to avoid composite index).
        // Status filtering is done client-side below.
        // Documents are deleted 2 seconds after ending, so stale results are not an issue.
        incomingCallListener = db.collection("calls")
            .whereArrayContains("participants", currentUserId)
            .addSnapshotListener((snapshot, e) -> {
                if (e != null) {
                    android.util.Log.e("BaseDrawerActivity", "Error listening to calls", e);
                    return;
                }

                if (snapshot == null) return;

                
                // Process each call
                for (com.google.firebase.firestore.DocumentSnapshot doc : snapshot.getDocuments()) {
                    Call call = doc.toObject(Call.class);
                    if (call == null) continue;

                    call.setCallId(doc.getId());
                    String status = call.getStatus();

                    // Skip calls initiated by me
                    if (call.getCallerId().equals(currentUserId)) continue;

                    if ("ringing".equals(status)) {
                        // New incoming call: show dialog only if we are NOT already in a call.
                        if (!com.example.workconnect.ui.chat.CallActivity.isInCall) {
                            runOnUiThread(() -> showIncomingCallDialog(call));
                        }
                        // If already in a call, silently ignore — the caller's dialog will eventually
                        // time out or they can cancel.
                    } else if ("cancelled".equals(status) || "ended".equals(status) || "missed".equals(status)) {
                        // Call terminated: close dialog if open
                        runOnUiThread(() -> {
                            if (currentIncomingCallDialog != null && currentIncomingCallDialog.isShowing()) {
                                currentIncomingCallDialog.dismiss();
                                currentIncomingCallDialog = null;
                            }
                        });
                    }

                }
            });
    }
    
    /**
     * BottomSheet dialog for incoming calls
     * Guarded by CallActivity.isInCall to prevent duplicate UI
     */
    private void showIncomingCallDialog(Call call) {
        // Double-check with the static flag
        if (com.example.workconnect.ui.chat.CallActivity.isInCall) {
            android.util.Log.d("BaseDrawerActivity", "Ignoring incoming call — already in a call");
            return;
        }

        {
            // Close any existing incoming call dialog
            if (currentIncomingCallDialog != null && currentIncomingCallDialog.isShowing()) {
                currentIncomingCallDialog.dismiss();
                currentIncomingCallDialog = null;
            }
            
            // Create a dialog for incoming call
            BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
            currentIncomingCallDialog = bottomSheet;
            View view = LayoutInflater.from(this).inflate(R.layout.dialog_incoming_call, null);
            bottomSheet.setContentView(view);
            bottomSheet.setCancelable(false); // Cannot dismiss by clicking outside
        
        TextView tvCallerName = view.findViewById(R.id.tv_caller_name);
        TextView tvCallType = view.findViewById(R.id.tv_call_type);
        ImageView ivCallIcon = view.findViewById(R.id.iv_call_icon);
        Button btnAccept = view.findViewById(R.id.btn_accept);
        Button btnDecline = view.findViewById(R.id.btn_decline);
        
        // Load caller name
        String callerId = call.getCallerId();
        db.collection("users").document(callerId)
            .get()
            .addOnSuccessListener(doc -> {
                String firstName = doc.getString("firstName");
                String lastName = doc.getString("lastName");
                String fullName = doc.getString("fullName");
                String name = fullName != null ? fullName : 
                    (firstName != null ? firstName + " " + (lastName != null ? lastName : "") : "Unknown");
                tvCallerName.setText(name.trim());
            });
        
        // Set call type and icon
        if (call.isVideoCall()) {
            tvCallType.setText("Incoming video call...");
            ivCallIcon.setImageResource(R.drawable.ic_call_video);
        } else {
            tvCallType.setText("Incoming audio call...");
            ivCallIcon.setImageResource(R.drawable.ic_call_audio);
        }
        
        btnAccept.setOnClickListener(v -> {
            bottomSheet.dismiss();
            // Update call status to active when accepting
            callRepository.updateCallStatus(call.getCallId(), "active");
            Intent intent = new Intent(this, CallActivity.class);
            intent.putExtra("callId", call.getCallId());
            intent.putExtra("conversationId", call.getConversationId());
            intent.putExtra("callType", call.getType());
            intent.putExtra("isCaller", false);
            boolean isGroupCall = call.getParticipants() != null && call.getParticipants().size() > 2;
            intent.putExtra("isGroupCall", isGroupCall);
            startActivity(intent);
        });
        
        btnDecline.setOnClickListener(v -> {
            bottomSheet.dismiss();
            // Mark call as missed
            callRepository.updateCallStatus(call.getCallId(), "missed");
        });
        
        // Dismiss dialog only on terminal states (ended/cancelled/missed) or document deletion.
        ListenerRegistration callStatusListener = callRepository.listenToCall(call.getCallId(), updatedCall -> {
            runOnUiThread(() -> {
                if (!bottomSheet.isShowing()) return;
                if (updatedCall == null) {
                    // Document deleted - call is fully over
                    bottomSheet.dismiss();
                    return;
                }
                String s = updatedCall.getStatus();
                if ("ended".equals(s) || "cancelled".equals(s) || "missed".equals(s)) {
                    bottomSheet.dismiss();
                }
                // "ringing" or "active" - keep dialog open
            });
        });
        
        // Remove listener when dialog is dismissed
        bottomSheet.setOnDismissListener(dialog -> {
            if (callStatusListener != null) {
                callStatusListener.remove();
            }
            currentIncomingCallDialog = null;
        });
        
        bottomSheet.show();
        }
    }

    // Clean up all active Firestore listeners to prevent leaks
    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Remove incoming call listener
        if (incomingCallListener != null) {
            incomingCallListener.remove();
            incomingCallListener = null;
        }

        // Dismiss any showing dialog
        if (currentIncomingCallDialog != null && currentIncomingCallDialog.isShowing()) {
            currentIncomingCallDialog.dismiss();
            currentIncomingCallDialog = null;
        }

        // Remove notification badge listener
        if (notifBadgeListener != null) {
            notifBadgeListener.remove();
            notifBadgeListener = null;
        }
    }

    private void stopAllListeners() {
        if (notifBadgeListener != null) {
            notifBadgeListener.remove();
            notifBadgeListener = null;
        }
        if (incomingCallListener != null) {
            incomingCallListener.remove();
            incomingCallListener = null;
        }
    }
}
