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

package com.android.systemui.qs.panels.ui.compose

import android.content.ContentResolver
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.qs.composefragment.POSITION_ABOVE_GRID
import com.android.systemui.qs.composefragment.POSITION_BELOW_GRID
import com.android.systemui.qs.composefragment.POSITION_HEADER
import com.android.systemui.qs.composefragment.SETTING_QS_FOLDER_POSITION
import com.android.systemui.qs.composefragment.SETTING_QS_FOLDER_SPAN
import com.android.systemui.qs.composefragment.SETTING_QS_MEDIA_SPAN
import com.android.systemui.qs.composefragment.SETTING_QS_SLIDERS_POSITION
import com.android.systemui.qs.composefragment.SETTING_QS_SLIDERS_SPAN
import com.android.systemui.qs.composefragment.SETTING_QS_MEDIA_POSITION
import com.android.systemui.qs.panels.ui.viewmodel.AvailableEditActions
import com.android.systemui.qs.composefragment.connectivityFolderEnabled
import com.android.systemui.qs.composefragment.connectivityFolderSpecs
import com.android.systemui.qs.composefragment.secureIntSetting
import com.android.systemui.qs.panels.ui.viewmodel.EditModeViewModel
import com.android.systemui.qs.panels.ui.viewmodel.EditTileViewModel
import androidx.compose.ui.unit.Dp
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.shared.model.TileCategory
import com.android.systemui.qs.shared.style.QsPanelStyle
import com.android.systemui.res.R

/**
 * Specs for the panel elements that are not real tiles but can still be arranged in edit mode. They
 * never reach the tile pipeline: [stripPanelElements] pulls them back out and stores where they
 * were dropped.
 */
val FOLDER_SPEC = TileSpec.create("penguin_connectivity_folder")
val MEDIA_SPEC = TileSpec.create("penguin_media_player")
val SLIDERS_SPEC = TileSpec.create("penguin_sliders")

val PANEL_ELEMENT_SPECS = setOf(FOLDER_SPEC, MEDIA_SPEC, SLIDERS_SPEC)

const val PANEL_FILLER_COLUMNS = 2

private const val PANEL_FILLER_ROWS = 2

fun panelFillerTiles(pool: List<TileSpec>, largeTiles: Set<TileSpec>): List<TileSpec> {
    val taken = mutableListOf<TileSpec>()
    var row = 0
    var used = 0
    for (spec in pool) {
        val width = if (spec in largeTiles) PANEL_FILLER_COLUMNS else 1
        if (used + width > PANEL_FILLER_COLUMNS) {
            row++
            used = 0
        }
        if (row >= PANEL_FILLER_ROWS) break
        taken.add(spec)
        used += width
        if (used >= PANEL_FILLER_COLUMNS) {
            row++
            used = 0
        }
    }
    return taken
}

/** Secure setting holding this element's width, or null if it is a real tile. */
fun TileSpec.panelSpanSetting(): String? =
    when (this) {
        FOLDER_SPEC -> SETTING_QS_FOLDER_SPAN
        MEDIA_SPEC -> SETTING_QS_MEDIA_SPAN
        SLIDERS_SPEC -> SETTING_QS_SLIDERS_SPAN
        else -> null
    }

/**
 * Live renderings for the panel elements, so edit mode shows the real folder or media card being
 * arranged instead of a placeholder pill. Supplied by whoever composes edit mode, since that is
 * where the tile and media view models are in scope.
 */
val LocalPanelElementPreview =
    compositionLocalOf<Map<TileSpec, @Composable () -> Unit>> { emptyMap() }

val LocalPanelElementHeight = compositionLocalOf<Map<TileSpec, Dp>> { emptyMap() }

/**
 * What the panel puts above the tiles, for styles that fix it in place. Null for styles where the
 * user arranges those elements themselves and they appear in the grid instead.
 */
val LocalQsHeaderPreview = compositionLocalOf<(@Composable () -> Unit)?> { null }

@Composable
fun EditMode(
    viewModel: EditModeViewModel,
    modifier: Modifier = Modifier,
    previews: Map<TileSpec, @Composable () -> Unit> = emptyMap(),
    previewHeights: Map<TileSpec, Dp> = emptyMap(),
    headerPreview: (@Composable () -> Unit)? = null,
) {
    CompositionLocalProvider(
        LocalPanelElementPreview provides previews,
        LocalPanelElementHeight provides previewHeights,
        LocalQsHeaderPreview provides headerPreview,
    ) {
        EditModeContent(viewModel, modifier)
    }
}

