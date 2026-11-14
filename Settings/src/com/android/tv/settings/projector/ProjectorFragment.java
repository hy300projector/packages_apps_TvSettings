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

import android.os.Bundle;
import android.os.SystemProperties;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.android.tv.settings.R;
import com.android.tv.settings.SettingsPreferenceFragment;

/**
 * Projector settings: the controls the stock projector stack exposed, without
 * the stock apps.
 *
 * <p>Two decompiled vendor apps defined these settings:
 * {@code com.hysd.vafocus} (the focus/keystone service, {@code tpjz_allwinner})
 * and the MagcubicOS projector UI. Neither is installed -- they carried the
 * malware hooks -- but every control they offered is a system property plus,
 * for manual focus, one write to the motor node:
 *
 * <ul>
 *   <li>{@code persist.sys.vafocus}: autofocus.
 *   <li>{@code persist.sys.tpryxcrt} (and the MagcubicOS spelling
 *       {@code persist.sys.tpryauto}): automatic keystone.
 *   <li>{@code persist.sys.mbrecognize}: automatic screen fit.
 *   <li>{@code persist.sys.obstacle}: obstacle avoidance.
 *   <li>{@code persist.sys.tchkeyenable}: on-screen touch keys.
 *   <li>{@code persist.sys.zoom_value} / {@code persist.sys.zoom_scale}:
 *       digital zoom.
 *   <li>{@code persist.htc.keystone.{lt,rt,lb,rb}{x,y}}: the four-corner
 *       correction, also mirrored to {@code persist.sys.projector.keystone_*}
 *       for the in-tree keystone keeper.
 *   <li>Focus commands go through {@code persist.sys.projector.focus_cmd} and
 *       {@code persist.sys.projector.focus_seq}; the keeper writes the
 *       {@code 0xCCAA} command word to the motor node.
 * </ul>
 */
public class ProjectorFragment extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {

    private static final String PROP_AUTO_FOCUS = "persist.sys.vafocus";
    private static final String PROP_AUTO_KEYSTONE = "persist.sys.tpryxcrt";
    private static final String PROP_AUTO_KEYSTONE_LEGACY = "persist.sys.tpryauto";
    private static final String PROP_AUTO_FIT = "persist.sys.mbrecognize";
    private static final String PROP_OBSTACLE = "persist.sys.obstacle";
    private static final String PROP_TOUCH_KEYS = "persist.sys.tchkeyenable";
    private static final String PROP_KEYSTONE_VAF = "persist.sys.keystone_vaf";
    private static final String PROP_ZOOM_VALUE = "persist.sys.zoom_value";
    private static final String PROP_ZOOM_SCALE = "persist.sys.zoom_scale";
    private static final String PROP_ZOOM_SCALE_OLD = "persist.sys.zoom_scale_old";
    private static final String PROP_FOCUS_CMD = "persist.sys.projector.focus_cmd";
    private static final String PROP_FOCUS_SEQ = "persist.sys.projector.focus_seq";

    private static final String KEY_AUTO_FOCUS = "projector_auto_focus";
    private static final String KEY_FOCUS_NOW = "projector_focus_now";
    private static final String KEY_FOCUS_NEAR = "projector_focus_near";
    private static final String KEY_FOCUS_FAR = "projector_focus_far";
    private static final String KEY_AUTO_KEYSTONE = "projector_auto_keystone";
    private static final String KEY_AUTO_FIT = "projector_auto_fit";
    private static final String KEY_OBSTACLE = "projector_obstacle";
    private static final String KEY_TOUCH_KEYS = "projector_touch_keys";
    private static final String KEY_ZOOM = "projector_zoom";
    private static final String KEY_ZOOM_IN = "projector_zoom_in";
    private static final String KEY_ZOOM_OUT = "projector_zoom_out";
    private static final String KEY_KEYSTONE_TOP_LEFT = "projector_keystone_top_left";
    private static final String KEY_KEYSTONE_TOP_RIGHT = "projector_keystone_top_right";
    private static final String KEY_KEYSTONE_BOTTOM_LEFT = "projector_keystone_bottom_left";
    private static final String KEY_KEYSTONE_BOTTOM_RIGHT = "projector_keystone_bottom_right";
    private static final String KEY_KEYSTONE_RESET = "projector_keystone_reset";

    /** TvSettings' corner copies; the keeper reads these, then the stock keys. */
    static final String PREFIX = "persist.sys.projector.keystone_";
    /**
     * The stock key family, read-only here. Device policy cannot claim the
     * persist.htc.* property namespace (it is not persist.vendor./persist.odm.)
     * and core system policy does not define it, so a system app may not write
     * it; the keeper still reads it in case a vendor consumer populated it.
     */
    static final String HTC_PREFIX = "persist.htc.keystone.";

    /** Manual adjustment step, in the same 0.001-of-screen units as the props. */
    static final int STEP = 5;
    /** Manual focus step in motor micro-steps, matching the stock 0xCCAA argument. */
    private static final int FOCUS_STEP = 5;
    /** Digital zoom range, matching the stock UI's scale. */
    private static final int ZOOM_MAX = 50;

