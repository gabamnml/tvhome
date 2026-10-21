package com.binario.tvhome;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Typeface;

/**
 * Outfit (SIL Open Font License) shipped as a single variable font and
 * instanced at three weights. Falls back to the platform sans if the asset is
 * missing or the device refuses the weight axis.
 */
final class Fonts {
    static Typeface thin;     // the clock
    static Typeface light;    // the focused dock app's name
    static Typeface regular;  // pills and tile labels

    private Fonts() { }

    static void init(Context c) {
        if (thin != null) return;
        AssetManager am = c.getAssets();
        thin    = instance(am, 200, "sans-serif-thin");
        light   = instance(am, 300, "sans-serif-light");
        regular = instance(am, 400, "sans-serif");
    }

    private static Typeface instance(AssetManager am, int weight, String fallback) {
        try {
            Typeface t = new Typeface.Builder(am, "fonts/outfit.ttf")
                    .setFontVariationSettings("'wght' " + weight)
                    .build();
            if (t != null) return t;
        } catch (Throwable ignored) {
            // asset missing, or the platform wouldn't apply the variation axis
        }
        return Typeface.create(fallback, Typeface.NORMAL);
    }
}
