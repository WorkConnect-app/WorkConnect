package com.example.workconnect.ui.vacations;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import androidx.test.espresso.contrib.PickerActions;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;

import com.example.workconnect.R;
import com.google.firebase.auth.FirebaseAuth;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.matcher.RootMatchers.withDecorView;

@RunWith(AndroidJUnit4.class)
public class VacationEdgeCaseUiTest {

    private static final String EMPLOYEE_EMAIL = "MANAGE1@M.com";
    private static final String EMPLOYEE_PASSWORD = "123456";

    @Rule
    public ActivityTestRule<NewVacationRequestActivity> activityRule =
            new ActivityTestRule<>(NewVacationRequestActivity.class);

    @Before
    public void ensureLoggedIn() throws Exception {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) return;

        final Object lock = new Object();
        final boolean[] done = {false};
        final Exception[] err = {null};

        auth.signInWithEmailAndPassword(EMPLOYEE_EMAIL, EMPLOYEE_PASSWORD)
                .addOnCompleteListener(task -> {
                    synchronized (lock) {
                        if (!task.isSuccessful()) err[0] = task.getException();
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
    }

    @Test
    public void submitVacationRequest_notEnoughBalance_showsErrorToast() {

        // Start date
        onView(withId(R.id.et_start_date)).perform(click());
        onView(withClassName(equalTo(android.widget.DatePicker.class.getName())))
                .perform(PickerActions.setDate(2026, 2, 1));
        onView(withId(android.R.id.button1)).perform(click());

        // End date
        onView(withId(R.id.et_end_date)).perform(click());
        onView(withClassName(equalTo(android.widget.DatePicker.class.getName())))
                .perform(PickerActions.setDate(2026, 2, 15));
        onView(withId(android.R.id.button1)).perform(click());

        onView(withId(R.id.et_reason))
                .perform(replaceText("Vacation longer than balance"), closeSoftKeyboard());

        onView(withId(R.id.btn_send_request)).perform(click());

        // Still on the same screen (optional UI validation)
        onView(withId(R.id.tv_title)).check(matches(isDisplayed()));
        onView(withId(R.id.et_reason)).check(matches(withText("Vacation longer than balance")));
    }
}
