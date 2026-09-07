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

package com.android.systemui.qs.composefragment

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.common.ui.compose.Icon
import com.android.systemui.plugins.qs.QSTile
import com.android.systemui.qs.panels.ui.compose.TileListener
import com.android.systemui.qs.panels.ui.viewmodel.IconProvider
import com.android.systemui.qs.panels.ui.viewmodel.TileUiState
import com.android.systemui.qs.panels.ui.viewmodel.TileViewModel
import com.android.systemui.qs.panels.ui.viewmodel.toIconProvider
import com.android.systemui.qs.panels.ui.viewmodel.toUiState
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.res.R

/**
 * An iOS-Control-Centre-style connectivity cluster for Quick Settings.
 *
 * Collapsed it shows three prominent toggles plus a 2x2 cluster of secondary ones. Tapping a
 * prominent toggle acts exactly like tapping its tile; tapping the small cluster expands the card
 * in place to a labelled list of every control it holds. Long press anywhere goes to that tile's
 * settings page, matching normal tile behaviour.
 *
 * Everything is driven by the real [TileViewModel]s already in the user's QS, so state and clicks
 * stay in sync with the grid rather than duplicating any tile logic.
 */
object ConnectivityFolderSpecs {
    /** Rendered large, in reading order. */
    val Large = listOf("airplane", "cast", "wifi")
    /** Rendered small in the 2x2 cluster. */
    val Small = listOf("cell", "bt", "hotspot", "dnd")
    /**
     * Of the folder's tiles, the ones that get a big two-column card when expanded. Anything else
     * the user puts in the folder falls back to a full width row, which is what Control Centre
     * does for its single-line controls.
     */
    val ExpandedCards = listOf("wifi", "bt", "cell", "cast")
}

/** Secure settings holding the user's choice of folder tiles, as comma separated specs. */
const val SETTING_QS_FOLDER_LARGE = "qs_connectivity_folder_large"
const val SETTING_QS_FOLDER_SMALL = "qs_connectivity_folder_small"

/**
 * The specs the user wants in the folder, defaulting to [ConnectivityFolderSpecs]. Observed so
 * edits apply without restarting SystemUI.
 */
@Composable
private fun folderSpecs(): Pair<List<String>, List<String>> {
    val resolver = LocalContext.current.contentResolver
    fun read(key: String, fallback: List<String>): List<String> {
        val raw = Settings.Secure.getString(resolver, key) ?: return fallback
        val parsed = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return parsed.ifEmpty { fallback }
    }
    var specs by remember {
        mutableStateOf(
            read(SETTING_QS_FOLDER_LARGE, ConnectivityFolderSpecs.Large) to
                read(SETTING_QS_FOLDER_SMALL, ConnectivityFolderSpecs.Small)
        )
    }
    DisposableEffect(resolver) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    specs =
                        read(SETTING_QS_FOLDER_LARGE, ConnectivityFolderSpecs.Large) to
                            read(SETTING_QS_FOLDER_SMALL, ConnectivityFolderSpecs.Small)
                }
            }
        listOf(SETTING_QS_FOLDER_LARGE, SETTING_QS_FOLDER_SMALL).forEach {
            resolver.registerContentObserver(Settings.Secure.getUriFor(it), false, observer)
        }
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return specs
}

private val LargeCircle = 56.dp
private val HeroCircle = 64.dp
private val SmallCircle = 30.dp
private val CellSize = 72.dp
private val BigCardHeight = 148.dp
private val CardPadding = 10.dp
private val CellSpacing = 6.dp

