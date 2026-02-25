package com.example.workconnect.ui.auth;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.example.workconnect.R;
import com.example.workconnect.ui.home.HomeActivity;
import com.example.workconnect.viewModels.auth.LoginViewModel;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.firebase.auth.FirebaseAuth;

/**
 * Login screen:
 * - Email/password login
 * - Google login
 */
public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin, btnRegister;
    private Button btnGoogleLogin;

    private LoginViewModel viewModel;
    private GoogleSignInClient googleClient;

    // Handles Google sign-in result
    private final ActivityResultLauncher<Intent> googleLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getData() == null) {
                    Toast.makeText(this, "Google sign-in cancelled", Toast.LENGTH_SHORT).show();
                    return;
                }

                try {
                    GoogleSignInAccount account =
                            GoogleSignIn.getSignedInAccountFromIntent(result.getData())
                                    .getResult(ApiException.class);

                    if (account == null) {
                        Toast.makeText(this, "Google sign-in failed", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String idToken = account.getIdToken();
                    if (idToken == null) {
                        Toast.makeText(this, "Missing Google ID token (check SHA-1 + default_web_client_id)", Toast.LENGTH_LONG).show();
                        return;
                    }

                    // Delegate actual login logic to ViewModel
                    viewModel.loginWithGoogleIdToken(idToken);

                } catch (ApiException e) {
                    Toast.makeText(this, "Google sign-in error: " + e.getStatusCode(), Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_activity);

        // Bind UI elements
        etEmail = findViewById(R.id.Email);
        etPassword = findViewById(R.id.password);
        btnLogin = findViewById(R.id.log_in);
        btnRegister = findViewById(R.id.Register);
        btnGoogleLogin = findViewById(R.id.btn_google_login);

        viewModel = new ViewModelProvider(this).get(LoginViewModel.class);

        setupGoogleClient();

        // Email/password login
        btnLogin.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            // Basic client-side validation
            if (TextUtils.isEmpty(email)) {
                etEmail.setError("Email is required");
                etEmail.requestFocus();
                return;
            }
            if (TextUtils.isEmpty(password)) {
                etPassword.setError("Password is required");
                etPassword.requestFocus();
                return;
            }

            viewModel.login(email, password);
        });

        // Google login (force account chooser)
        btnGoogleLogin.setOnClickListener(v -> {
            fullSignOutGoogleThenLaunch();
        });

        // Navigate to registration type selection
        btnRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterTypeActivity.class))
        );

        observeViewModel();
    }

    // Configure GoogleSignIn client
    private void setupGoogleClient() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        googleClient = GoogleSignIn.getClient(this, gso);
    }

    /**
     * Full sign out: Firebase + Google.
     * Ensures account chooser appears instead of auto-login.
     */
    private void fullSignOutGoogleThenLaunch() {
        FirebaseAuth.getInstance().signOut();

        if (googleClient == null) {
            setupGoogleClient();
        }

        googleClient.revokeAccess().addOnCompleteListener(task ->
                googleClient.signOut().addOnCompleteListener(task2 -> {
                    Intent signInIntent = googleClient.getSignInIntent();
                    googleLauncher.launch(signInIntent);
                })
        );
    }

    // Used when we only need to clear session silently
    private void fullSignOutOnly() {
        FirebaseAuth.getInstance().signOut();
        if (googleClient != null) {
            googleClient.revokeAccess();
            googleClient.signOut();
        }
    }

    // Observe ViewModel state (loading, errors, navigation)
    private void observeViewModel() {

        // Disable buttons while loading
        viewModel.getIsLoading().observe(this, isLoading -> {
            boolean loading = isLoading != null && isLoading;
            btnLogin.setEnabled(!loading);
            btnRegister.setEnabled(!loading);
            btnGoogleLogin.setEnabled(!loading);
        });

        // Show error messages
        viewModel.getErrorMessage().observe(this, msg -> {
            if (!TextUtils.isEmpty(msg)) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

                // If user is pending/rejected - force full logout
                if ("Waiting for manager approval".equals(msg)
                        || "Your registration was rejected by the manager".equals(msg)) {
                    fullSignOutOnly();
                }

                viewModel.clearError();
            }
        });

        // Google user exists but no Firestore profile yet
        viewModel.getNeedsRegistration().observe(this, needs -> {
            if (needs != null && needs) {
                Toast.makeText(this, "Signed in with Google. Please complete registration.", Toast.LENGTH_LONG).show();
                startActivity(new Intent(this, CompleteGoogleProfileActivity.class));
                viewModel.clearNeedsRegistration();
                finish();
            }
        });

        // Navigation decisions handled centrally by ViewModel
        viewModel.getNavigationTarget().observe(this, target -> {
            if (TextUtils.isEmpty(target)) return;

            switch (target) {
                case "COMPLETE_GOOGLE":
                    startActivity(new Intent(this, CompleteGoogleProfileActivity.class));
                    break;

                case "MANAGER_COMPLETE":
                    startActivity(new Intent(this, CompleteManagerProfileActivity.class));
                    break;

                case "HOME":
                    startActivity(new Intent(this, HomeActivity.class));
                    break;
            }

            viewModel.clearNavigation();
            finish();
        });
    }
}