package com.example.myapplication;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_RECORD_AUDIO = 123;

    // Default / fallback cooldown when UI is empty or invalid (milliseconds)
    private static final long DEFAULT_COOLDOWN_MS = 5000L; // 5 seconds

    private SoundMeter meter;
    private Thread worker;
    private volatile boolean running = false;

    // UI / control flags
    private boolean micRecording = false;         // whether Start/Pause shows recording
    private boolean startAfterPermission = false; // used when requesting permission and user previously tapped Start

    private MediaPlayer player;

    // counting/state
    private int whistleCount = 0;
    private long lastWhistleTime = 0;
    private boolean whistleActive = false;

    private long startIgnoreUntil = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkRecordPermission(); // will prompt if permission missing

        player = MediaPlayer.create(this, Settings.System.DEFAULT_RINGTONE_URI);

        // Initialize UI values
        TextView countView = findViewById(R.id.whistleCount);
        if (countView != null) countView.setText("0");
        TextView ampView = findViewById(R.id.ampBox);
        if (ampView != null) ampView.setText("amp=0 freq=0 E=0 A=0");
        Button btn = findViewById(R.id.reset);
        if (btn != null) btn.setText("Start");

        // Optionally pre-fill cooldown input (if present)
        EditText cooldown = findViewById(R.id.cooldownInput);
        if (cooldown != null && cooldown.getText().toString().isEmpty()) {
            cooldown.setText("5"); // default 5 seconds
        }
    }

    private void checkRecordPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO
            );
        }
    }

    // Called by Start/Pause button in layout
    public void onStartStopButton(View view) {
        Button btn = findViewById(R.id.reset);
        if (!micRecording) {
            // User wants to start
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                // Ask for permission and remember to start after granted
                startAfterPermission = true;
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        REQUEST_RECORD_AUDIO
                );
                return;
            }

            // Permission granted → start recording
            if (startRecording()) {
                if (btn != null) btn.setText("Pause");
                micRecording = true;
            } else {
                // Failed to start, keep UI consistent
                if (btn != null) btn.setText("Start");
                micRecording = false;
            }
        } else {
            // User wants to pause
            stopRecording();
            if (btn != null) btn.setText("Resume");
            micRecording = false;
        }
    }

    // Reset button handler (wired in layout)
    public void onResetButton(View view) {
        stopRecording();

        whistleCount = 0;
        lastWhistleTime = 0;
        whistleActive = false;
        micRecording = false;
        startAfterPermission = false;
        startIgnoreUntil = 0L;

        // Stop alarm playback
        if (player != null && player.isPlaying()) {
            player.pause();
            player.seekTo(0);
        }

        // Reset UI views
        TextView countView = findViewById(R.id.whistleCount);
        if (countView != null) countView.setText("0");
        TextView ampView = findViewById(R.id.ampBox);
        if (ampView != null) ampView.setText("amp=0 freq=0 E=0 A=0");
        Button btn = findViewById(R.id.reset);
        if (btn != null) btn.setText("Start");
    }

    // Start recording: returns true if successfully started
    private boolean startRecording() {
        try {
            meter = new SoundMeter();
            meter.start();

            running = true;

            // New: set the initial ignore window so no whistle is counted immediately after Start.
            // We use the user-configured cooldown as the initial ignore duration.
            startIgnoreUntil = System.currentTimeMillis() + 4000L; // 4 seconds fixed ignore

            worker = new Thread(() -> {
                while (running && meter != null) {
                    try {
                        SoundMeter.Result r = meter.read(); // blocking read (~46ms per frame for 2048 @44.1kHz)
                        if (r == null) continue;

                        long now = System.currentTimeMillis();
                        long userCooldown = getCooldownMsFromUI();

                        boolean isWhistle =
                                r.normalizedEnergy > 0.15 &&
                                        r.normalizedAmplitude > 0.3 &&
                                        r.peakFrequency >= 2000 &&
                                        r.peakFrequency <= 4500;

                        // If we're still in the start-ignore window, skip detection entirely.
                        if (now < startIgnoreUntil) {
                            whistleActive = false; // ensure edge state reset
                            // update debug UI to show we are in start-ignore window
                            final String debug = String.format("IGNORING (until %d) freq=%.0f E=%.3f A=%.3f",
                                    startIgnoreUntil, r.peakFrequency, r.normalizedEnergy, r.normalizedAmplitude);
                            runOnUiThread(() -> {
                                TextView ampView = findViewById(R.id.ampBox);
                                if (ampView != null) ampView.setText(debug);
                            });
                            continue;
                        }

                        // Strict edge-trigger + user-configured cooldown
                        if (isWhistle && !whistleActive && (now - lastWhistleTime) > userCooldown) {
                            whistleActive = true;
                            lastWhistleTime = now;
                            whistleCount++;

                            runOnUiThread(() -> {
                                TextView countView = findViewById(R.id.whistleCount);
                                if (countView != null) countView.setText(String.valueOf(whistleCount));

                                // Play alarm only if reached user requested target
                                int target = getUserRequestedCount();
                                if (whistleCount >= target) {
                                    if (player != null && !player.isPlaying()) {
                                        player.start();
                                    }
                                }
                            });
                        }

                        // If sound no longer matches whistle, reset the edge state
                        if (!isWhistle) {
                            whistleActive = false;
                        }

                        // Update debug UI normally when not in initial ignore period
                        final String debug = String.format("freq=%.0f E=%.3f A=%.3f",
                                r.peakFrequency, r.normalizedEnergy, r.normalizedAmplitude);
                        runOnUiThread(() -> {
                            TextView ampView = findViewById(R.id.ampBox);
                            if (ampView != null) ampView.setText(debug);
                        });

                    } catch (Exception e) {
                        Log.e(TAG, "Worker exception", e);
                    }
                }
            }, "WhistleWorker");
            worker.start();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to start meter", e);
            if (meter != null) {
                try { meter.stop(); } catch (Exception ex) { /* ignore */ }
                meter = null;
            }
            running = false;
            return false;
        }
    }

    private void stopRecording() {
        running = false;

        if (meter != null) {
            try {
                meter.stop();
            } catch (Exception e) {
                Log.w(TAG, "stop() error", e);
            }
            meter = null;
        }

        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
        whistleActive = false;
    }

    // Read user-entered cooldown (seconds) and convert to milliseconds.
    // Returns DEFAULT_COOLDOWN_MS if input invalid or missing.
    private long getCooldownMsFromUI() {
        EditText et = findViewById(R.id.cooldownInput);
        if (et == null || et.getText().toString().trim().isEmpty()) {
            return DEFAULT_COOLDOWN_MS;
        }
        try {
            int sec = Integer.parseInt(et.getText().toString().trim());
            // enforce a reasonable minimum
            return Math.max(1000L, sec * 1000L);
        } catch (NumberFormatException e) {
            return DEFAULT_COOLDOWN_MS;
        }
    }

    private int getUserRequestedCount() {
        EditText av = findViewById(R.id.numOfWhistles);
        if (av == null || av.getText().toString().isEmpty()) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(av.getText().toString());
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    // Permission callback
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                if (startAfterPermission) {
                    startAfterPermission = false;
                    if (startRecording()) {
                        Button btn = findViewById(R.id.reset);
                        if (btn != null) btn.setText("Pause");
                        micRecording = true;
                    }
                }
            } else {
                startAfterPermission = false;
                Log.w(TAG, "Audio permission denied by user");
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRecording();
        if (player != null) {
            try {
                player.release();
            } catch (Exception e) {
                // ignore
            }
            player = null;
        }
    }
}
