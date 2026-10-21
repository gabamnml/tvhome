package com.binario.tvhome;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.Rect;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** One app tile: its banner, and a name that only appears while focused. */
final class TileView extends LinearLayout {

    /** How much a tile grows while focused. The glass highlight needs this too. */
    static final float FOCUS_SCALE = 1.08f;

    final AppEntry entry;
    /** Grid row this tile sits in; -1 means it's a dock tile. */
    int row = -1;

    private final ImageView art;
    private final TextView label;
    private final boolean labelOnFocus;

    TileView(Context c, AppEntry entry, int artW, int artH, int labelH,
             boolean labelOnFocus, final int cornerPx) {
        super(c);
        this.entry = entry;
        this.labelOnFocus = labelOnFocus;

        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setClipChildren(false);
        setClipToPadding(false);
        setFocusable(true);
        setFocusableInTouchMode(false);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);

        art = new ImageView(c);
        art.setScaleType(ImageView.ScaleType.FIT_CENTER);
        art.setBackgroundResource(R.drawable.tile_fallback);
        art.setImageDrawable(entry.art);

        // Most banners are square-cornered artwork, which looked wrong sitting
        // inside the rounded glass highlight. Clip them to a matching radius.
        if (cornerPx > 0) {
            art.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline outline) {
                    outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), cornerPx);
                }
            });
            art.setClipToOutline(true);
        }

        addView(art, new LayoutParams(artW, artH));

        // Dock tiles get no label slot at all - the big name above the dock does
        // that job, and an empty reserved strip made the dock band bottom-heavy.
        if (labelOnFocus) {
            TextView t = new TextView(c);
            t.setText(entry.label);
            t.setTypeface(Fonts.regular);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            t.setTextColor(0xE6FFFFFF);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setGravity(Gravity.CENTER);
            t.setAlpha(0f);
            LayoutParams lp = new LayoutParams(artW, labelH);
            lp.topMargin = labelH / 4;
            addView(t, lp);
            label = t;
        } else {
            label = null;
        }

        // Scale around the banner, not around banner+label, or focused tiles
        // appear to drift upward as they grow.
        setPivotX(artW / 2f);
        setPivotY(artH / 2f);
    }

    @Override protected void onFocusChanged(boolean gained, int direction, Rect prev) {
        super.onFocusChanged(gained, direction, prev);
        float scale = gained ? FOCUS_SCALE : 1f;
        animate().scaleX(scale).scaleY(scale).setDuration(180).start();
        if (label != null) {
            label.animate().alpha(gained ? 1f : 0f).setDuration(160).start();
        }
    }

    /** The banner's bounds, which is what the glass highlight frames. */
    Rect artBounds() {
        return new Rect(art.getLeft(), art.getTop(), art.getRight(), art.getBottom());
    }
}
