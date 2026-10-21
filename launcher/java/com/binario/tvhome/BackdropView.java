package com.binario.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * The whole background. One wide, soft radial gradient in the focused app's own
 * colour, centred near that app and falling off to black.
 *
 * Deliberately a gradient and not a real blur: at this size it reads the same,
 * and it costs a single drawRect per frame - which matters on a box with about
 * 1 GB free. Moving between apps cross-fades the colour and slides the centre.
 */
final class BackdropView extends View {

    /** Gradient radius, as a multiple of screen width. Wide = soft. */
    private static final float RADIUS_SCALE = 1.25f;
    private static final long FADE_MS = 420L;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int peakAlpha;

    private int color = Color.TRANSPARENT;
    private float cx = 0.5f, cy = 0.78f;      // centre, as fractions of the view
    private boolean shaderStale = true;
    private ValueAnimator animator;

    /** @param intensity 0-100, from config.xml */
    BackdropView(Context c, int intensity) {
        super(c);
        setWillNotDraw(false);
        int clamped = Math.max(0, Math.min(100, intensity));
        peakAlpha = Math.round(255f * clamped / 100f);
    }

    /**
     * Cross-fade to a new colour while sliding the centre to (fx, fy), both
     * fractions of the view. The first call lands instantly so nothing sweeps
     * in across the screen at startup.
     */
    void moveTo(int newColor, float fx, float fy) {
        if (animator != null) animator.cancel();

        if (Color.alpha(color) == 0) {
            color = newColor;
            cx = fx;
            cy = fy;
            shaderStale = true;
            invalidate();
            return;
        }

        final int fromColor = color;
        final float fromX = cx, fromY = cy;
        final float toX = fx, toY = fy;
        final int toColor = newColor;

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(FADE_MS);
        animator.setInterpolator(new DecelerateInterpolator(1.4f));
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                float t = a.getAnimatedFraction();
                color = blend(fromColor, toColor, t);
                cx = fromX + (toX - fromX) * t;
                cy = fromY + (toY - fromY) * t;
                shaderStale = true;
                invalidate();
            }
        });
        animator.start();
    }

    private static int blend(int from, int to, float t) {
        int r = Math.round(Color.red(from)   + (Color.red(to)   - Color.red(from))   * t);
        int g = Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * t);
        int b = Math.round(Color.blue(from)  + (Color.blue(to)  - Color.blue(from))  * t);
        return Color.rgb(r, g, b);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        shaderStale = true;
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) animator.cancel();
    }

    @Override protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0 || Color.alpha(color) == 0 || peakAlpha == 0) return;

        if (shaderStale) {
            int rgb = color & 0x00FFFFFF;
            paint.setShader(new RadialGradient(
                    cx * w, cy * h, w * RADIUS_SCALE,
                    new int[] {
                            rgb | (peakAlpha << 24),
                            rgb | ((peakAlpha / 4) << 24),
                            rgb                              // alpha 0 at the edge
                    },
                    new float[] { 0f, 0.42f, 1f },
                    Shader.TileMode.CLAMP));
            shaderStale = false;
        }
        canvas.drawRect(0, 0, w, h, paint);
    }
}
