package com.binario.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.Choreographer;
import android.view.View;

/**
 * The single translucent pad that springs from tile to tile. There is exactly
 * one in the whole launcher - tiles never draw borders of their own.
 */
final class GlassHighlight extends View implements Choreographer.FrameCallback {
    private final Spring sx, sy, sw, sh;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float radius;

    private boolean running;
    private boolean placed;
    private long lastFrame;

    GlassHighlight(Context c, float stiffness, float dampingRatio, float radiusPx, float strokePx) {
        super(c);
        sx = new Spring(stiffness, dampingRatio);
        sy = new Spring(stiffness, dampingRatio);
        sw = new Spring(stiffness, dampingRatio);
        sh = new Spring(stiffness, dampingRatio);
        radius = radiusPx;

        fill.setColor(0x1FFFFFFF);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(strokePx);
        stroke.setColor(0x59FFFFFF);

        setAlpha(0f);
    }

    /** Spring to a new rect. The first call lands instantly so nothing flies in. */
    void moveTo(Rect r) {
        if (!placed) { snapTo(r); return; }
        sx.setTarget(r.left);
        sy.setTarget(r.top);
        sw.setTarget(r.width());
        sh.setTarget(r.height());
        animate().alpha(1f).setDuration(120).start();
        start();
    }

    void snapTo(Rect r) {
        sx.snapTo(r.left);
        sy.snapTo(r.top);
        sw.snapTo(r.width());
        sh.snapTo(r.height());
        placed = true;
        setAlpha(1f);
        invalidate();
    }

    /** Used when focus moves to a pill, which carries its own focused look. */
    void hide() {
        animate().alpha(0f).setDuration(120).start();
    }

    private void start() {
        if (running) return;
        running = true;
        lastFrame = 0L;
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override public void doFrame(long nanos) {
        if (lastFrame == 0L) lastFrame = nanos;
        float dt = Math.min((nanos - lastFrame) / 1000000000f, 0.032f);
        lastFrame = nanos;

        // single | so every axis steps, not just the first still-moving one
        boolean moving = sx.step(dt) | sy.step(dt) | sw.step(dt) | sh.step(dt);
        invalidate();

        if (moving) Choreographer.getInstance().postFrameCallback(this);
        else running = false;
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Choreographer.getInstance().removeFrameCallback(this);
        running = false;
    }

    @Override protected void onDraw(Canvas canvas) {
        if (!placed) return;
        float l = sx.value(), t = sy.value();
        float r = l + sw.value(), b = t + sh.value();
        canvas.drawRoundRect(l, t, r, b, radius, radius, fill);
        canvas.drawRoundRect(l, t, r, b, radius, radius, stroke);
    }
}
