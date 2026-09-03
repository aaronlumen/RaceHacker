package xyz.surina.racehacker.activities;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import xyz.surina.racehacker.R;

/**
 * Mode Selection Screen — shown after login.
 *
 * Two paths:
 *   🏁  PRO RACING  → MainActivity (live gauges, tuning, ECU flash)
 *   ⚡  WORKSHOP    → bundled CarHackerKit toolkit
 */
public class ModeSelectActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mode_select);

        // Racing panel — full FrameLayout tap
        findViewById(R.id.panel_racing).setOnClickListener(v ->
                startActivity(new Intent(this, MainActivity.class)));

        // Workshop panel — the toolkit is bundled in :app as the :carhackerkit
        // library, so open it directly instead of routing through the lightweight
        // Workshop overview screen.
        findViewById(R.id.panel_workshop).setOnClickListener(v ->
                startActivity(new Intent(this, com.carhacker.kit.ui.MainActivity.class)));
    }
}
