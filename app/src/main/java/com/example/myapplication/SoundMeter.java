package com.example.myapplication;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

public class SoundMeter {
    private static final String TAG = "SoundMeter";
    private static final int SAMPLE_RATE = 44100;
    private static final int FFT_SIZE = 2048; // keep same as JS design (~46ms frames)

    private AudioRecord recorder;
    private short[] buffer = new short[FFT_SIZE];
    private double[] real = new double[FFT_SIZE];
    private double[] imag = new double[FFT_SIZE];

    public static class Result {
        public double peakFrequency;     // Hz
        public double normalizedEnergy;  // whistle band energy / total energy
        public double normalizedAmplitude; // 0..1, based on peak magnitude
    }

    // Start the AudioRecord safely
    public void start() {
        int minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );

        // Use MIC source for compatibility
        int bufferSize = Math.max(minBuf * 2, FFT_SIZE * 2);

        recorder = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
        );
        recorder.startRecording();
    }

    // Stop and release
    public void stop() {
        if (recorder != null) {
            try {
                recorder.stop();
            } catch (Exception e) {
                Log.w(TAG, "stop() error", e);
            }
            recorder.release();
            recorder = null;
        }
    }

    // Blocking read + FFT processing. Returns null on error.
    public Result read() {
        if (recorder == null) return null;

        int read = recorder.read(buffer, 0, FFT_SIZE);
        if (read <= 0) return null;

        // Fill real/imag arrays and apply simple window (Hann) to reduce leakage
        for (int i = 0; i < FFT_SIZE; i++) {
            double v = i < read ? buffer[i] : 0.0;
            // Hann window
            double window = 0.5 * (1 - Math.cos(2 * Math.PI * i / (FFT_SIZE - 1)));
            real[i] = v * window;
            imag[i] = 0.0;
        }

        // Perform FFT (in-place)
        FFT.fft(real, imag);

        double nyquist = SAMPLE_RATE / 2.0;
        int half = FFT_SIZE / 2;
        double binWidth = nyquist / half;

        int minBin = (int) Math.floor(2000.0 / binWidth);
        int maxBin = (int) Math.ceil(4500.0 / binWidth);
        if (minBin < 0) minBin = 0;
        if (maxBin > half - 1) maxBin = half - 1;

        double whistleEnergy = 0.0;
        double totalEnergy = 0.0;
        double peakMag = 0.0;
        double peakFreq = 0.0;

        for (int i = 0; i < half; i++) {
            double mag = Math.sqrt(real[i] * real[i] + imag[i] * imag[i]);
            totalEnergy += mag;

            if (i >= minBin && i <= maxBin) {
                whistleEnergy += mag;
                if (mag > peakMag) {
                    peakMag = mag;
                    peakFreq = i * binWidth;
                }
            }
        }

        Result res = new Result();
        res.peakFrequency = peakFreq;
        res.normalizedEnergy = whistleEnergy / Math.max(totalEnergy, 1e-9);
        // Normalize amplitude relative to 16-bit signed max: reasonable mapping
        res.normalizedAmplitude = Math.min(1.0, peakMag / 32768.0);
        return res;
    }
}
