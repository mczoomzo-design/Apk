package com.photobooth.sticker;

import android.graphics.Bitmap;
import java.io.ByteArrayOutputStream;

/** ESC/POS command helpers for 80mm thermal printers (576px printable width). */
public class EscPos {
    public static final byte[] INIT = {0x1B, 0x40};            // ESC @  (reset)
    public static final byte[] ALIGN_CENTER = {0x1B, 0x61, 0x01}; // ESC a 1
    public static final byte[] CUT = {0x1D, 0x56, 0x01};      // GS V 1 (partial cut)

    public static byte[] feed(int lines) {
        return new byte[]{0x1B, 0x64, (byte) lines};          // ESC d n (feed n lines)
    }

    /**
     * Convert a bitmap to an ESC/POS raster (GS v 0), scaled to printWidth,
     * Floyd–Steinberg dithered, and split into horizontal bands for printer buffers.
     */
    public static byte[] bitmapToRaster(Bitmap src, int printWidth) {
        int w = printWidth;
        int h = Math.round(src.getHeight() * (printWidth / (float) src.getWidth()));
        Bitmap bmp = Bitmap.createScaledBitmap(src, w, h, true);

        // grayscale (composite transparency onto white)
        float[] gray = new float[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = bmp.getPixel(x, y);
                int a = (p >>> 24) & 0xff;
                int r = (p >> 16) & 0xff, g = (p >> 8) & 0xff, b = p & 0xff;
                float af = a / 255f;
                float lum = (0.299f * r + 0.587f * g + 0.114f * b) * af + 255f * (1f - af);
                gray[y * w + x] = lum;
            }
        }

        // Floyd–Steinberg dithering
        boolean[] black = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                float oldv = gray[i];
                float newv = oldv < 128 ? 0f : 255f;
                black[i] = newv == 0f;
                float err = oldv - newv;
                if (x + 1 < w)            gray[i + 1]     += err * 7f / 16f;
                if (y + 1 < h) {
                    if (x > 0)            gray[i + w - 1] += err * 3f / 16f;
                                         gray[i + w]     += err * 5f / 16f;
                    if (x + 1 < w)        gray[i + w + 1] += err * 1f / 16f;
                }
            }
        }

        int bytesPerRow = (w + 7) / 8;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int band = 128; // rows per GS v 0 command (safe for most buffers)
        for (int y0 = 0; y0 < h; y0 += band) {
            int bh = Math.min(band, h - y0);
            bos.write(0x1D); bos.write(0x76); bos.write(0x30); bos.write(0x00);
            bos.write(bytesPerRow & 0xff); bos.write((bytesPerRow >> 8) & 0xff);
            bos.write(bh & 0xff);          bos.write((bh >> 8) & 0xff);
            for (int y = 0; y < bh; y++) {
                for (int bx = 0; bx < bytesPerRow; bx++) {
                    int val = 0;
                    for (int bit = 0; bit < 8; bit++) {
                        int x = bx * 8 + bit;
                        if (x < w && black[(y0 + y) * w + x]) val |= (0x80 >> bit);
                    }
                    bos.write(val);
                }
            }
        }
        return bos.toByteArray();
    }
}