    private static final String[][] SWITCHES = {
            {KEY_AUTO_FOCUS, PROP_AUTO_FOCUS},
            {KEY_AUTO_KEYSTONE, PROP_AUTO_KEYSTONE},
            {KEY_AUTO_FIT, PROP_AUTO_FIT},
            {KEY_OBSTACLE, PROP_OBSTACLE},
            {KEY_TOUCH_KEYS, PROP_TOUCH_KEYS},
    };

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.projector, rootKey);

        final PreferenceScreen screen = getPreferenceScreen();
        for (String[] entry : SWITCHES) {
            final SwitchPreference preference = screen.findPreference(entry[0]);
            if (preference != null) {
                preference.setOnPreferenceChangeListener(this);
            }
        }
        for (String key : new String[] {KEY_FOCUS_NOW, KEY_FOCUS_NEAR, KEY_FOCUS_FAR,
                KEY_ZOOM_IN, KEY_ZOOM_OUT, KEY_KEYSTONE_RESET}) {
            final Preference preference = screen.findPreference(key);
            if (preference != null) {
                preference.setOnPreferenceClickListener(this);
            }
        }
        for (int i = 0; i < 4; i++) {
            final Preference corner = screen.findPreference(cornerKey(i));
            if (corner != null) {
                corner.setOnPreferenceClickListener(this);
            }
        }
        refresh();
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        final PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) {
            return;
        }
        for (String[] entry : SWITCHES) {
            final SwitchPreference preference = screen.findPreference(entry[0]);
            if (preference != null) {
                preference.setChecked(SystemProperties.getBoolean(entry[1], false));
            }
        }
        final Preference zoom = screen.findPreference(KEY_ZOOM);
        if (zoom != null) {
            zoom.setSummary(getString(R.string.projector_zoom_summary) + ": "
                    + readZoom());
        }
        for (int i = 0; i < 4; i++) {
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
        for (String[] entry : SWITCHES) {
            if (!entry[0].equals(key)) {
                continue;
            }
            SystemProperties.set(entry[1], on ? "1" : "0");
            if (KEY_AUTO_KEYSTONE.equals(key)) {
                // The MagcubicOS UI wrote its own spelling of the same flag.
                SystemProperties.set(PROP_AUTO_KEYSTONE_LEGACY, on ? "1" : "0");
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        final String key = preference.getKey();
        if (KEY_FOCUS_NOW.equals(key)) {
            sendFocusCommand(8, 1);
            return true;
        }
        if (KEY_FOCUS_NEAR.equals(key)) {
            sendFocusCommand(1, FOCUS_STEP);
            return true;
        }
        if (KEY_FOCUS_FAR.equals(key)) {
            sendFocusCommand(2, FOCUS_STEP);
            return true;
        }
        if (KEY_ZOOM_IN.equals(key) || KEY_ZOOM_OUT.equals(key)) {
            final int next = Math.max(0, Math.min(ZOOM_MAX,
                    readZoom() + (KEY_ZOOM_IN.equals(key) ? 1 : -1)));
            SystemProperties.set(PROP_ZOOM_SCALE_OLD, Integer.toString(readZoom()));
            SystemProperties.set(PROP_ZOOM_VALUE, Integer.toString(next));
            SystemProperties.set(PROP_ZOOM_SCALE, Integer.toString(next));
            refresh();
            return true;
        }
        if (KEY_KEYSTONE_RESET.equals(key)) {
            for (int i = 0; i < 4; i++) {
                writeCorner(i, "x", 0);
                writeCorner(i, "y", 0);
            }
            SystemProperties.set(PROP_ZOOM_SCALE_OLD, Integer.toString(readZoom()));
            SystemProperties.set(PROP_ZOOM_VALUE, "0");
            SystemProperties.set(PROP_ZOOM_SCALE, "0");
            // Tells the vendor focus service the shape came from a reset.
            SystemProperties.set(PROP_KEYSTONE_VAF, "1");
            refresh();
            return true;
        }
        for (int i = 0; i < 4; i++) {
            if (cornerKey(i).equals(key)) {
                ProjectorCornerDialog.show(this, i);
                return true;
            }
        }
        return false;
    }

    /**
     * Queues a motor command for the keystone keeper. The command word is the
     * stock 0xCCAA encoding: command in the high byte, argument in the low byte
     * (1 up, 2 down, 7 goto step, 8 autofocus, 9 abort).
     */
    private void sendFocusCommand(int command, int argument) {
        final int word = ((command & 0xff) << 8) | (argument & 0xff);
        SystemProperties.set(PROP_FOCUS_CMD, String.format("0x%04X", word));
        final int seq = SystemProperties.getInt(PROP_FOCUS_SEQ, 0) + 1;
        SystemProperties.set(PROP_FOCUS_SEQ, Integer.toString(seq));
    }

    /** Applies one adjustment step to a corner; the keeper applies the rest. */
    static void adjustCorner(int corner, int dx, int dy) {
        writeCorner(corner, "x", readCornerValue(corner, "x") + dx * STEP);
        writeCorner(corner, "y", readCornerValue(corner, "y") + dy * STEP);
    }

    static int readCornerValue(int corner, String axis) {
        final int own = SystemProperties.getInt(PREFIX + cornerSuffix(corner) + axis, 0);
        if (own != 0) {
            return own;
        }
        return SystemProperties.getInt(HTC_PREFIX + cornerSuffix(corner) + axis, 0);
    }

    private static void writeCorner(int corner, String axis, int value) {
        SystemProperties.set(PREFIX + cornerSuffix(corner) + axis, Integer.toString(value));
    }

    static String cornerSuffix(int corner) {
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

    private static int readZoom() {
        return SystemProperties.getInt(PROP_ZOOM_VALUE,
                SystemProperties.getInt(PROP_ZOOM_SCALE, 0));
    }
}
