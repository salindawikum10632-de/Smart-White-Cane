package com.example.smart_cane;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Locale;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin, btnSignup;
    private ProgressBar progressBar;
    private TextToSpeech textToSpeech;
    private CheckBox cbRememberMe;
    private TextView tvForgotPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("WhiteCanePrefs", MODE_PRIVATE);
        boolean isLoggedIn = prefs.getBoolean("isLoggedIn", false);

        if (isLoggedIn) {
            String email = prefs.getString("email", "");
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            intent.putExtra("USER_EMAIL", email);
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.activity_login);

        initializeViews();
        setupTextToSpeech();
        setupListeners();
        loadSavedCredentials();

        speak("Login screen. Enter your email and password. Double tap login button to sign in.");
    }

    private void initializeViews() {
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnSignup = findViewById(R.id.btnSignup);
        progressBar = findViewById(R.id.progressBar);
        cbRememberMe = findViewById(R.id.cbRememberMe);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
    }

    private void setupTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.US);
                textToSpeech.setSpeechRate(0.8f);
            }
        });
    }

    private void setupListeners() {
        DoubleTapHandler.setupDoubleTap(btnLogin, this::attemptLogin, textToSpeech, "Login button");

        DoubleTapHandler.setupDoubleTap(btnSignup, () -> {
            speak("Opening sign up screen");
            startActivity(new Intent(LoginActivity.this, SignupActivity.class));
        }, textToSpeech, "Sign up button");

        DoubleTapHandler.setupDoubleTap(tvForgotPassword, () -> {
            ForgotPasswordDialog dialog = new ForgotPasswordDialog(this, textToSpeech);
            dialog.show();
        }, textToSpeech, "Forgot password");

        etEmail.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) speak("Email field focused.");
        });

        etPassword.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) speak("Password field focused.");
        });
    }

    private void loadSavedCredentials() {
        SharedPreferences prefs = getSharedPreferences("WhiteCanePrefs", MODE_PRIVATE);
        boolean rememberMe = prefs.getBoolean("rememberMe", false);

        if (rememberMe) {
            etEmail.setText(prefs.getString("email", ""));
            etPassword.setText(prefs.getString("password", ""));
            cbRememberMe.setChecked(true);
        }
    }

    private void attemptLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            speak("Please enter both email and password.");
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnLogin.setEnabled(false);
        speak("Logging in. Please wait.");

        // Simulated Login Delay
        new android.os.Handler().postDelayed(() -> {
            progressBar.setVisibility(View.GONE);
            btnLogin.setEnabled(true);

            if (isValidCredentials(email, password)) {
                saveLoginState(email, password);
                speak("Login successful.");

                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                intent.putExtra("USER_EMAIL", email);
                startActivity(intent);
                finish();
            } else {
                speak("Invalid email or password.");
                Toast.makeText(LoginActivity.this, "Invalid credentials", Toast.LENGTH_SHORT).show();
            }
        }, 1500);
    }

    private boolean isValidCredentials(String email, String password) {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() && password.length() >= 6;
    }

    private void saveLoginState(String email, String password) {
        SharedPreferences prefs = getSharedPreferences("WhiteCanePrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("isLoggedIn", true);
        editor.putString("email", email);
        if (cbRememberMe.isChecked()) {
            editor.putBoolean("rememberMe", true);
            editor.putString("password", password); // Note: Not secure for production
        } else {
            editor.putBoolean("rememberMe", false);
            editor.remove("password");
        }
        editor.apply();
    }

    private void speak(String text) {
        if (textToSpeech != null) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }
}