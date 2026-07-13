package xyz.surina.proracingobd.activities;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import xyz.surina.proracingobd.R;

/**
 * Mode Selection Screen — shown after login.
 *
 * Two paths:
 *   🏁  PRO RACING  → MainActivity (live gauges, tuning, ECU flash)
 *   ⚡  WORKSHOP    → WorkshopActivity (CAN monitor, PID browser, CarHackerKit tools)
 */
public class ModeSelectActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mode_select);

        // Racing panel — full FrameLayout tap
        findViewById(R.id.panel_racing).setOnClickListener(v ->
                startActivity(new Intent(this, MainActivity.class)));

        // Workshop panel
        findViewById(R.id.panel_workshop).setOnClickListener(v ->
                startActivity(new Intent(this, WorkshopActivity.class)));
    }
}
