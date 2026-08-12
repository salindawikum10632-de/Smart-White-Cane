package com.example.smart_cane;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private CheckBox cbVoiceFeedback, cbVibration, cbHighContrast;
    private SeekBar sbVoiceSpeed, sbVoiceVolume;
    private TextView tvVoiceSpeed, tvVoiceVolume;
    private Button btnSave;
    private TextToSpeech textToSpeech;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        initializeViews();
        setupTextToSpeech();
        loadSettings();
        setupListeners();
        speak("Settings. Double tap save to apply changes.");
    }

    private void setupTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.US);
            }
        });
    }

    private void speak(String text) {
        if (textToSpeech != null) textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
    }

    private void initializeViews() {
        cbVoiceFeedback = findViewById(R.id.cbVoiceFeedback);
        cbVibration = findViewById(R.id.cbVibration);
        cbHighContrast = findViewById(R.id.cbHighContrast);
        sbVoiceSpeed = findViewById(R.id.sbVoiceSpeed);
        sbVoiceVolume = findViewById(R.id.sbVoiceVolume);
        tvVoiceSpeed = findViewById(R.id.tvVoiceSpeed);
        tvVoiceVolume = findViewById(R.id.tvVoiceVolume);
        btnSave = findViewById(R.id.btnSave);
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences("WhiteCanePrefs", MODE_PRIVATE);
        cbVoiceFeedback.setChecked(prefs.getBoolean("voiceFeedback", true));
        cbVibration.setChecked(prefs.getBoolean("vibration", true));
        cbHighContrast.setChecked(prefs.getBoolean("highContrast", false));
        sbVoiceSpeed.setProgress(prefs.getInt("voiceSpeed", 50));
        sbVoiceVolume.setProgress(prefs.getInt("voiceVolume", 80));
    }

    private void setupListeners() {
        sbVoiceSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean b) { tvVoiceSpeed.setText("Speed: " + p + "%"); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });

        sbVoiceVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean b) { tvVoiceVolume.setText("Volume: " + p + "%"); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });

        DoubleTapHandler.setupDoubleTap(btnSave, this::saveSettings, textToSpeech, "Save button");
    }

    private void saveSettings() {
        SharedPreferences.Editor editor = getSharedPreferences("WhiteCanePrefs", MODE_PRIVATE).edit();
        editor.putBoolean("voiceFeedback", cbVoiceFeedback.isChecked());
        editor.putBoolean("vibration", cbVibration.isChecked());
        editor.putBoolean("highContrast", cbHighContrast.isChecked());
        editor.putInt("voiceSpeed", sbVoiceSpeed.getProgress());
        editor.putInt("voiceVolume", sbVoiceVolume.getProgress());
        editor.apply();
        speak("Settings saved.");
        finish();
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