package com.example.smart_cane;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Locale;

public class SignupActivity extends AppCompatActivity {

    private EditText etFullName, etEmail, etPassword, etConfirmPassword, etPhone;
    private Button btnSignup, btnLogin;
    private ProgressBar progressBar;
    private TextToSpeech textToSpeech;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);
        initializeViews();
        setupTextToSpeech();
        setupListeners();
        speak("Sign up screen. Double tap sign up button to create account.");
    }

    private void setupTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.US);
                textToSpeech.setSpeechRate(0.8f);
            }
        });
    }

    private void speak(String text) {
        if (textToSpeech != null) textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
    }

    private void initializeViews() {
        etFullName = findViewById(R.id.etFullName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        etPhone = findViewById(R.id.etPhone);
        btnSignup = findViewById(R.id.btnSignup);
        btnLogin = findViewById(R.id.btnLogin);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupListeners() {
        DoubleTapHandler.setupDoubleTap(btnSignup, this::attemptSignup, textToSpeech, "Sign up button");
        DoubleTapHandler.setupDoubleTap(btnLogin, () -> {
            speak("Returning to login");
            finish();
        }, textToSpeech, "Back to login button");
    }

    private void attemptSignup() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            speak("All fields are required.");
            return;
        }

        if (!password.equals(confirmPassword)) {
            speak("Passwords do not match.");
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnSignup.setEnabled(false);
        speak("Creating account...");

        new android.os.Handler().postDelayed(() -> {
            progressBar.setVisibility(View.GONE);
            btnSignup.setEnabled(true);
            speak("Registration successful.");

            SharedPreferences prefs = getSharedPreferences("WhiteCanePrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("isLoggedIn", true);
            editor.putString("email", email);
            editor.apply();

            startActivity(new Intent(SignupActivity.this, MainActivity.class));
            finish();
        }, 2000);
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