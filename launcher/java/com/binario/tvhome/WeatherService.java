package com.binario.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Current conditions from Open-Meteo - free, no API key, no registration, so
 * there is no credential to manage or leak. Position comes from an IP lookup
 * that is cached for a week, because a TV does not move.
 *
 * Deliberately NOT a poller. The original brief said no network polling on a
 * box with ~1 GB free, so this:
 *   - paints the CACHED reading first, which means the screen is populated
 *     instantly at boot and stays populated with no network at all;
 *   - goes to the network at most once every REFRESH_MS, and only while the
 *     home screen is actually resumed;
 *   - fails silently. A home screen must never show an error.
 */
final class WeatherService {

    interface Listener { void onWeather(Weather w); }

    private static final long REFRESH_MS = 30L * 60L * 1000L;             // 30 min
    private static final long GEO_TTL_MS = 7L * 24L * 60L * 60L * 1000L;  // a week
    private static final int TIMEOUT_MS = 8000;

    private static final String GEO_URL = "https://ipapi.co/json/";
    private static final String WX_URL =
            "https://api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s"
                    + "&current=temperature_2m,weather_code"
                    + "&daily=temperature_2m_max,temperature_2m_min"
                    + "&timezone=auto&forecast_days=1&temperature_unit=%s";

    private final Context ctx;
    private final SharedPreferences prefs;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile boolean fetching;

    WeatherService(Context context) {
        this.ctx = context.getApplicationContext();
        this.prefs = ctx.getSharedPreferences("tvhome_weather", Context.MODE_PRIVATE);
    }

    /** The last reading we managed to get, or null if we never have. */
    Weather cached() {
        if (!prefs.contains("temp")) return null;
        return new Weather(
                prefs.getFloat("temp", 0f),
                prefs.getFloat("max", 0f),
                prefs.getFloat("min", 0f),
                prefs.getInt("code", -1));
    }

    /** Refreshes only if the cache is stale. Safe to call on every resume and tick. */
    void refresh(final Listener listener) {
        if (fetching) return;
        if (System.currentTimeMillis() - prefs.getLong("at", 0L) < REFRESH_MS) return;
        if (!online()) return;

        fetching = true;
        new Thread(new Runnable() {
            @Override public void run() {
                Weather fetched = null;
                try {
                    fetched = fetch();
                } catch (Throwable ignored) {
                    // offline, rate-limited, bad JSON - keep whatever we had
                } finally {
                    fetching = false;
                }
                if (fetched == null) return;
                final Weather result = fetched;
                ui.post(new Runnable() {
                    @Override public void run() { listener.onWeather(result); }
                });
            }
        }, "tvhome-weather").start();
    }

    private Weather fetch() throws Exception {
        String lat = prefs.getString("lat", null);
        String lon = prefs.getString("lon", null);
        boolean geoStale = System.currentTimeMillis() - prefs.getLong("geo_at", 0L) > GEO_TTL_MS;

        if (lat == null || lon == null || geoStale) {
            JSONObject geo = new JSONObject(get(GEO_URL));
            lat = String.valueOf(geo.getDouble("latitude"));
            lon = String.valueOf(geo.getDouble("longitude"));
            prefs.edit()
                    .putString("lat", lat)
                    .putString("lon", lon)
                    .putString("city", geo.optString("city", ""))
                    .putLong("geo_at", System.currentTimeMillis())
                    .apply();
        }

        String unit = ctx.getResources().getBoolean(R.bool.weather_fahrenheit)
                ? "fahrenheit" : "celsius";
        JSONObject root = new JSONObject(get(String.format(Locale.US, WX_URL, lat, lon, unit)));

        JSONObject current = root.getJSONObject("current");
        JSONObject daily = root.getJSONObject("daily");

        float temp = (float) current.getDouble("temperature_2m");
        int code = current.getInt("weather_code");
        float max = (float) daily.getJSONArray("temperature_2m_max").getDouble(0);
        float min = (float) daily.getJSONArray("temperature_2m_min").getDouble(0);

        prefs.edit()
                .putFloat("temp", temp).putFloat("max", max).putFloat("min", min)
                .putInt("code", code)
                .putLong("at", System.currentTimeMillis())
                .apply();

        return new Weather(temp, max, min, code);
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setRequestProperty("User-Agent", "tvhome/1.0");
            if (c.getResponseCode() != 200) {
                throw new IllegalStateException("HTTP " + c.getResponseCode());
            }
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    private boolean online() {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo active = cm != null ? cm.getActiveNetworkInfo() : null;
            return active != null && active.isConnected();
        } catch (Throwable t) {
            return false;
        }
    }
}
