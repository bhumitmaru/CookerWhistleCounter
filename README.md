Cooker Whistle Counter (Android)

An Android application that accurately counts pressure cooker whistles in real time using non-ML audio signal processing (FFT).
The app listens through the microphone, detects whistle sounds based on frequency and energy analysis, applies a strict cooldown, and triggers an alarm when a user-defined whistle limit is reached.

Features

* Real-time microphone audio capture
* FFT-based frequency analysis (no machine learning)
* Accurate detection of cooker whistles in the 2–4.5 kHz range
* Fixed 4-second startup ignore window to prevent accidental counts
* User-configurable cooldown between whistles
* Live whistle counter
* Alarm trigger when target whistle count is reached
* Threaded audio processing to keep UI responsive
* Lightweight and efficient with no external DSP libraries

Detection Logic (How It Works)

1. Audio is captured using AudioRecord
2. Samples are processed in approximately 46 ms frames (2048 samples at 44.1 kHz)
3. FFT is applied to convert audio from time domain to frequency domain
4. Energy in the 2–4.5 kHz band is compared against total signal energy
5. A whistle is detected only if:

   * Whistle-band energy dominates the spectrum
   * Peak amplitude exceeds the defined threshold
   * Peak frequency lies within the cooker whistle range
6. Detection uses edge-triggering with:

   * A fixed 4-second ignore period after pressing Start
   * A user-defined cooldown period between whistles

This design prevents double counting, background noise triggers, and startup artifacts.

Tech Stack

* Language: Java
* Platform: Android
* Audio API: AudioRecord
* Signal Processing: FFT (Cooley–Tukey)
* Architecture: Activity with background worker thread
* UI: XML layouts
* No machine learning and no external DSP libraries

Configurable Parameters

Startup ignore period: 4 seconds
Cooldown period: User-defined (seconds)
Frequency band: 2000–4500 Hz
FFT size: 2048 samples
Sample rate: 44100 Hz

Permissions
The app requires the following permission:

    <uses-permission android:name="android.permission.RECORD_AUDIO" />

Runtime permission handling is implemented inside the app.

Notes

* Best accuracy is achieved in a quiet kitchen environment
* Detection thresholds may be tuned for different pressure cooker models
* Zero-crossing frequency detection is intentionally avoided due to inaccuracy
* The app works completely offline

Author

Bhumit Maru
Second-year B.Tech student in Artificial Intelligence and Data Science at KJSCE
As of Jan 2026
