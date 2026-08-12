package com.example.smart_cane;

import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.view.View;

public class DoubleTapHandler {
    private static final long DOUBLE_TAP_TIMEOUT = 500; // milliseconds
    private long lastTapTime = 0;
    private int tapCount = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable resetRunnable;

    public static void setupDoubleTap(View view, Runnable action, TextToSpeech tts, String buttonName) {
        DoubleTapHandler tapHandler = new DoubleTapHandler();

        view.setOnClickListener(v -> {
            long currentTime = System.currentTimeMillis();

            if (tapHandler.resetRunnable != null) {
                tapHandler.handler.removeCallbacks(tapHandler.resetRunnable);
            }

            long timeDiff = currentTime - tapHandler.lastTapTime;

            if (timeDiff < DOUBLE_TAP_TIMEOUT && tapHandler.tapCount == 1) {
                tapHandler.tapCount = 0;
                action.run();
            } else {
                tapHandler.tapCount = 1;
                tapHandler.lastTapTime = currentTime;

                if (tts != null && buttonName != null) {
                    tts.speak(buttonName + ". Double tap to activate", TextToSpeech.QUEUE_FLUSH, null, null);
                }

                tapHandler.resetRunnable = () -> {
                    tapHandler.tapCount = 0;
                };
                tapHandler.handler.postDelayed(tapHandler.resetRunnable, DOUBLE_TAP_TIMEOUT);
            }
        });
    }

    public static void setupDoubleTap(View view, Runnable action) {
        setupDoubleTap(view, action, null, null);
    }
}