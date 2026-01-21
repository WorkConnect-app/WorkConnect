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

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin, btnRegister;
    private Button btnGoogleLogin;

    private LoginViewModel viewModel;

    private GoogleSignInClient googleClient;

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

                    viewModel.loginWithGoogleIdToken(idToken);

                } catch (ApiException e) {
                    Toast.makeText(this, "Google sign-in error: " + e.getStatusCode(), Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_activity);

        etEmail = findViewById(R.id.Email);
        etPassword = findViewById(R.id.password);
        btnLogin = findViewById(R.id.log_in);
        btnRegister = findViewById(R.id.Register);

        // add this id in XML
        btnGoogleLogin = findViewById(R.id.btn_google_login);

        viewModel = new ViewModelProvider(this).get(LoginViewModel.class);

        setupGoogleClient();

        btnLogin.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

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

        btnGoogleLogin.setOnClickListener(v -> {
            Intent signInIntent = googleClient.getSignInIntent();
            googleLauncher.launch(signInIntent);
        });

        btnRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterTypeActivity.class))
        );

        observeViewModel();
    }

    private void setupGoogleClient() {
        // IMPORTANT: default_web_client_id comes from google-services.json
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        googleClient = GoogleSignIn.getClient(this, gso);
    }

    private void observeViewModel() {
        viewModel.getIsLoading().observe(this, isLoading -> {
            boolean loading = isLoading != null && isLoading;
            btnLogin.setEnabled(!loading);
            btnRegister.setEnabled(!loading);
            btnGoogleLogin.setEnabled(!loading);
        });

        viewModel.getErrorMessage().observe(this, msg -> {
            if (!TextUtils.isEmpty(msg)) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getNeedsRegistration().observe(this, needs -> {
            if (needs != null && needs) {
                Toast.makeText(this, "Signed in with Google. Please complete registration.", Toast.LENGTH_LONG).show();
                startActivity(new Intent(this, RegisterTypeActivity.class));
                finish();
            }
        });


        viewModel.getLoginRole().observe(this, role -> {
            if (TextUtils.isEmpty(role)) return;


            Toast.makeText(this, "Login success, role=" + role, Toast.LENGTH_SHORT).show();

            // זמנית תעשי return כדי לא לנווט:
            // return;

            // everyone to same home
            Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
            startActivity(intent);
            finish();
        });
    }
}
