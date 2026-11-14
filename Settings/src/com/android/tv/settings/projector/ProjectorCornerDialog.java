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
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

/**
 * Remote-friendly adjustment dialog for one keystone corner.
 *
 * <p>Four buttons move the corner instead of a touch slider, because the
 * device is driven with a D-pad remote: X- / X+ / Y- / Y+. Every press is
 * applied immediately through {@link ProjectorFragment#adjustCorner}, so the
 * projector updates while the dialog stays open.
 */
public class ProjectorCornerDialog extends DialogFragment {

    private static final String ARG_CORNER = "corner";

    private TextView mValue;

    static void show(ProjectorFragment fragment, int corner) {
        final ProjectorCornerDialog dialog = new ProjectorCornerDialog();
        final Bundle args = new Bundle();
        args.putInt(ARG_CORNER, corner);
        dialog.setArguments(args);
        dialog.setTargetFragment(fragment, 0);
        dialog.show(fragment.getFragmentManager(), "projector_corner");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            Bundle savedInstanceState) {
        final int corner = getArguments() == null ? 0 : getArguments().getInt(ARG_CORNER, 0);
        getDialog().setTitle(cornerTitle(corner));

        final LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);

        mValue = new TextView(getContext());
        mValue.setGravity(Gravity.CENTER);
        mValue.setPadding(0, 24, 0, 24);
        layout.addView(mValue);

        layout.addView(button("X -", corner, -1, 0));
        layout.addView(button("X +", corner, 1, 0));
        layout.addView(button("Y -", corner, 0, -1));
        layout.addView(button("Y +", corner, 0, 1));
        return layout;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateValue();
    }

    private Button button(String label, int corner, int dx, int dy) {
        final Button button = new Button(getContext());
        button.setText(label);
        button.setOnClickListener(v -> {
            ProjectorFragment.adjustCorner(corner, dx, dy);
            updateValue();
        });
        return button;
    }

    private void updateValue() {
        final int corner = getArguments() == null ? 0 : getArguments().getInt(ARG_CORNER, 0);
        mValue.setText("x " + ProjectorFragment.readCornerValue(corner, "x")
                + "   y " + ProjectorFragment.readCornerValue(corner, "y"));
    }

    private static String cornerTitle(int corner) {
        switch (corner) {
            case 0:
                return "Top left";
            case 1:
                return "Top right";
            case 2:
                return "Bottom left";
            default:
                return "Bottom right";
        }
    }
}
