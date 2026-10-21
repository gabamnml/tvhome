package com.binario.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything the TV can launch, split into the dock and the grid. */
final class AppRepository {

    static final class Result {
        final List<AppEntry> dock = new ArrayList<AppEntry>();
        final List<AppEntry> grid = new ArrayList<AppEntry>();
    }

    private AppRepository() { }

    static Result load(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        Intent probe = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);

        Map<String, AppEntry> byPackage = new LinkedHashMap<String, AppEntry>();
        for (ResolveInfo ri : pm.queryIntentActivities(probe, 0)) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null || !ai.enabled) continue;
            if (byPackage.containsKey(ai.packageName)) continue;   // first activity wins

            Intent launch = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    .setComponent(new ComponentName(ai.packageName, ai.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

            Drawable art = pickArt(pm, ai);

            byPackage.put(ai.packageName,
                    new AppEntry(ai.packageName, String.valueOf(ri.loadLabel(pm)), launch, art));
        }

        for (String hidden : ctx.getResources().getStringArray(R.array.hidden_packages)) {
            byPackage.remove(hidden);
        }

        Result out = new Result();
        // Dock first, in exactly the order config.xml lists. Missing ones are skipped.
        for (String pkg : ctx.getResources().getStringArray(R.array.dock_packages)) {
            AppEntry e = byPackage.remove(pkg);
            if (e != null) out.dock.add(e);
        }

        out.grid.addAll(byPackage.values());
        Collections.sort(out.grid, new Comparator<AppEntry>() {
            @Override public int compare(AppEntry a, AppEntry b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return out;
    }

    /**
     * The first artwork that actually paints something.
     *
     * A non-null banner isn't enough: YouTube's banner resource resolves fine
     * but draws nothing when loaded from another process, which left an empty
     * tile in the dock. So each candidate is rasterised tiny and checked for
     * coverage before it's accepted.
     */
    private static Drawable pickArt(PackageManager pm, ActivityInfo ai) {
        Drawable[] candidates = new Drawable[] {
                load(ai, pm, 0),   // activity banner
                load(ai, pm, 1),   // application banner
                load(ai, pm, 2),   // logo
                load(ai, pm, 3),   // icon
        };
        for (Drawable d : candidates) if (paintsSomething(d)) return d;
        for (Drawable d : candidates) if (d != null) return d;
        return null;
    }

    private static Drawable load(ActivityInfo ai, PackageManager pm, int which) {
        try {
            switch (which) {
                case 0:  return ai.loadBanner(pm);
                case 1:  return ai.applicationInfo.loadBanner(pm);
                case 2:  return ai.loadLogo(pm);
                default: return ai.loadIcon(pm);
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean paintsSomething(Drawable d) {
        if (d == null) return false;
        Bitmap b = null;
        Rect saved = new Rect(d.getBounds());   // never leave our bounds behind
        try {
            b = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888);
            d.setBounds(0, 0, 12, 12);
            d.draw(new Canvas(b));
            int covered = 0;
            for (int y = 0; y < 12; y++) {
                for (int x = 0; x < 12; x++) {
                    if (Color.alpha(b.getPixel(x, y)) > 32) covered++;
                }
            }
            return covered >= 8;          // ~5% coverage is enough to count
        } catch (Throwable t) {
            return false;
        } finally {
            d.setBounds(saved);
            if (b != null) b.recycle();
        }
    }
}
