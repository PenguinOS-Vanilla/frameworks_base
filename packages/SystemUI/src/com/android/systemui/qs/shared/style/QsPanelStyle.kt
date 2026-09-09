/*
 * Copyright (C) 2026 The PenguinOS Project
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

package com.android.systemui.qs.shared.style

/** Which Quick Settings panel design to render. Backed by [SETTING_NAME]. */
enum class QsPanelStyle(val value: Int) {
    /** Stock Android 17 Quick Settings, with none of the PenguinOS redesign. */
    Default(0),

    /** PenguinOS redesign: vertical brightness/volume sliders, paired header, single-tone tiles. */
    Penguin(1),

    /** MyUI: a connectivity card beside two standing sliders, over a grid of labelled tiles. */
    MyUi(2);

    companion object {
        const val SETTING_NAME = "qs_panel_style"

        fun fromValue(value: Int): QsPanelStyle =
            entries.firstOrNull { it.value == value } ?: Penguin
    }
}
