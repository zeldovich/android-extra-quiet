package net.kolya.android.extraquiet;

import android.content.Context;
import android.content.SharedPreferences;

/** Persistent settings. */
final class Prefs {
    static final String KEY_ENABLED = "enabled";
    static final String KEY_REDUCTION_DB = "reduction_db";

    static final int MIN_REDUCTION_DB = 1;
    static final int MAX_REDUCTION_DB = 50;
    static final int DEFAULT_REDUCTION_DB = 20;

    private Prefs() {}

    static SharedPreferences get(Context c) {
        return c.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    /** Whether the user wants Extra quiet on (used to restore after the process is killed). */
    static boolean isEnabled(Context c) {
        return get(c).getBoolean(KEY_ENABLED, false);
    }

    static void setEnabled(Context c, boolean enabled) {
        get(c).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    /** How many dB to cut from all audio output, as a positive number. */
    static int getReductionDb(Context c) {
        int db = get(c).getInt(KEY_REDUCTION_DB, DEFAULT_REDUCTION_DB);
        return Math.max(MIN_REDUCTION_DB, Math.min(MAX_REDUCTION_DB, db));
    }

    static void setReductionDb(Context c, int db) {
        get(c).edit().putInt(KEY_REDUCTION_DB, db).apply();
    }
}
