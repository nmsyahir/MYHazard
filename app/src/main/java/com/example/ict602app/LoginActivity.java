package com.example.ict602app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.ict602app.network.ApiClient;
import com.example.ict602app.network.ApiService;
import com.example.ict602app.network.dto.AuthResponse;
import com.example.ict602app.network.dto.LoginRequest;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvGoRegister;

    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        setTitle("MYHazard");

        session = new SessionManager(this);

        if (session.isLoggedIn()) {
            startActivity(new Intent(this, HomeActivity.class));
            finish();
            return;
        }

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvGoRegister = findViewById(R.id.tvGoRegister);

        String prefill = getIntent().getStringExtra("prefill_email");
        if (prefill != null && !prefill.trim().isEmpty()) {
            etEmail.setText(prefill.trim());
            etPassword.requestFocus();
        }

        btnLogin.setOnClickListener(v -> doLogin());

        tvGoRegister.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
            finish();
        });
    }

    private void doLogin() {
        String email = etEmail.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();

        if (email.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Please enter email & password", Toast.LENGTH_SHORT).show();
            return;
        }

        btnLogin.setEnabled(false);
        btnLogin.setText("Logging in...");

        ApiService api = ApiClient.getClient().create(ApiService.class);
        api.login(new LoginRequest(email, pass)).enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(@NonNull Call<AuthResponse> call, @NonNull Response<AuthResponse> response) {
                btnLogin.setEnabled(true);
                btnLogin.setText("Login");

                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(LoginActivity.this, "Login failed (" + response.code() + ")", Toast.LENGTH_SHORT).show();
                    return;
                }

                AuthResponse res = response.body();
                if (!res.success) {
                    Toast.makeText(LoginActivity.this, (res.message != null ? res.message : "Invalid login"), Toast.LENGTH_SHORT).show();
                    return;
                }

                session.saveUser(res.userId, res.name);

                startActivity(new Intent(LoginActivity.this, HomeActivity.class));
                finish();
            }

            @Override
            public void onFailure(@NonNull Call<AuthResponse> call, @NonNull Throwable t) {
                btnLogin.setEnabled(true);
                btnLogin.setText("Login");
                Toast.makeText(LoginActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
