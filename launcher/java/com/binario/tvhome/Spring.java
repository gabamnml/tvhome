package com.binario.tvhome;

/**
 * One axis of a damped spring. Four of these drive the glass highlight.
 * Exists so the launcher doesn't need androidx.dynamicanimation - the whole
 * APK carries no third-party code.
 */
final class Spring {
    private final float stiffness;
    private final float damping;
    private float value, target, velocity;

    /** dampingRatio 1.0 stops dead; below 1.0 overshoots slightly. */
    Spring(float stiffness, float dampingRatio) {
        this.stiffness = stiffness;
        this.damping = 2f * dampingRatio * (float) Math.sqrt(stiffness);
    }

    void snapTo(float v) { value = target = v; velocity = 0f; }
    void setTarget(float t) { target = t; }
    float value() { return value; }

    /** Advance by dt seconds. Returns true while still moving. */
    boolean step(float dt) {
        float accel = stiffness * (target - value) - damping * velocity;
        velocity += accel * dt;
        value += velocity * dt;
        if (Math.abs(target - value) < 0.4f && Math.abs(velocity) < 0.4f) {
            value = target;
            velocity = 0f;
            return false;
        }
        return true;
    }
}