@Composable
fun ConnectivityFolder(
    tiles: List<TileViewModel>,
    modifier: Modifier = Modifier,
    /** Collapsed height to fit, so the card lines up with the sliders beside it. */
    compactHeight: Dp? = null,
    /**
     * Expansion is hoisted: the collapsed card lives in the header's half-width slot, but the
     * expanded list has to be rendered by the caller at full panel width or its rows come out
     * squeezed into that half.
     */
    expanded: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = {},
) {
    // Key on the raw spec string: TileSpec.toString() renders as "P(wifi)" / "C(pkg/cls)", so
    // matching against it would never hit.
    val bySpec = remember(tiles) { tiles.associateBy { it.spec.spec } }
    val (largeSpecs, smallSpecs) = folderSpecs()
    val large = remember(bySpec, largeSpecs) { largeSpecs.mapNotNull { bySpec[it] } }
    val small = remember(bySpec, smallSpecs) { smallSpecs.mapNotNull { bySpec[it] } }

    // Nothing to show (e.g. the user removed all of these tiles) - render nothing rather than an
    // empty card.
    if (large.isEmpty() && small.isEmpty()) return

    // Without this the tiles never start listening, so QSTile only pushes state on its own slow
    // cadence: taps worked but the icon wouldn't light up until much later. Gate it on actual
    // visibility exactly like TileGrid: the shade scene is always composed, so a hardcoded `true`
    // kept all six tiles polling with the shade closed, which showed up as QS jank on 4GB devices.
    var listening by remember { mutableStateOf(false) }
    LifecycleStartEffect(Unit) {
        listening = true
        onStopOrDispose { listening = false }
    }
    TileListener(large + small) { listening }

    // Match the grid's own gaps so the folder's internal spacing reads as part of the panel
    // rather than a different rhythm sitting next to it.
    val gap = dimensionResource(id = R.dimen.qs_tile_margin_vertical)

    if (expanded) {
        ExpandedSheet(large = large, small = small, gap = gap, onDone = { onExpandedChange(false) })
        return
    }

    // Derive the cell size from the height we're asked to fit, so the 2x2 matches the sliders'
    // height exactly rather than overflowing the header row.
    val cell = compactHeight?.let { (it - CardPadding * 2 - CellSpacing) / 2 } ?: CellSize

    Column(
        modifier =
            modifier
                // Always fill the slot: hugging the content left a gap to the sliders far wider
                // than the gap between tiles, which read as misaligned.
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .padding(CardPadding),
        verticalArrangement = spacedBy(CellSpacing),
    ) {
        // A tight 2x2 of equal cells that hugs its content, the way Control Centre's
        // connectivity module does. Spreading these across the full panel width left dead gaps.
        Row(horizontalArrangement = spacedBy(CellSpacing), modifier = Modifier.fillMaxWidth()) {
            Cell(cell) { large.getOrNull(0)?.let { FolderCircle(it, cell * 0.88f) } }
            Cell(cell) { large.getOrNull(1)?.let { FolderCircle(it, cell * 0.88f) } }
        }
        Row(horizontalArrangement = spacedBy(CellSpacing), modifier = Modifier.fillMaxWidth()) {
            Cell(cell) { large.getOrNull(2)?.let { FolderCircle(it, cell * 0.96f) } }
            Cell(cell) {
                if (small.isNotEmpty()) {
                    SmallCluster(small, cell * 0.38f, onClick = { onExpandedChange(true) })
                }
            }
        }
    }
}

/**
 * The expanded folder: a Control-Centre style sheet where each control is its own card rather than
 * a row inside one container. Connection toggles get big two column cards that can show what they
 * are connected to; everything else gets a full width row.
 */
@Composable
private fun ExpandedSheet(
    large: List<TileViewModel>,
    small: List<TileViewModel>,
    gap: Dp,
    onDone: () -> Unit,
) {
    val all = large + small
    val cards = all.filter { it.spec.spec in ConnectivityFolderSpecs.ExpandedCards }
    val rows = all.filterNot { it in cards }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = spacedBy(gap)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.quick_settings_connectivity_folder_title),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.quick_settings_done),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onDone),
            )
        }
        // Airplane mode and the like read as a switch, so they stay single line above the cards,
        // matching how Control Centre orders them.
        rows.take(1).forEach { FolderRow(it) }
        cards.chunked(2).forEach { pair ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = spacedBy(gap)) {
                pair.forEach { tile -> Box(Modifier.weight(1f)) { FolderBigCard(tile) } }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
        rows.drop(1).forEach { FolderRow(it) }
    }
}

