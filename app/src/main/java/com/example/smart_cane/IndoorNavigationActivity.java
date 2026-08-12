package com.example.smart_cane;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Locale;

public class IndoorNavigationActivity extends AppCompatActivity implements SensorEventListener {

    private TextView tvSteps;
    private SensorManager sensorManager;
    private Sensor stepSensor;
    private int stepCount = 0;
    private TextToSpeech textToSpeech;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_indoor_navigation);

        tvSteps = findViewById(R.id.tvTitle); // Assuming you reuse the title TextView
        if(tvSteps == null) tvSteps = new TextView(this); // Fallback

        setupTextToSpeech();

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);

        if (stepSensor == null) {
            tvSteps.setText("No Step Sensor");
            speak("This device does not support step counting.");
        } else {
            speak("Indoor Navigation. Start walking to count steps.");
        }
    }

    private void setupTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) textToSpeech.setLanguage(Locale.US);
        });
    }

    private void speak(String text) {
        if (textToSpeech != null) textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (stepSensor != null) sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (stepSensor != null) sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            stepCount++;
            tvSteps.setText("Steps: " + stepCount);
            if (stepCount % 10 == 0) {
                speak(stepCount + " steps taken.");
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}