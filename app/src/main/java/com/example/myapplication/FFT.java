package com.example.myapplication;

public class FFT {
    // In-place radix-2 iterative FFT. real[] and imag[] length must be power of two.
    public static void fft(double[] real, double[] imag) {
        int n = real.length;
        if (n == 0) return;
        int levels = 31 - Integer.numberOfLeadingZeros(n); // floor(log2(n))
        if ((1 << levels) != n) {
            throw new IllegalArgumentException("Length is not a power of two");
        }

        // Bit-reversed addressing permutation
        for (int i = 0, j = 0; i < n; i++) {
            if (j > i) {
                double tmpR = real[i];
                double tmpI = imag[i];
                real[i] = real[j];
                imag[i] = imag[j];
                real[j] = tmpR;
                imag[j] = tmpI;
            }
            int bit = n >> 1;
            while ((j & bit) != 0) {
                j ^= bit;
                bit >>= 1;
            }
            j ^= bit;
        }

        // Cooley-Tukey
        for (int size = 2; size <= n; size <<= 1) {
            double angle = -2 * Math.PI / size;
            double wlenR = Math.cos(angle);
            double wlenI = Math.sin(angle);
            for (int i = 0; i < n; i += size) {
                double wr = 1.0;
                double wi = 0.0;
                for (int j = 0; j < size / 2; j++) {
                    int u = i + j;
                    int v = i + j + size / 2;
                    double xr = real[v] * wr - imag[v] * wi;
                    double xi = real[v] * wi + imag[v] * wr;

                    real[v] = real[u] - xr;
                    imag[v] = imag[u] - xi;
                    real[u] += xr;
                    imag[u] += xi;

                    double nextWr = wr * wlenR - wi * wlenI;
                    double nextWi = wr * wlenI + wi * wlenR;
                    wr = nextWr;
                    wi = nextWi;
                }
            }
        }
    }
}
