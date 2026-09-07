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

import androidx.compose.runtime.compositionLocalOf

/**
 * The Quick Settings design the surrounding panel is rendering.
 *
 * Tile drawing code sits several layers below the panel composables that know the user's choice, so
 * it reads the style from here instead of taking it as a parameter through every call site.
 */
val LocalQsPanelStyle = compositionLocalOf { QsPanelStyle.Penguin }

/** True when tiles should render the stock Android look rather than the PenguinOS redesign. */
val isStockQsStyle: Boolean
    @androidx.compose.runtime.Composable
    @androidx.compose.runtime.ReadOnlyComposable
    get() = LocalQsPanelStyle.current == QsPanelStyle.Default
