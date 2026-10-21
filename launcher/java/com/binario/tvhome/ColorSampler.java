package com.binario.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * Pulls one representative colour out of an app banner: draw it tiny, then keep
 * the most colourful pixel that is neither near-black nor blown out.
 *
 * Replaces androidx.palette - we want a single glow colour, not a palette, and
 * 144 pixels is plenty to find one.
 */
final class ColorSampler {
    // 32x18, not 16x9: a small logo on a big white banner (YouTube) averages
    // itself out of existence at the lower resolution.
    private static final int W = 32, H = 18;

    private ColorSampler() { }

    static int sample(Drawable d, int fallback) {
        if (d == null) return fallback;
        Bitmap bmp = null;
        // The caller's Drawable is the very same instance an ImageView is
        // showing. Rasterising it must not leave our 16x9 bounds behind, or the
        // tile renders a thumbnail in its top-left corner.
        Rect saved = new Rect(d.getBounds());
        try {
            bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            d.setBounds(0, 0, W, H);
            d.draw(canvas);

            float[] hsv = new float[3];
            int best = fallback;
            float bestScore = 0f;

            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    int px = bmp.getPixel(x, y);
                    if (Color.alpha(px) < 128) continue;
                    Color.colorToHSV(px, hsv);

                    if (hsv[2] < 0.15f) continue;                        // near black
                    // Near white = washed out AND bright. Testing brightness
                    // alone threw away pure #FF0000, whose HSV value is 1.0 -
                    // which is exactly why YouTube came out grey while
                    // Netflix's #E50914 (value 0.898) worked.
                    if (hsv[1] < 0.18f && hsv[2] > 0.88f) continue;

                    float score = hsv[1] * hsv[1] * hsv[2];
                    if (score > bestScore) { bestScore = score; best = px; }
                }
            }
            // A genuinely grey banner (Moonlight, WiiM) gets no invented colour.
            if (bestScore <= 0.02f) return fallback;

            Color.colorToHSV(best, hsv);
            hsv[1] = Math.min(1f, Math.max(hsv[1], 0.55f));
            hsv[2] = 1f;
            return Color.HSVToColor(hsv);
        } catch (Throwable t) {
            return fallback;
        } finally {
            d.setBounds(saved);
            if (bmp != null) bmp.recycle();
        }
    }
}
