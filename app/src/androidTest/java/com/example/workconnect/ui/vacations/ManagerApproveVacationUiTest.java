package com.example.workconnect.ui.vacations;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import android.view.View;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.PerformException;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.workconnect.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class ManagerApproveVacationUiTest {

    private static final String MANAGER_EMAIL = "shay@mid.com";
    private static final String MANAGER_PASSWORD = "123456";

    private static final String COLLECTION = "vacation_requests";

    @Before
    public void ensureLoggedInAsManager() throws Exception {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) return;

        final Object lock = new Object();
        final boolean[] done = {false};
        final Exception[] err = {null};

        auth.signInWithEmailAndPassword(MANAGER_EMAIL, MANAGER_PASSWORD)
                .addOnCompleteListener(task -> {
                    synchronized (lock) {
                        if (!task.isSuccessful()) {
                            err[0] = task.getException();
                        }
                        done[0] = true;
                        lock.notifyAll();
                    }
                });

        long deadline = System.currentTimeMillis() + 10_000;
        synchronized (lock) {
            while (!done[0] && System.currentTimeMillis() < deadline) {
                lock.wait(200);
            }
        }

        if (!done[0]) throw new AssertionError("Login timed out");
        if (err[0] != null) throw err[0];

        assertNotNull(auth.getCurrentUser());
    }

    @Test
    public void approveRequest_statusBecomesApproved_andItemRemoved() {

        ActivityScenario.launch(PendingVacationRequestsActivity.class);

        onView(withId(R.id.tv_title)).check(matches(isDisplayed()));
        onView(withId(R.id.rv_requests)).perform(waitForRecyclerViewMinItemCount(1, 8000));

        final int[] before = new int[1];
        onView(withId(R.id.rv_requests)).perform(getRecyclerViewItemCount(before));

        onView(withId(R.id.rv_requests))
                .perform(RecyclerViewActions.scrollToPosition(0));

        final String[] requestId = new String[1];
        onView(withId(R.id.rv_requests))
                .perform(getRequestIdFromItemAtPosition(0, requestId));

        // לחיצה על Approve
        onView(withId(R.id.rv_requests))
                .perform(RecyclerViewActions.actionOnItemAtPosition(
                        0,
                        clickChildViewWithId(R.id.btn_approve)
                ));

        // הרשימה קטנה
        onView(withId(R.id.rv_requests))
                .perform(waitForRecyclerViewExactItemCount(before[0] - 1, 10_000));

        // הסטטוס בפיירסטור נהיה APPROVED
        waitForFirestoreStatus(requestId[0], "APPROVED", 10_000);
    }

    // ===== Helpers =====

    private static ViewAction getRequestIdFromItemAtPosition(int position, String[] out) {
        return new ViewAction() {
            @Override public Matcher<View> getConstraints() { return allOf(isDisplayed()); }
            @Override public String getDescription() { return "Get requestId from itemView tag"; }
            @Override public void perform(UiController uiController, View view) {
                RecyclerView rv = (RecyclerView) view;
                RecyclerView.ViewHolder vh = rv.findViewHolderForAdapterPosition(position);
                if (vh == null) throw new AssertionError("ViewHolder is null");
                Object tag = vh.itemView.getTag();
                if (tag == null) throw new AssertionError("itemView tag is null");
                out[0] = tag.toString();
            }
        };
    }

    private static void waitForFirestoreStatus(String requestId, String expected, long timeoutMs) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        long start = System.currentTimeMillis();

        while (System.currentTimeMillis() - start < timeoutMs) {
            CountDownLatch latch = new CountDownLatch(1);
            final String[] value = new String[1];

            db.collection(COLLECTION).document(requestId).get()
                    .addOnSuccessListener(snap -> {
                        value[0] = snap.getString("status");
                        latch.countDown();
                    });

            try {
                latch.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}

            if (expected.equals(value[0])) return;

            try { Thread.sleep(250); } catch (InterruptedException ignored) {}
        }

        throw new AssertionError("status did not become " + expected);
    }

    // --- Recycler helpers ---

    private static ViewAction waitForRecyclerViewMinItemCount(int minCount, long timeoutMs) {
        return new ViewAction() {
            @Override public Matcher<View> getConstraints() { return allOf(isDisplayed()); }

            @Override public String getDescription() {
                return "Wait for RecyclerView itemCount >= " + minCount + " within " + timeoutMs + "ms";
            }

            @Override public void perform(UiController uiController, View view) {
                RecyclerView rv = (RecyclerView) view;
                long start = System.currentTimeMillis();

                while (true) {
                    RecyclerView.Adapter<?> adapter = rv.getAdapter();
                    int count = (adapter == null) ? 0 : adapter.getItemCount();

                    if (count >= minCount) return;

                    if (System.currentTimeMillis() - start > timeoutMs) {
                        throw new PerformException.Builder()
                                .withActionDescription(getDescription())
                                .withViewDescription("RecyclerView itemCount stayed " + count)
                                .build();
                    }

                    uiController.loopMainThreadForAtLeast(300);
                }
            }
        };
    }


    private static ViewAction getRecyclerViewItemCount(final int[] outCount) {
        return new ViewAction() {
            @Override public Matcher<View> getConstraints() { return allOf(isDisplayed()); }
            @Override public String getDescription() { return "Get item count"; }
            @Override public void perform(UiController uiController, View view) {
                RecyclerView rv = (RecyclerView) view;
                outCount[0] = rv.getAdapter() == null ? 0 : rv.getAdapter().getItemCount();
            }
        };
    }

    private static ViewAction clickChildViewWithId(int id) {
        return new ViewAction() {
            @Override public Matcher<View> getConstraints() { return allOf(isDisplayed()); }
            @Override public String getDescription() { return "Click child"; }
            @Override public void perform(UiController uiController, View view) {
                View v = view.findViewById(id);
                if (v == null) throw new AssertionError("No view");
                v.performClick();
            }
        };
    }

    private static ViewAction waitForRecyclerViewExactItemCount(int expected, long timeoutMs) {
        return new ViewAction() {
            @Override public Matcher<View> getConstraints() { return allOf(isDisplayed()); }
            @Override public String getDescription() { return "Wait for exact itemCount"; }
            @Override public void perform(UiController uiController, View view) {
                RecyclerView rv = (RecyclerView) view;
                long start = System.currentTimeMillis();
                while (true) {
                    int count = rv.getAdapter() == null ? 0 : rv.getAdapter().getItemCount();
                    if (count == expected) return;
                    if (System.currentTimeMillis() - start > timeoutMs)
                        throw new AssertionError("Timeout waiting for itemCount=" + expected);
                    uiController.loopMainThreadForAtLeast(250);
                }
            }
        };
    }
}
