package com.binario.tvhome;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class HomeActivity extends Activity {

    // Layout, in dp. The box is 960 x 540 dp (1920x1080 at 320dpi).
    private static final int SIDE = 48, TOP = 36;
    private static final int TILE_W = 152, TILE_H = 86, GAP = 20;
    private static final int DOCK_PAD = 22, LABEL_H = 22;
    /** Visible margin between the focused tile and the glass highlight, all round. */
    private static final int GLASS_GAP = 5;
    private static final int ROW_GAP = 26;
    // Where a focused grid row parks. 40dp is chosen so one Down from the dock
    // scrolls the dock fully off rather than leaving a row of half-cut tiles.
    private static final int SCROLL_ANCHOR = 40;
    private static final int GLOW_FALLBACK = 0xFF8A8A8A;

    private static final String PREFS = "tvhome";
    private static final String KEY_KIND = "focus_kind";   // 0 = dock, 1 = grid
    private static final String KEY_INDEX = "focus_index";

    private FrameLayout root, content;
    private ScrollView scroll;
    private LinearLayout column, dockBand, gridBlock;
    private GlassHighlight glass;
    private BackdropView backdrop;
    private TextView greetingView, clockView, dateView, wxNow, wxRange;
    private View weatherBlock;
    private WeatherService weather;

    private final List<TileView> dockTiles = new ArrayList<TileView>();
    private final List<List<TileView>> gridRows = new ArrayList<List<TileView>>();
    private final List<View> gridRowViews = new ArrayList<View>();
    private final List<View> pills = new ArrayList<View>();

    private BroadcastReceiver clockReceiver;
    private int tileCornerDp;

    // ---------------------------------------------------------------- lifecycle

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Fonts.init(this);
        weather = new WeatherService(this);
        AppRepository.Result apps = AppRepository.load(this);
        buildUi(apps);
        wireFocus();
        restoreFocusWhenLaidOut();
    }

    @Override protected void onResume() {
        super.onResume();
        updateClock();
        applyWeather(weather.cached());   // instant, and works with no network
        refreshWeather();

        clockReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                updateClock();
                refreshWeather();      // self-throttles to once every 30 minutes
            }
        };
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(clockReceiver, f);
    }

    @Override protected void onPause() {
        super.onPause();
        if (clockReceiver != null) {
            try { unregisterReceiver(clockReceiver); } catch (Throwable ignored) { }
            clockReceiver = null;
        }
        rememberFocus();
    }

    /** HOME pressed while already home: go back to the top, don't reload. */
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        scroll.smoothScrollTo(0, 0);
        if (!dockTiles.isEmpty()) dockTiles.get(0).requestFocus();
    }

    /** A launcher must not finish on Back. */
    @Override public void onBackPressed() {
        scroll.smoothScrollTo(0, 0);
        if (!dockTiles.isEmpty()) dockTiles.get(0).requestFocus();
    }

    // ------------------------------------------------------------------- build

    private void buildUi(AppRepository.Result apps) {
        int screenH = getResources().getDisplayMetrics().heightPixels;
        tileCornerDp = getResources().getInteger(R.integer.tile_corner_radius);

        root = new FrameLayout(this);
        root.setBackgroundResource(R.drawable.wallpaper);

        // Behind everything, and outside the ScrollView so it never scrolls.
        backdrop = new BackdropView(this, getResources().getInteger(R.integer.backdrop_intensity));
        root.addView(backdrop, match(ViewGroup.LayoutParams.MATCH_PARENT));

        scroll = new HomeScroll(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        root.addView(scroll, match(ViewGroup.LayoutParams.MATCH_PARENT));

        content = new FrameLayout(this);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        scroll.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        glass = new GlassHighlight(this,
                getResources().getInteger(R.integer.spring_stiffness),
                Float.parseFloat(getString(R.string.spring_damping)),
                // Concentric with a FOCUSED tile: its corner radius grows with
                // the tile, so scale it too before adding the gap.
                dp(tileCornerDp) * TileView.FOCUS_SCALE + dp(GLASS_GAP), dp(1.5f));
        content.addView(glass, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);
        column.setClipToPadding(false);
        content.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- first screen: exactly one display tall, so Down reveals the grid
        FrameLayout screenOne = new FrameLayout(this);
        screenOne.setClipChildren(false);
        screenOne.setClipToPadding(false);
        column.addView(screenOne, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, screenH));

        screenOne.addView(buildTopBar(), topBarParams());
        screenOne.addView(buildBottom(apps), bottomParams());

        // ---- the grid, below the fold
        gridBlock = new LinearLayout(this);
        gridBlock.setOrientation(LinearLayout.VERTICAL);
        gridBlock.setClipChildren(false);
        gridBlock.setClipToPadding(false);
        buildGrid(apps.grid);
        column.addView(gridBlock, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }

    private View buildTopBar() {
        FrameLayout bar = new FrameLayout(this);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);

        // Every child here needs EXPLICIT wrap_content params. A vertical
        // LinearLayout's generateDefaultLayoutParams() hands out MATCH_PARENT
        // width, so an addView(child) with no params made the clock match the
        // parent - whose width came from the only wrap_content child, the date.
        // The clock then measured 232px for text needing 321px and lost its
        // " PM".
        greetingView = new TextView(this);
        greetingView.setTypeface(Fonts.light);
        greetingView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        greetingView.setTextColor(0xB3FFFFFF);
        left.addView(greetingView, wrap());

        clockView = new TextView(this);
        clockView.setTypeface(Fonts.thin);
        clockView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 46f);
        clockView.setTextColor(0xFFFFFFFF);
        clockView.setLetterSpacing(-0.02f);
        clockView.setIncludeFontPadding(false);

        // Clock and weather share a row, bottom-aligned so the two small weather
        // lines sit on the clock's baseline rather than floating beside it.
        LinearLayout clockRow = new LinearLayout(this);
        clockRow.setOrientation(LinearLayout.HORIZONTAL);
        clockRow.setGravity(Gravity.BOTTOM);
        clockRow.addView(clockView, wrap());

        LinearLayout wx = new LinearLayout(this);
        wx.setOrientation(LinearLayout.VERTICAL);
        wx.setVisibility(View.GONE);            // nothing shown until there's a reading

        wxNow = new TextView(this);
        wxNow.setTypeface(Fonts.light);
        wxNow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        wxNow.setTextColor(0xE6FFFFFF);
        wx.addView(wxNow, wrap());

        wxRange = new TextView(this);
        wxRange.setTypeface(Fonts.regular);
        wxRange.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        wxRange.setTextColor(0x8CFFFFFF);
        LinearLayout.LayoutParams rangeLp = wrap();
        rangeLp.topMargin = dp(2);
        wx.addView(wxRange, rangeLp);

        LinearLayout.LayoutParams wxLp = wrap();
        wxLp.leftMargin = dp(26);
        wxLp.bottomMargin = dp(9);
        clockRow.addView(wx, wxLp);
        weatherBlock = wx;

        left.addView(clockRow, wrap());

        dateView = new TextView(this);
        dateView.setTypeface(Fonts.regular);
        dateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        dateView.setTextColor(0x8CFFFFFF);
        LinearLayout.LayoutParams dlp = wrap();
        dlp.topMargin = dp(4);
        left.addView(dateView, dlp);

        bar.addView(left, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START));

        // Just the cog. Power duplicated the remote's own button, and Wi-Fi is
        // inside Settings anyway - dropping it also dropped this app's need for
        // the location permission.
        LinearLayout pillRow = new LinearLayout(this);
        pillRow.setOrientation(LinearLayout.HORIZONTAL);

        View settings = makeIconPill(R.drawable.ic_settings, R.string.pill_settings,
                new Runnable() {
                    @Override public void run() { start(new Intent(Settings.ACTION_SETTINGS), null); }
                });
        pillRow.addView(settings, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));
        pills.add(settings);

        bar.addView(pillRow, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END));
        return bar;
    }

    private View buildBottom(AppRepository.Result apps) {
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setGravity(Gravity.CENTER_HORIZONTAL);
        bottom.setClipChildren(false);
        bottom.setClipToPadding(false);

        // No app name here any more - the background colour is the only thing
        // that reacts to focus. The dock stays put because this block is
        // bottom-anchored, so removing the name just leaves clean space above.
        dockBand = new LinearLayout(this);
        dockBand.setOrientation(LinearLayout.HORIZONTAL);
        dockBand.setBackgroundResource(R.drawable.dock_band);
        dockBand.setPadding(dp(DOCK_PAD), dp(DOCK_PAD), dp(DOCK_PAD), dp(DOCK_PAD));
        dockBand.setClipChildren(false);
        dockBand.setClipToPadding(false);

        for (int i = 0; i < apps.dock.size(); i++) {
            TileView t = new TileView(this, apps.dock.get(i),
                    dp(TILE_W), dp(TILE_H), dp(LABEL_H), false, dp(tileCornerDp));
            t.setId(View.generateViewId());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.leftMargin = dp(GAP);
            dockBand.addView(t, lp);
            dockTiles.add(t);
        }

        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.gravity = Gravity.CENTER_HORIZONTAL;
        bottom.addView(dockBand, blp);
        return bottom;
    }

    private void buildGrid(List<AppEntry> entries) {
        int cols = getResources().getInteger(R.integer.grid_columns);
        boolean labels = getResources().getBoolean(R.bool.show_tile_labels);

        // Pad the whole block rather than centring each row: a final short row
        // would otherwise centre on its own and break the column alignment.
        // This lands the grid columns exactly under the dock tiles.
        int gridW = cols * dp(TILE_W) + (cols - 1) * dp(GAP);
        int pad = Math.max(dp(SIDE),
                (getResources().getDisplayMetrics().widthPixels - gridW) / 2);
        gridBlock.setPadding(pad, 0, pad, dp(64));

        List<TileView> row = null;
        LinearLayout rowView = null;

        for (int i = 0; i < entries.size(); i++) {
            if (i % cols == 0) {
                rowView = new LinearLayout(this);
                rowView.setOrientation(LinearLayout.HORIZONTAL);
                rowView.setGravity(Gravity.START);
                rowView.setClipChildren(false);
                rowView.setClipToPadding(false);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.bottomMargin = dp(ROW_GAP);
                gridBlock.addView(rowView, rlp);

                row = new ArrayList<TileView>();
                gridRows.add(row);
                gridRowViews.add(rowView);
            }

            TileView t = new TileView(this, entries.get(i),
                    dp(TILE_W), dp(TILE_H), dp(LABEL_H), labels, dp(tileCornerDp));
            t.setId(View.generateViewId());
            t.row = gridRows.size() - 1;

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i % cols != 0) lp.leftMargin = dp(GAP);
            rowView.addView(t, lp);
            row.add(t);
        }
    }

    // ------------------------------------------------------------------ focus

    /**
     * Every next-focus edge is set by hand. Nothing is left to focus search, so
     * Up/Down always move exactly one row and the ends of the top bar point at
     * themselves instead of falling through to the tiles below.
     */
    private void wireFocus() {
        View.OnFocusChangeListener listener = new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                if (has) onFocused(v);
            }
        };

        for (int i = 0; i < pills.size(); i++) {
            View p = pills.get(i);
            p.setOnFocusChangeListener(listener);
            p.setNextFocusUpId(p.getId());                                   // nothing above
            p.setNextFocusLeftId(pills.get(Math.max(0, i - 1)).getId());     // ends clamp
            p.setNextFocusRightId(pills.get(Math.min(pills.size() - 1, i + 1)).getId());
            // Starting point only; onFocused() keeps this pointing at whichever
            // tile you actually came up from, so Down puts you back there.
            if (!dockTiles.isEmpty()) {
                p.setNextFocusDownId(dockTiles.get(0).getId());
            }
        }

        for (int i = 0; i < dockTiles.size(); i++) {
            TileView t = dockTiles.get(i);
            t.setOnFocusChangeListener(listener);
            t.setNextFocusLeftId(dockTiles.get(Math.max(0, i - 1)).getId());
            t.setNextFocusRightId(dockTiles.get(Math.min(dockTiles.size() - 1, i + 1)).getId());
            if (!pills.isEmpty()) {
                int p = Math.min(pills.size() - 1, Math.max(0, i - 1));
                t.setNextFocusUpId(pills.get(p).getId());
            } else {
                t.setNextFocusUpId(t.getId());
            }
            t.setNextFocusDownId(gridRows.isEmpty() ? t.getId() : clampInRow(0, i).getId());
            t.setOnClickListener(launcherFor(t));
        }

        for (int r = 0; r < gridRows.size(); r++) {
            List<TileView> row = gridRows.get(r);
            for (int c = 0; c < row.size(); c++) {
                TileView t = row.get(c);
                t.setOnFocusChangeListener(listener);
                t.setNextFocusLeftId(row.get(Math.max(0, c - 1)).getId());
                t.setNextFocusRightId(row.get(Math.min(row.size() - 1, c + 1)).getId());

                if (r == 0) {
                    t.setNextFocusUpId(dockTiles.isEmpty()
                            ? t.getId()
                            : dockTiles.get(Math.min(dockTiles.size() - 1, c)).getId());
                } else {
                    t.setNextFocusUpId(clampInRow(r - 1, c).getId());
                }

                t.setNextFocusDownId(r == gridRows.size() - 1
                        ? t.getId()
                        : clampInRow(r + 1, c).getId());

                t.setOnClickListener(launcherFor(t));
            }
        }
    }

    /** The tile at column c of row r, or the last one if that row is shorter. */
    private TileView clampInRow(int r, int c) {
        List<TileView> row = gridRows.get(r);
        return row.get(Math.min(c, row.size() - 1));
    }

    private View.OnClickListener launcherFor(final TileView tile) {
        return new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    startActivity(tile.entry.launch);
                } catch (Throwable t) {
                    Toast.makeText(HomeActivity.this,
                            getString(R.string.launch_failed, tile.entry.label),
                            Toast.LENGTH_SHORT).show();
                }
            }
        };
    }

    private void onFocused(View v) {
        if (!(v instanceof TileView)) {
            // a pill: it carries its own focused look, so the glass steps aside
            glass.hide();
            scroll.smoothScrollTo(0, 0);
            return;
        }

        TileView tile = (TileView) v;
        Rect r = rectInContent(tile, tile.artBounds());

        // The tile is scaled up while focused, and because it's wider than it is
        // tall a uniform scale adds more width than height. Insetting the layout
        // rect by a fixed amount therefore left the sides looking pinched while
        // the top and bottom looked right. So grow the rect to the size the tile
        // actually occupies on screen first, then add one even gap all round.
        float grow = (TileView.FOCUS_SCALE - 1f) / 2f;
        r.inset(-Math.round(r.width() * grow), -Math.round(r.height() * grow));
        r.inset(-dp(GLASS_GAP), -dp(GLASS_GAP));

        glass.moveTo(r);

        updateBackdrop(tile);

        // Coming back Down from the top bar should land where you left, not on
        // some fixed tile.
        for (View p : pills) p.setNextFocusDownId(tile.getId());

        if (tile.row < 0) scroll.smoothScrollTo(0, 0);
        else scrollToRow(tile.row);
    }

    /** Wash the whole background with the focused app's own colour. */
    private void updateBackdrop(TileView tile) {
        if (!getResources().getBoolean(R.bool.show_ambient_glow)) return;

        int[] loc = new int[2];
        tile.getLocationInWindow(loc);
        float w = getResources().getDisplayMetrics().widthPixels;
        float h = getResources().getDisplayMetrics().heightPixels;

        // X is exact (we only ever scroll vertically). Y is the tile's position
        // before any pending scroll settles, which is close enough for something
        // this soft, and is clamped so the wash never parks off-screen.
        float fx = (loc[0] + tile.getWidth() / 2f) / w;
        float fy = (loc[1] + tile.getHeight() / 2f) / h;
        fy = Math.max(0.2f, Math.min(0.9f, fy));

        backdrop.moveTo(tile.entry.glow(GLOW_FALLBACK), fx, fy);
    }

    private void scrollToRow(int row) {
        if (row < 0 || row >= gridRowViews.size()) return;
        int top = rectInContent(gridRowViews.get(row), new Rect(0, 0, 0, 0)).top;
        int target = Math.max(0, top - dp(SCROLL_ANCHOR));
        scroll.smoothScrollTo(0, target);
    }

    /** A descendant's rect in `content` coordinates (i.e. before scrolling). */
    private Rect rectInContent(View v, Rect inner) {
        int x = 0, y = 0;
        View cur = v;
        while (cur != null && cur != content) {
            x += cur.getLeft();
            y += cur.getTop();
            ViewParent p = cur.getParent();
            cur = (p instanceof View) ? (View) p : null;
        }
        return new Rect(x + inner.left, y + inner.top, x + inner.right, y + inner.bottom);
    }

    private void restoreFocusWhenLaidOut() {
        final ViewTreeObserver vto = root.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                ViewTreeObserver o = root.getViewTreeObserver();
                if (o.isAlive()) o.removeOnGlobalLayoutListener(this);

                SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
                int kind = p.getInt(KEY_KIND, 0);
                int index = p.getInt(KEY_INDEX, 0);

                View target = null;
                if (kind == 1) {
                    List<TileView> flat = flatGrid();
                    if (index >= 0 && index < flat.size()) target = flat.get(index);
                } else if (index >= 0 && index < dockTiles.size()) {
                    target = dockTiles.get(index);
                }
                if (target == null && !dockTiles.isEmpty()) target = dockTiles.get(0);
                if (target == null && !gridRows.isEmpty()) target = gridRows.get(0).get(0);
                if (target != null) target.requestFocus();
            }
        });
    }

    private void rememberFocus() {
        View f = getCurrentFocus();
        if (!(f instanceof TileView)) return;
        TileView t = (TileView) f;
        SharedPreferences.Editor e = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        if (t.row < 0) {
            e.putInt(KEY_KIND, 0).putInt(KEY_INDEX, dockTiles.indexOf(t));
        } else {
            e.putInt(KEY_KIND, 1).putInt(KEY_INDEX, flatGrid().indexOf(t));
        }
        e.apply();
    }

    private List<TileView> flatGrid() {
        List<TileView> all = new ArrayList<TileView>();
        for (List<TileView> row : gridRows) all.addAll(row);
        return all;
    }

    // ------------------------------------------------------------- top bar bits

    private void updateClock() {
        Date now = new Date();
        Locale locale = Locale.getDefault();

        // SimpleDateFormat, not android.text.format.DateFormat.format(): the
        // latter silently dropped the AM/PM marker from "h:mm a" on this box,
        // leaving an ambiguous "7:38" at half past seven in the evening.
        String timePattern = DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm a";
        clockView.setText(new SimpleDateFormat(timePattern, locale).format(now));
        dateView.setText(new SimpleDateFormat(
                DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale).format(now));

        Calendar cal = Calendar.getInstance();
        cal.setTime(now);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int greeting;
        if (hour < 12)      greeting = R.string.greeting_morning;
        else if (hour < 18) greeting = R.string.greeting_afternoon;
        else if (hour < 22) greeting = R.string.greeting_evening;
        else                greeting = R.string.greeting_night;
        greetingView.setText(greeting);
    }

    private void applyWeather(Weather w) {
        if (w == null || !getResources().getBoolean(R.bool.weather_enabled)) {
            weatherBlock.setVisibility(View.GONE);
            return;
        }
        int desc = Weather.describe(w.code);
        wxNow.setText(desc == 0
                ? getString(R.string.wx_temp_only, Math.round(w.temp))
                : getString(R.string.wx_now, Math.round(w.temp), getString(desc)));
        wxRange.setText(getString(R.string.wx_max_min, Math.round(w.max), Math.round(w.min)));
        weatherBlock.setVisibility(View.VISIBLE);
    }

    private void refreshWeather() {
        if (!getResources().getBoolean(R.bool.weather_enabled)) return;
        weather.refresh(new WeatherService.Listener() {
            @Override public void onWeather(Weather w) { applyWeather(w); }
        });
    }

    private View makeIconPill(int iconRes, int labelRes, final Runnable action) {
        ImageView v = new ImageView(this);
        v.setId(View.generateViewId());
        v.setImageResource(iconRes);
        v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        v.setBackgroundResource(R.drawable.pill_bg);
        v.setContentDescription(getString(labelRes));   // for TalkBack
        v.setPadding(dp(13), dp(7), dp(13), dp(7));
        v.setFocusable(true);
        v.setFocusableInTouchMode(false);
        v.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { action.run(); }
        });
        return v;
    }

    private void start(Intent primary, Intent fallback) {
        try {
            startActivity(primary);
        } catch (Throwable t) {
            if (fallback == null) return;
            try { startActivity(fallback); } catch (Throwable ignored) { }
        }
    }

    // ----------------------------------------------------------------- helpers

    private FrameLayout.LayoutParams topBarParams() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(SIDE), dp(TOP), dp(SIDE), 0);
        return lp;
    }

    private FrameLayout.LayoutParams bottomParams() {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = dp(46);
        return lp;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private FrameLayout.LayoutParams match(int size) {
        return new FrameLayout.LayoutParams(size, size);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /**
     * ScrollView normally scrolls itself whenever a child takes focus, which
     * half-reveals rows and makes Down feel non-deterministic. We suppress that
     * and drive every scroll from onFocused() instead.
     */
    private static final class HomeScroll extends ScrollView {
        HomeScroll(Context c) { super(c); }

        @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
            return 0;
        }
    }
}
