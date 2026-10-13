package net.kolya.android.extraquiet;

import android.media.audiofx.AudioEffect;
import android.media.audiofx.DynamicsProcessing;
import android.media.audiofx.Equalizer;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * Attaches attenuating audio effects to audio sessions.
 *
 * Session 0 is the global output mix, which covers all apps on most devices.
 * Individual player sessions (announced by music apps via
 * ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION) are handled too, for devices
 * where the global mix doesn't accept effects.
 *
 * DynamicsProcessing's input gain gives an arbitrary, precise cut. If it's
 * unavailable, an Equalizer with every band at its minimum is used instead,
 * which gives a smaller (usually ~15 dB) cut.
 */
final class QuietEngine {
    private static final String TAG = "ExtraQuiet";
    /** High priority so we win control of the session over other effect apps. */
    private static final int PRIORITY = 10000;

    private final Map<Integer, AudioEffect> effects = new HashMap<>();
    private float reductionDb;

    QuietEngine(float reductionDb) {
        this.reductionDb = reductionDb;
    }

    /** Returns true if an effect is attached to the given session. */
    boolean attach(int session) {
        if (effects.containsKey(session)) {
            return true;
        }
        AudioEffect effect = createDynamicsProcessing(session);
        if (effect == null) {
            effect = createEqualizer(session);
        }
        if (effect == null) {
            return false;
        }
        effects.put(session, effect);
        return true;
    }

    void detach(int session) {
        AudioEffect effect = effects.remove(session);
        if (effect != null) {
            effect.release();
        }
    }

    void releaseAll() {
        for (AudioEffect effect : effects.values()) {
            effect.release();
        }
        effects.clear();
    }

    void setReductionDb(float db) {
        reductionDb = db;
        for (AudioEffect effect : effects.values()) {
            applyGain(effect);
        }
    }

    /** Short description of what's attached to the global mix, for the UI. */
    String describeGlobal() {
        AudioEffect effect = effects.get(0);
        if (effect == null) {
            return "not attached";
        }
        String kind = effect instanceof DynamicsProcessing ? "DynamicsProcessing" : "Equalizer fallback";
        boolean control;
        try {
            control = effect.hasControl();
        } catch (IllegalStateException e) {
            control = false;
        }
        return kind + (control ? "" : " (another app has control!)");
    }

    private AudioEffect createDynamicsProcessing(int session) {
        try {
            DynamicsProcessing.Config config = new DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2,            // channels
                    false, 0,     // pre-EQ
                    false, 0,     // multi-band compressor
                    false, 0,     // post-EQ
                    false)        // limiter
                    .build();
            config.setInputGainAllChannelsTo(-reductionDb);
            DynamicsProcessing dp = new DynamicsProcessing(PRIORITY, session, config);
            dp.setInputGainAllChannelsTo(-reductionDb);
            dp.setEnabled(true);
            Log.i(TAG, "DynamicsProcessing attached to session " + session);
            return dp;
        } catch (RuntimeException e) {
            Log.w(TAG, "DynamicsProcessing failed on session " + session, e);
            return null;
        }
    }

    private AudioEffect createEqualizer(int session) {
        try {
            Equalizer eq = new Equalizer(PRIORITY, session);
            applyGain(eq);
            eq.setEnabled(true);
            Log.i(TAG, "Equalizer attached to session " + session);
            return eq;
        } catch (RuntimeException e) {
            Log.w(TAG, "Equalizer failed on session " + session, e);
            return null;
        }
    }

    private void applyGain(AudioEffect effect) {
        try {
            if (effect instanceof DynamicsProcessing) {
                ((DynamicsProcessing) effect).setInputGainAllChannelsTo(-reductionDb);
            } else if (effect instanceof Equalizer) {
                Equalizer eq = (Equalizer) effect;
                short min = eq.getBandLevelRange()[0];  // millibels
                short level = (short) Math.max(min, Math.round(-reductionDb * 100));
                for (short band = 0; band < eq.getNumberOfBands(); band++) {
                    eq.setBandLevel(band, level);
                }
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Failed to set gain", e);
        }
    }
}
