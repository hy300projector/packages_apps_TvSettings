/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tv.settings.projector;

import android.content.Intent;
import android.os.Bundle;
import android.os.SystemProperties;
import android.util.Log;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.android.tv.settings.R;
import com.android.tv.settings.SettingsPreferenceFragment;

/**
 * Projector settings: autofocus, auto keystone and manual four-corner keystone.
 *
 * <p>The controls are the ones the stock projector UI (com.htc.magcubicos
 * ProjectActivity) exposed. That app is not shipped -- it carried the malware
 * hooks -- but its settings are plain system properties and broadcasts, so the
 * same controls can live in TvSettings without it:
 *
 * <ul>
 *   <li>{@code persist.sys.vafocus}: autofocus on/off.
 *   <li>{@code intent.htc.vafocus}: "run autofocus now".
 *   <li>{@code persist.sys.tpryauto}: automatic keystone on/off;
 *       {@code android.intent.hotack_keystone} asks the keystone service to
 *       re-measure.
 *   <li>{@code persist.display.keystone_{lt,l b,rt,rb}{x,y}}: the manual
 *       four-corner offsets, in the same 0.001-of-screen units the stock app
 *       stored; {@code persist.sys.zoom.value} mirrors all eight in the order
 *       bottom-left, top-left, top-right, bottom-right.
 * </ul>
 *
 * <p>Whether a key or broadcast has an effect depends on the vendor side: the
 * keystone engine is the display-config/ksc path, and autofocus needs the
 * vendor focus service. Both are absent from a build that ships no vendor
 * projector app, in which case these settings are stored but inert; the page
 * still round-trips the values so a device with the vendor path behaves the
 * same as it did with the stock app.
 */
public class ProjectorFragment extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {

    private static final String TAG = "ProjectorFragment";

    private static final String PROP_AUTO_FOCUS = "persist.sys.vafocus";
    private static final String PROP_AUTO_KEYSTONE = "persist.sys.tpryauto";
    private static final String PROP_ZOOM_VALUE = "persist.sys.zoom.value";

    private static final String ACTION_FOCUS_NOW = "intent.htc.vafocus";
    private static final String ACTION_KEYSTONE_MEASURE = "android.intent.hotack_keystone";

    private static final String KEY_AUTO_FOCUS = "projector_auto_focus";
    private static final String KEY_FOCUS_NOW = "projector_focus_now";
    private static final String KEY_AUTO_KEYSTONE = "projector_auto_keystone";
    private static final String KEY_KEYSTONE_TOP_LEFT = "projector_keystone_top_left";
    private static final String KEY_KEYSTONE_TOP_RIGHT = "projector_keystone_top_right";
    private static final String KEY_KEYSTONE_BOTTOM_LEFT = "projector_keystone_bottom_left";
    private static final String KEY_KEYSTONE_BOTTOM_RIGHT = "projector_keystone_bottom_right";
    private static final String KEY_KEYSTONE_RESET = "projector_keystone_reset";

    /** Corner property prefixes, index 0..3, matching ProjectorCornerDialog. */
    static final String[] CORNER_PREFIXES = {
            "persist.display.keystone_lt",
            "persist.display.keystone_rt",
            "persist.display.keystone_lb",
            "persist.display.keystone_rb",
    };

    /**
     * Prefix actually used for storage. Under stock AOSP policy the system app
     * may set persist.sys.* (system_prop) but not persist.display.*, so the
     * values live here and are mirrored to the stock keys on a best-effort
     * basis (the stock keys are what a vendor keystone consumer reads).
     */
    static final String SYS_PREFIX = "persist.sys.projector.keystone_";

