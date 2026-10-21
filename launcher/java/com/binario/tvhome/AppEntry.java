package com.binario.tvhome;

import android.content.Intent;
import android.graphics.drawable.Drawable;

/** One launchable app. */
final class AppEntry {
    final String pkg;
    final String label;
    final Intent launch;
    final Drawable art;

    private int glow;          // sampled lazily, 0 = not yet computed
    private boolean sampled;

    AppEntry(String pkg, String label, Intent launch, Drawable art) {
        this.pkg = pkg;
        this.label = label;
        this.launch = launch;
        this.art = art;
    }

    /** Dominant banner colour, computed once and kept. */
    int glow(int fallback) {
        if (!sampled) {
            glow = ColorSampler.sample(art, fallback);
            sampled = true;
        }
        return glow;
    }
}
