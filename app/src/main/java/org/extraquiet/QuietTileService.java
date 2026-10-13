package org.extraquiet;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

/** The "Extra quiet" Quick Settings tile. */
public class QuietTileService extends TileService {

    @Override
    public void onStartListening() {
        updateTile(QuietService.isRunning());
    }

    @Override
    public void onClick() {
        if (QuietService.isRunning()) {
            QuietService.stop(this);
            updateTile(false);
            return;
        }
        try {
            QuietService.start(this);
            updateTile(true);
        } catch (RuntimeException e) {
            // Some Android versions refuse foreground-service starts from a tile;
            // go through an invisible activity instead.
            Log.w("ExtraQuiet", "Direct start refused, using trampoline", e);
            launchToggleActivity();
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private void launchToggleActivity() {
        Intent intent = new Intent(this, ToggleActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE));
        } else {
            startActivityAndCollapse(intent);
        }
    }

    private void updateTile(boolean on) {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        tile.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setSubtitle(on ? getString(R.string.tile_subtitle_on, Prefs.getReductionDb(this))
                            : getString(R.string.tile_subtitle_off));
        tile.updateTile();
    }
}