    /** Manual adjustment step, in the same 0.001-of-screen units as the props. */
    static final int STEP = 5;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.projector, rootKey);

        final PreferenceScreen screen = getPreferenceScreen();
        bindSwitch(screen, KEY_AUTO_FOCUS);
        bindSwitch(screen, KEY_AUTO_KEYSTONE);

        final Preference focusNow = screen.findPreference(KEY_FOCUS_NOW);
        if (focusNow != null) {
            focusNow.setOnPreferenceClickListener(this);
        }
        final Preference reset = screen.findPreference(KEY_KEYSTONE_RESET);
        if (reset != null) {
            reset.setOnPreferenceClickListener(this);
        }
        for (int i = 0; i < CORNER_PREFIXES.length; i++) {
            final Preference corner = screen.findPreference(cornerKey(i));
            if (corner != null) {
                corner.setSummary(cornerSummary(i));
                corner.setOnPreferenceClickListener(this);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        final PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) {
            return;
        }
        final SwitchPreference autoFocus = screen.findPreference(KEY_AUTO_FOCUS);
        if (autoFocus != null) {
            autoFocus.setChecked(isTrue(PROP_AUTO_FOCUS));
        }
        final SwitchPreference autoKeystone = screen.findPreference(KEY_AUTO_KEYSTONE);
        if (autoKeystone != null) {
            autoKeystone.setChecked(isTrue(PROP_AUTO_KEYSTONE));
        }
        for (int i = 0; i < CORNER_PREFIXES.length; i++) {
            final Preference corner = screen.findPreference(cornerKey(i));
            if (corner != null) {
                corner.setSummary(cornerSummary(i));
            }
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final String key = preference.getKey();
        final boolean on = Boolean.TRUE.equals(newValue);
        if (KEY_AUTO_FOCUS.equals(key)) {
            SystemProperties.set(PROP_AUTO_FOCUS, on ? "1" : "0");
            return true;
        }
        if (KEY_AUTO_KEYSTONE.equals(key)) {
            SystemProperties.set(PROP_AUTO_KEYSTONE, on ? "1" : "0");
            if (!on) {
                // Turning auto off leaves the last measured shape in place.
                return true;
            }
            sendKeystoneMeasure();
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        final String key = preference.getKey();
        if (KEY_FOCUS_NOW.equals(key)) {
            final Intent intent = new Intent(ACTION_FOCUS_NOW);
            intent.setPackage("com.hysd.vafocus");
            getContext().sendBroadcast(intent);
            // Also nudge the property the vendor service reads, in case the
            // service watches it instead of the broadcast.
            SystemProperties.set(PROP_AUTO_FOCUS, "1");
            return true;
        }
        if (KEY_KEYSTONE_RESET.equals(key)) {
            for (int i = 0; i < CORNER_PREFIXES.length; i++) {
                writeCorner(i, "x", 0);
                writeCorner(i, "y", 0);
            }
            SystemProperties.set(PROP_ZOOM_VALUE, "0,0,0,0,0,0,0,0");
            refreshCorners();
            return true;
        }
        for (int i = 0; i < CORNER_PREFIXES.length; i++) {
            if (cornerKey(i).equals(key)) {
                ProjectorCornerDialog.show(this, i);
                return true;
            }
        }
        return false;
    }

    /** Applies one adjustment step to a corner and persists the whole shape. */
    static void adjustCorner(int corner, int dx, int dy) {
        final int x = readCornerValue(corner, "x") + dx * STEP;
        final int y = readCornerValue(corner, "y") + dy * STEP;
        writeCorner(corner, "x", x);
        writeCorner(corner, "y", y);
        // persist.sys.zoom.value carries all eight: bl, tl, tr, br.
        SystemProperties.set(PROP_ZOOM_VALUE,
                readCorner(2) + "," + readCorner(0) + ","
                        + readCorner(1) + "," + readCorner(3));
    }

    private static String readCorner(int corner) {
        return readCornerValue(corner, "x") + "," + readCornerValue(corner, "y");
    }

    /** Reads the system-app-writable copy, falling back to the stock key. */
    private static int readCornerValue(int corner, String axis) {
        final int own = SystemProperties.getInt(SYS_PREFIX + cornerSuffix(corner) + axis, 0);
        if (own != 0) {
            return own;
        }
        return SystemProperties.getInt(CORNER_PREFIXES[corner] + axis, 0);
    }

    /** Writes both keys: the writable one first, then the stock one best-effort. */
    private static void writeCorner(int corner, String axis, int value) {
        final String text = Integer.toString(value);
        SystemProperties.set(SYS_PREFIX + cornerSuffix(corner) + axis, text);
        SystemProperties.set(CORNER_PREFIXES[corner] + axis, text);
    }

    private static String cornerSuffix(int corner) {
        switch (corner) {
            case 0:
                return "lt";
            case 1:
                return "rt";
            case 2:
                return "lb";
            default:
                return "rb";
        }
    }

    private void refreshCorners() {
        final PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) {
            return;
        }
        for (int i = 0; i < CORNER_PREFIXES.length; i++) {
            final Preference corner = screen.findPreference(cornerKey(i));
            if (corner != null) {
                corner.setSummary(cornerSummary(i));
            }
        }
    }

    private static String cornerKey(int corner) {
        switch (corner) {
            case 0:
                return KEY_KEYSTONE_TOP_LEFT;
            case 1:
                return KEY_KEYSTONE_TOP_RIGHT;
            case 2:
                return KEY_KEYSTONE_BOTTOM_LEFT;
            default:
                return KEY_KEYSTONE_BOTTOM_RIGHT;
        }
    }

    private static String cornerSummary(int corner) {
        return "x " + readCornerValue(corner, "x")
                + "   y " + readCornerValue(corner, "y");
    }

    private void bindSwitch(PreferenceScreen screen, String key) {
        final SwitchPreference preference = screen.findPreference(key);
        if (preference != null) {
            preference.setOnPreferenceChangeListener(this);
        }
    }

    private static boolean isTrue(String property) {
        return SystemProperties.getBoolean(property, false);
    }

    private void sendKeystoneMeasure() {
        final Intent intent = new Intent(ACTION_KEYSTONE_MEASURE);
        intent.putExtra("ratio", 1);
        getContext().sendBroadcast(intent);
    }
}
