package com.binario.tvhome;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

/** Throwaway. Shows one line so a screenshot can confirm it really took over HOME. */
public class GateActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView t = new TextView(this);
        t.setText("GATE OK - sideload + HOME accepted");
        t.setTextColor(Color.WHITE);
        t.setTextSize(32f);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundColor(Color.BLACK);
        setContentView(t);
    }
}
