package com.example.smart_cane;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class BluetoothService extends Service {

    private static final String TAG = "BluetoothService";
    // SPP UUID for HC-05/HC-06 (Standard Serial Port Profile)
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String CHANNEL_ID = "SmartCaneChannel";

    private final IBinder binder = new LocalBinder();
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket socket;
    private ConnectedThread connectedThread;
    private TextToSpeech textToSpeech;
    private Handler mainHandler;

    private boolean isConnected = false;

    public class LocalBinder extends Binder {
        public BluetoothService getService() {
            return BluetoothService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        setupTextToSpeech();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 1. Create Notification Channel (Required for Android 8.0+)
        createNotificationChannel();

        // 2. Create the Notification intent (tapping notification opens app)
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE);

        // 3. Build the notification
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Smart Cane Active")
                .setContentText("Connected and monitoring obstacles...")
                .setSmallIcon(R.drawable.ic_launcher_foreground) // Make sure this icon exists in res/drawable
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        // 4. Start as Foreground Service (Prevents OS from killing it)
        startForeground(1, notification);

        // If the service is killed by the system, restart it automatically
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /**
     * Connects to a paired Bluetooth device by name (e.g., "HC-05")
     */
    public boolean connectToDevice(String deviceName) {
        if (bluetoothAdapter == null) {
            speak("Bluetooth is not supported on this device.");
            return false;
        }

        // Permission Check for Android 12+ (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // Permissions should be requested in Activity, not Service
                speak("Bluetooth permission is missing.");
                return false;
            }
        }

        // Find the device in paired list
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        BluetoothDevice targetDevice = null;

        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                if (device.getName().equals(deviceName)) {
                    targetDevice = device;
                    break;
                }
            }
        }

        if (targetDevice == null) {
            speak("Could not find " + deviceName + " in paired devices. Please pair the cane first.");
            return false;
        }

        // Cancel discovery to save battery and improve connection speed
        bluetoothAdapter.cancelDiscovery();

        try {
            socket = targetDevice.createRfcommSocketToServiceRecord(MY_UUID);
            socket.connect();

            // Start the thread to listen for data
            connectedThread = new ConnectedThread(socket);
            connectedThread.start();

            isConnected = true;
            speak("Connected to Smart Cane successfully.");
            return true;

        } catch (IOException e) {
            Log.e(TAG, "Connection failed", e);
            speak("Failed to connect to cane. Is it turned on?");
            closeConnection();
            return false;
        }
    }

    private void closeConnection() {
        try {
            if (socket != null) socket.close();
            isConnected = false;
        } catch (IOException e) {
            Log.e(TAG, "Error closing socket", e);
        }
    }

    /**
     * Thread that handles incoming data from Arduino/ESP32
     */
    private class ConnectedThread extends Thread {
        private final InputStream mmInStream;

        public ConnectedThread(BluetoothSocket socket) {
            InputStream tmpIn = null;
            try {
                tmpIn = socket.getInputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error creating stream", e);
            }
            mmInStream = tmpIn;
        }

        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            while (isConnected) {
                try {
                    // Read from the InputStream
                    if (mmInStream != null) {
                        bytes = mmInStream.read(buffer);
                        if (bytes > 0) {
                            String incomingMessage = new String(buffer, 0, bytes);
                            // Post to main thread to update UI or Speak
                            mainHandler.post(() -> processSensorData(incomingMessage));
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Input stream disconnected", e);
                    isConnected = false;
                    mainHandler.post(() -> speak("Cane disconnected."));
                    break;
                }
            }
        }
    }

    /**
     * Interprets data sent from the Arduino
     */
    private void processSensorData(String data) {
        data = data.trim();

        // Ensure we don't spam speech for empty data
        if (data.isEmpty()) return;

        // Logic based on keywords sent by Arduino
        if (data.contains("OBSTACLE")) {
            speak("Warning. Obstacle detected.");
        } else if (data.contains("WATER")) {
            speak("Warning. Water detected.");
        } else if (data.contains("STAIRS")) {
            speak("Caution. Stairs detected.");
        } else if (data.contains("HOLE") || data.contains("DROP")) {
            speak("Danger. Drop off detected.");
        }
    }

    private void setupTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.US);
                // Slightly faster speech rate for warnings
                textToSpeech.setSpeechRate(1.1f);
            }
        });
    }

    private void speak(String text) {
        if (textToSpeech != null) {
            // QUEUE_ADD allows warnings to stack if they come in fast
            // QUEUE_FLUSH would cut off the previous warning
            textToSpeech.speak(text, TextToSpeech.QUEUE_ADD, null, null);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Smart Cane Connection",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        closeConnection();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
    }
}