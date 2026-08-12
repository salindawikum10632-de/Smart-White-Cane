package com.example.smart_cane;

import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.widget.Button; // <--- This import is critical
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    // --- FIX IS HERE: Changed LinearLayout to Button ---
    private Button btnOutdoorNavigation, btnIndoorNavigation;
    private Button btnSettings, btnLogout;
    // ---------------------------------------------------

    private TextView tvWelcome;
    private TextToSpeech textToSpeech;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupTextToSpeech();
        setupListeners();

        speak("Main Menu. Double tap options to select.");
    }

    private void initializeViews() {
        // Because these are now defined as Buttons above, this will no longer crash
        btnOutdoorNavigation = findViewById(R.id.btnOutdoorNavigation);
        btnIndoorNavigation = findViewById(R.id.btnIndoorNavigation);
        btnSettings = findViewById(R.id.btnSettings);
        btnLogout = findViewById(R.id.btnLogout);

        tvWelcome = findViewById(R.id.tvWelcome);

        String email = getIntent().getStringExtra("USER_EMAIL");
        if(email != null && tvWelcome != null) {
            tvWelcome.setText("Welcome, " + email);
        }
    }

    private void setupTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.US);
            }
        });
    }

    private void setupListeners() {
        // Outdoor Navigation
        if (btnOutdoorNavigation != null) {
            DoubleTapHandler.setupDoubleTap(btnOutdoorNavigation, () -> {
                speak("Opening Outdoor Navigation");
                startActivity(new Intent(MainActivity.this, OutdoorNavigationActivity.class));
            });
        }

        // Indoor Navigation
        if (btnIndoorNavigation != null) {
            DoubleTapHandler.setupDoubleTap(btnIndoorNavigation, () -> {
                speak("Opening Indoor Navigation");
                startActivity(new Intent(MainActivity.this, IndoorNavigationActivity.class));
            });
        }

        // Settings
        if (btnSettings != null) {
            DoubleTapHandler.setupDoubleTap(btnSettings, () -> {
                speak("Opening Settings");
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            });
        }

        // Logout
        if (btnLogout != null) {
            DoubleTapHandler.setupDoubleTap(btnLogout, () -> {
                speak("Logging out");
                getSharedPreferences("WhiteCanePrefs", MODE_PRIVATE).edit().clear().apply();
                startActivity(new Intent(MainActivity.this, LoginActivity.class));
                finish();
            });
        }
    }

    private void speak(String text) {
        if (textToSpeech != null) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
    }
}