@Composable
private fun EditModeContent(viewModel: EditModeViewModel, modifier: Modifier = Modifier) {
    val gridLayout by viewModel.gridLayout.collectAsStateWithLifecycle()
    val tiles by viewModel.tiles.collectAsStateWithLifecycle(emptyList())
    val resolver = LocalContext.current.contentResolver

    BackHandler { viewModel.stopEditing() }

    DisposableEffect(Unit) { onDispose { viewModel.stopEditing() } }

    val panelStyle =
        QsPanelStyle.fromValue(
            secureIntSetting(QsPanelStyle.SETTING_NAME, QsPanelStyle.Penguin.value)
        )
    // Only the Penguin panel places its elements in the grid. MyUI fixes the card, the sliders
    // and the media player in place, and the default panel has none of them, so editing there is
    // about tiles only.
    val panelElementsEditable = panelStyle == QsPanelStyle.Penguin

    // The default panel shows the connectivity tiles as ordinary tiles, not in a folder.
    val folderMemberSpecs =
        if (panelStyle != QsPanelStyle.Default && connectivityFolderEnabled()) {
            connectivityFolderSpecs()
        } else {
            emptyList()
        }
    val gridTiles =
        remember(tiles, folderMemberSpecs) {
            tiles.filterNot { it.isCurrent && it.tileSpec.spec in folderMemberSpecs }
        }
    Column(modifier) {
        gridLayout.EditTileGrid(
            if (panelElementsEditable) {
                gridTiles.withPanelElements(resolver, connectivityFolderEnabled())
            } else {
                gridTiles
            },
            Modifier,
            viewModel::addTile,
            { spec -> if (spec.isPanelElement()) resolver.park(spec) else viewModel.removeTile(spec) },
            { specs ->
                val merged = specs + resolver.folderTilesToKeep(folderMemberSpecs, specs)
                viewModel.setTiles(
                    if (panelElementsEditable) merged.stripPanelElements(resolver) else merged
                )
            },
            viewModel::stopEditing,
        )
    }
}

/**
 * The folder's own tiles, which edit mode hides because the folder shows them. They are still part
 * of the tile list, so a commit has to write them back or they are lost. Read from the setting
 * rather than the edit list, which lags a commit by a recomposition.
 */
private fun ContentResolver.folderTilesToKeep(
    folderMemberSpecs: List<String>,
    written: List<TileSpec>,
): List<TileSpec> =
    (Settings.Secure.getString(this, Settings.Secure.QS_TILES) ?: "")
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() && it in folderMemberSpecs }
        .map { TileSpec.create(it) }
        .filterNot { it in written }

private fun TileSpec.isPanelElement() = this in PANEL_ELEMENT_SPECS

private fun TileSpec.positionSetting() =
    when (this) {
        FOLDER_SPEC -> SETTING_QS_FOLDER_POSITION
        SLIDERS_SPEC -> SETTING_QS_SLIDERS_POSITION
        else -> SETTING_QS_MEDIA_POSITION
    }

/**
 * Where the element sits in the edit grid. Kept separately from the panel position so that a drop
 * stays put: the panel itself only has a slot above and below the grid, but re-inserting the
 * element at one of those ends on every commit made the whole grid reflow under the finger.
 */
private fun TileSpec.editIndexSetting() =
    when (this) {
        FOLDER_SPEC -> "qs_connectivity_folder_edit_index"
        SLIDERS_SPEC -> "qs_sliders_edit_index"
        else -> "qs_media_edit_index"
    }

private fun ContentResolver.editIndex(spec: TileSpec) =
    Settings.Secure.getInt(
        this,
        spec.editIndexSetting(),
        when (spec) {
            MEDIA_SPEC -> 0
            FOLDER_SPEC -> 1
            else -> 2
        },
    )

/** Dragged out of the grid, so it goes back to sharing the header with the sliders. */
private fun ContentResolver.park(spec: TileSpec) {
    Settings.Secure.putInt(this, spec.positionSetting(), POSITION_HEADER)
}

/**
 * Adds the folder and the media player to the tiles being edited, at whichever end of the grid they
 * are currently pinned to.
 */
private fun List<EditTileViewModel>.withPanelElements(
    resolver: ContentResolver,
    folderEnabled: Boolean,
): List<EditTileViewModel> {
    val current = filter { it.isCurrent }.toMutableList()
    val rest = filterNot { it.isCurrent }
    PANEL_ELEMENT_SPECS.filter { folderEnabled || it != FOLDER_SPEC }
        .sortedBy { resolver.editIndex(it) }
        .forEach { spec ->
            val index = resolver.editIndex(spec).coerceIn(0, current.size)
            current.add(index, spec.toEditTileViewModel())
        }
    return current + rest
}

/**
 * Records where each panel element was dropped and removes it, so only real tile specs reach the
 * tile pipeline. The grid is paginated, so an element that spans the panel cannot sit at an
 * arbitrary index: anything dropped in the first half pins above the grid, the rest below it.
 */
private fun List<TileSpec>.stripPanelElements(resolver: ContentResolver): List<TileSpec> {
    val firstTile = indexOfFirst { !it.isPanelElement() }
    forEachIndexed { index, spec ->
        if (spec.isPanelElement()) {
            Settings.Secure.putInt(resolver, spec.editIndexSetting(), index)
            val position =
                if (firstTile == -1 || index < firstTile) POSITION_ABOVE_GRID
                else POSITION_BELOW_GRID
            Settings.Secure.putInt(resolver, spec.positionSetting(), position)
        }
    }
    return filterNot { it.isPanelElement() }
}

private fun TileSpec.toEditTileViewModel(): EditTileViewModel {
    val label =
        when (this) {
            FOLDER_SPEC -> "Connectivity folder"
            SLIDERS_SPEC -> "Brightness and volume"
            else -> "Media player"
        }
    val iconRes =
        when (this) {
            FOLDER_SPEC -> R.drawable.ic_apps_expressive
            SLIDERS_SPEC -> R.drawable.ic_brightness_full
            else -> R.drawable.ic_music_note
        }
    return EditTileViewModel(
        tileSpec = this,
        icon = Icon.Resource(iconRes, null),
        label = AnnotatedString(label),
        inlinedLabel = null,
        appName = null,
        appIcon = null,
        isCurrent = true,
        isDualTarget = false,
        availableEditActions = setOf(AvailableEditActions.MOVE, AvailableEditActions.REMOVE),
        category = TileCategory.CONNECTIVITY,
    )
}
