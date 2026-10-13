package net.kolya.android.extraquiet;

import android.app.Activity;
import android.os.Bundle;

/** Invisible activity that turns Extra quiet on and immediately finishes. */
public class ToggleActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        QuietService.start(this);
        finish();
    }
}