/** A big two column card: icon badge on top, name and current value beneath. */
@Composable
private fun FolderBigCard(tile: TileViewModel) {
    val (uiState, icon) = rememberTileState(tile)
    val active = uiState.visualState == Tile.STATE_ACTIVE
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .height(BigCardHeight)
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .combinedClickable(
                    onClick = { tile.primaryAction(uiState) },
                    onLongClick = { tile.settingsClick(null) },
                )
                .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(folderBackground(active)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon = icon, tint = folderForeground(active), modifier = Modifier.size(22.dp))
        }
        Column {
            Text(
                text = uiState.label,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (uiState.secondaryLabel.isNotBlank()) {
                Text(
                    text = uiState.secondaryLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** One of the four equal square cells the folder is built from. */
@Composable
private fun RowScope.Cell(height: Dp, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.weight(1f).height(height),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** A single circular toggle that mirrors its tile's state and click behaviour. */
@Composable
private fun FolderCircle(tile: TileViewModel, diameter: Dp) {
    val (uiState, icon) = rememberTileState(tile)
    val active = uiState.visualState == Tile.STATE_ACTIVE
    Box(
        modifier =
            Modifier.size(diameter)
                .clip(CircleShape)
                .background(folderBackground(active))
                .combinedClickable(
                    onClick = { tile.primaryAction(uiState) },
                    onLongClick = { tile.settingsClick(null) },
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon = icon, tint = folderForeground(active), modifier = Modifier.size(diameter / 2))
    }
}

/** The 2x2 cluster of secondary toggles. Tapping anywhere on it expands the folder. */
@Composable
private fun SmallCluster(tiles: List<TileViewModel>, dot: Dp, onClick: () -> Unit) {
    Box(
        modifier =
            Modifier.clip(RoundedCornerShape(20.dp))
                .combinedClickable(onClick = onClick, onLongClick = onClick)
                .padding(4.dp)
    ) {
        Column(
            verticalArrangement = spacedBy(5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = spacedBy(5.dp)) {
                tiles.getOrNull(0)?.let { SmallDot(it, dot) }
                tiles.getOrNull(1)?.let { SmallDot(it, dot) }
            }
            Row(horizontalArrangement = spacedBy(5.dp)) {
                tiles.getOrNull(2)?.let { SmallDot(it, dot) }
                tiles.getOrNull(3)?.let { SmallDot(it, dot) }
            }
        }
    }
}

/**
 * A dot in the 2x2 cluster. Deliberately not clickable itself: the whole cluster is one tap target
 * that expands the folder, which is what the small icons do in Control Centre.
 */
@Composable
private fun SmallDot(tile: TileViewModel, diameter: Dp) {
    val (uiState, icon) = rememberTileState(tile)
    val active = uiState.visualState == Tile.STATE_ACTIVE
    Box(
        modifier = Modifier.size(diameter).clip(CircleShape).background(folderBackground(active)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon = icon, tint = folderForeground(active), modifier = Modifier.size(diameter / 2))
    }
}

/** A labelled row in the expanded folder, mirroring the collapsed toggles. */
@Composable
private fun FolderRow(tile: TileViewModel) {
    val (uiState, icon) = rememberTileState(tile)
    val active = uiState.visualState == Tile.STATE_ACTIVE
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .combinedClickable(
                    onClick = { tile.primaryAction(uiState) },
                    onLongClick = { tile.settingsClick(null) },
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier.size(44.dp).clip(CircleShape).background(folderBackground(active)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon = icon, tint = folderForeground(active), modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = uiState.label,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (uiState.secondaryLabel.isNotBlank()) {
                Text(
                    text = uiState.secondaryLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun folderBackground(active: Boolean): Color =
    if (active) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)

@Composable
private fun folderForeground(active: Boolean): Color =
    if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

/** Keeps a tile's UI state and icon in sync, the same way [Tile] does for the grid. */
@Composable
private fun rememberTileState(tile: TileViewModel): Pair<TileUiState, Icon> {
    val context = LocalContext.current
    val resources = context.resources
    // One collector, not two. Collecting tile.state twice per tile meant 12 flows for six tiles,
    // each recomposing independently for the same upstream emission.
    val state by
        produceState(
            tile.currentState.let { it.toUiState(resources) to it.toIconProvider() },
            tile,
            resources,
        ) {
            tile.state.collect { value = it.toUiState(resources) to it.toIconProvider() }
        }
    return state.first to context.folderIcon(state.second)
}

/**
 * Mirrors Tile.kt's private getTileIcon: resolve a tile's icon to a compose [Icon], falling back to
 * the error glyph so a misbehaving tile cannot crash the folder.
 */
private fun Context.folderIcon(icon: IconProvider): Icon {
    return icon.icon?.let {
        if (it is QSTileImpl.ResourceIcon) {
            Icon.Resource(it.resId, null)
        } else {
            Icon.Loaded(it.getDrawable(this), null)
        }
    } ?: Icon.Resource(R.drawable.ic_error_outline, null)
}

/** Secure setting gating the folder. 0 (default) keeps the stock grid untouched. */
const val SETTING_QS_CONNECTIVITY_FOLDER = "qs_connectivity_folder"

/**
 * Whether the user has switched the folder on. Observed rather than read once so toggling it takes
 * effect without restarting SystemUI. Independent of qs_panel_style, so it applies to either QS
 * style.
 */
@Composable
fun connectivityFolderEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    var enabled by remember {
        mutableStateOf(
            Settings.Secure.getInt(resolver, SETTING_QS_CONNECTIVITY_FOLDER, 0) != 0
        )
    }
    DisposableEffect(resolver) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    enabled = Settings.Secure.getInt(resolver, SETTING_QS_CONNECTIVITY_FOLDER, 0) != 0
                }
            }
        resolver.registerContentObserver(
            Settings.Secure.getUriFor(SETTING_QS_CONNECTIVITY_FOLDER),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return enabled
}

/**
 * What a tap should do. Large QS tiles are dual-target: the icon toggles and the label opens the
 * tile's detail view. mainClick() is the label action, so using it here opened the Bluetooth
 * dialog instead of switching Bluetooth on. Prefer the toggle when the tile offers one.
 */
private fun TileViewModel.primaryAction(uiState: TileUiState) {
    if (uiState.handlesToggleClick) toggleClick() else mainClick(null)
}
