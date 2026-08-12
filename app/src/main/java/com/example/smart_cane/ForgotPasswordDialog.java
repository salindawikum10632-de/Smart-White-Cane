package com.example.smart_cane;

import android.app.Dialog;
import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class ForgotPasswordDialog extends Dialog {

    private EditText etEmail;
    private Button btnSubmit, btnCancel;
    private ProgressBar progressBar;
    private TextToSpeech textToSpeech;
    private ImageView ivClose;
    private TextView tvDialogTitle;

    public ForgotPasswordDialog(Context context, TextToSpeech tts) {
        super(context);
        this.textToSpeech = tts;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_forgot_password);

        initializeViews();
        setupListeners();
        speak("Forgot password. Enter email to reset.");
    }

    private void initializeViews() {
        etEmail = findViewById(R.id.etDialogEmail);
        btnSubmit = findViewById(R.id.btnSubmit);
        btnCancel = findViewById(R.id.btnCancel);
        progressBar = findViewById(R.id.dialogProgressBar);
        ivClose = findViewById(R.id.ivClose);
        tvDialogTitle = findViewById(R.id.tvDialogTitle);
    }

    private void setupListeners() {
        DoubleTapHandler.setupDoubleTap(btnSubmit, () -> {
            if (etEmail.getText().toString().isEmpty()) {
                speak("Enter email first.");
                return;
            }
            progressBar.setVisibility(View.VISIBLE);
            speak("Sending reset link...");

            new android.os.Handler().postDelayed(() -> {
                progressBar.setVisibility(View.GONE);
                speak("Reset link sent.");
                dismiss();
            }, 1500);
        }, textToSpeech, "Submit button");

        DoubleTapHandler.setupDoubleTap(btnCancel, this::dismiss, textToSpeech, "Cancel button");
        if(ivClose != null) DoubleTapHandler.setupDoubleTap(ivClose, this::dismiss, textToSpeech, "Close button");
    }

    private void speak(String text) {
        if (textToSpeech != null) textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
    }
}