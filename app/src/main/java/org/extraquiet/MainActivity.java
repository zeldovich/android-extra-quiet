package org.extraquiet;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.StatusBarManager;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private Switch enabledSwitch;
    private TextView reductionLabel;
    private TextView statusText;
    private boolean updatingUi;

    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener =
            (prefs, key) -> enabledSwitch.post(this::refresh);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        enabledSwitch = findViewById(R.id.enabled_switch);
        reductionLabel = findViewById(R.id.reduction_label);
        statusText = findViewById(R.id.status_text);
        SeekBar seekBar = findViewById(R.id.reduction_seek);
        Button addTile = findViewById(R.id.add_tile_button);

        seekBar.setMin(Prefs.MIN_REDUCTION_DB);
        seekBar.setMax(Prefs.MAX_REDUCTION_DB);
        seekBar.setProgress(Prefs.getReductionDb(this));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    Prefs.setReductionDb(MainActivity.this, progress);
                }
                reductionLabel.setText(getString(R.string.reduction_label, progress));
            }

            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        reductionLabel.setText(getString(R.string.reduction_label, seekBar.getProgress()));

        enabledSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (updatingUi) {
                return;
            }
            if (checked) {
                QuietService.start(this);
            } else {
                QuietService.stop(this);
            }
            // The service starts asynchronously; refresh once it has.
            statusText.postDelayed(this::refresh, 300);
        });

        if (Build.VERSION.SDK_INT >= 33) {
            addTile.setOnClickListener(v -> requestAddTile());
        } else {
            addTile.setVisibility(View.GONE);
        }

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 0);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Prefs.get(this).registerOnSharedPreferenceChangeListener(prefListener);
        refresh();
    }

    @Override
    protected void onPause() {
        Prefs.get(this).unregisterOnSharedPreferenceChangeListener(prefListener);
        super.onPause();
    }

    private void refresh() {
        boolean running = QuietService.isRunning();
        updatingUi = true;
        enabledSwitch.setChecked(running);
        updatingUi = false;
        statusText.setText(running
                ? getString(R.string.status_on, QuietService.getGlobalStatus())
                : getString(R.string.status_off));
    }

    @TargetApi(33)
    private void requestAddTile() {
        StatusBarManager sbm = getSystemService(StatusBarManager.class);
        sbm.requestAddTileService(
                new ComponentName(this, QuietTileService.class),
                getString(R.string.tile_label),
                Icon.createWithResource(this, R.drawable.ic_tile),
                getMainExecutor(),
                result -> {
                    if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) {
                        Toast.makeText(this, R.string.tile_already_added, Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
