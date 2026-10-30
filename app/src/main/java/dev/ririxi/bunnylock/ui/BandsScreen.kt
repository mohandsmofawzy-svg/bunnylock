@file:Suppress("FunctionName")

package dev.ririxi.bunnylock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.animateColorAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.ririxi.bunnylock.R
import dev.ririxi.bunnylock.modem.Backend
import dev.ririxi.bunnylock.modem.BandGroupState
import dev.ririxi.bunnylock.modem.NrMode

@Composable
fun BandsScreen(
    state: UiState,
    vm: MainViewModel,
    contentPadding: PaddingValues,
) {
    LazyColumn(contentPadding = contentPadding.withScreenMargins(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            BandPlanSummary(state, vm)
        }

        item { SectionHeader("Band groups") }
        items(state.modem.groups, key = { it.group.name }) { bandGroup ->
            BandGroup(
                bandGroup,
                selected = state.selectedBands(bandGroup),
                favorites = state.favoritesOf(bandGroup.group),
                busy = state.busy || state.dynamicBandEnabled,
                simSelectionRequired = state.modem.simSelectionRequired,
                onToggle = { vm.toggleBand(bandGroup.group, it) },
                onToggleFavorite = { vm.toggleFavorite(bandGroup.group, it) },
                onSelectAll = { vm.selectAll(bandGroup.group, it) },
                onLock = { vm.applyGroup(bandGroup.group) },
                onUnlock = { vm.applyGroup(bandGroup.group, unlock = true) },
            )
        }

        item {
            SectionHeader("Network mode")
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(state.modem.netMode?.label ?: "Automatic", style = MaterialTheme.typography.titleMediumEmphasized)
                        Text("Preferred radio technologies", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "ACTIVE",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                ToggleGrid(
                    options = state.modem.netModes,
                    isChecked = { it == state.modem.netMode },
                    label = { it.label },
                    onToggle = vm::setNetMode,
                    columns = if (state.modem.supports5g) 3 else 2,
                    enabled = !state.busy && !state.dynamicBandEnabled && !state.modem.simSelectionRequired,
                )
                state.modem.netModeHeldBack?.let { heldBack ->
                    Text(heldBack, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        when {
            state.modem.supportsNrMode -> {
                item {
                    SectionHeader("5G configuration")
                    ConnectedChoice(
                        options = NrMode.entries,
                        selected = state.modem.nrMode,
                        label = { it.label },
                        onSelect = vm::setNrMode,
                        enabled = !state.busy && !state.dynamicBandEnabled && !state.modem.simSelectionRequired,
                    )
                }
            }

            state.modem.supportsNrDc -> {
                item {
                    SectionHeader("5G configuration")
                    ConnectedChoice(
                        options = listOf(true, false),
                        selected = state.modem.nrDc,
                        label = { nsaOn -> if (nsaOn) "NSA on" else "NSA off" },
                        onSelect = vm::setNrDc,
                        enabled = !state.busy && !state.dynamicBandEnabled && !state.modem.simSelectionRequired,
                    )
                }
            }
        }

        item {
            Panel {
                SectionHeader("How changes apply", Modifier.padding(top = 0.dp))
                Text(backendHint(state), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.modem.device?.samsungMenu == true) {
                    FilledTonalButton(
                        onClick = vm::samsungMenu,
                        enabled = !state.dynamicBandEnabled && !state.modem.simSelectionRequired,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(BunnyIcons.Bands, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Open Samsung band menu")
                    }
                }
            }
        }
    }
}

@Composable
private fun BandPlanSummary(
    state: UiState,
    vm: MainViewModel,
) {
    val lockedGroups = state.modem.groups.count { it.locked }
    val selectedBands = state.modem.groups.sumOf { state.selectedBands(it).size }
    var intervalText by rememberSaveable { mutableStateOf(state.dynamicBandIntervalSeconds.toString()) }
    var customInterval by rememberSaveable { mutableStateOf(state.dynamicBandIntervalSeconds !in listOf(10, 15, 30, 45, 60)) }
    LaunchedEffect(state.dynamicBandIntervalSeconds) {
        intervalText = state.dynamicBandIntervalSeconds.toString()
        customInterval = state.dynamicBandIntervalSeconds !in listOf(10, 15, 30, 45, 60)
    }
    Panel(padding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Band plan", style = MaterialTheme.typography.headlineSmallEmphasized)
                Text(
                    if (lockedGroups == 0) "Choose a radio group, then apply the selection." else "$lockedGroups radio groups are currently locked.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LockBadge(locked = lockedGroups > 0, size = 52.dp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryMetric("$selectedBands", "selected bands", Modifier.weight(1f))
            SummaryMetric("${state.modem.groups.size}", "radio groups", Modifier.weight(1f))
            SummaryMetric(state.modem.netMode?.label ?: "Auto", "network mode", Modifier.weight(1f))
        }
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val iconTint by animateColorAsState(
                        targetValue =
                            if (state.dynamicBandEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "dynamic-band-toggle",
                    )
                    IconButton(
                        onClick = {
                            if (state.dynamicBandEnabled) {
                                vm.setDynamicBandSwitching(false)
                            } else {
                                vm.setDynamicBandSwitching(true, intervalText.toIntOrNull() ?: -1)
                            }
                        },
                        enabled = state.dynamicBandEnabled || !state.busy,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription =
                                translateUiText(
                                    if (state.dynamicBandEnabled) "Turn Dynamic Band Switching off" else "Turn Dynamic Band Switching on",
                                    LocalAppLanguage.current,
                                ),
                            colorFilter = ColorFilter.tint(iconTint),
                            modifier = Modifier.size(36.dp),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Dynamic Band Switching", style = MaterialTheme.typography.titleMediumEmphasized)
                        Text(
                            if (state.dynamicBandEnabled) "ON" else "OFF",
                            style = MaterialTheme.typography.labelLarge,
                            color = iconTint,
                        )
                    }
                    if (state.dynamicBandEnabled && state.dynamicBandSecondsRemaining > 0) {
                        Text("${state.dynamicBandSecondsRemaining}s", style = MaterialTheme.typography.titleMediumEmphasized)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(10, 15, 30, 45, 60).forEach { seconds ->
                        FilterChip(
                            selected = !customInterval && state.dynamicBandIntervalSeconds == seconds,
                            onClick = {
                                customInterval = false
                                intervalText = seconds.toString()
                                vm.setDynamicBandInterval(seconds)
                            },
                            label = { Text("${seconds}s") },
                        )
                    }
                    FilterChip(
                        selected = customInterval,
                        onClick = { customInterval = true },
                        label = { Text("Custom") },
                    )
                }
                OutlinedTextField(
                    value = intervalText,
                    onValueChange = {
                        intervalText = it.filter(Char::isDigit).take(4)
                        customInterval = true
                        intervalText.toIntOrNull()?.let(vm::setDynamicBandInterval)
                    },
                    label = { Text("Switch interval (seconds)") },
                    supportingText = { Text("10–3600 seconds; 30 seconds by default.") },
                    enabled = !state.busy,
                    singleLine = true,
                    textStyle = ltrInputTextStyle(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                DynamicBandFact("Current band", state.dynamicBandCurrent ?: "Not confirmed")
                DynamicBandFact("Next band", state.dynamicBandNext ?: "—")
                DynamicBandFact("Last switch", state.dynamicBandLastResult ?: "Not started")
                if (state.dynamicBandReadOnly) {
                    Text(
                        "One selected band uses read-only status refresh. No band-lock command or handover is sent.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.dynamicBandError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "Experimental: band locks can trigger network reselection and interrupt data. Pixel is not supported because its band-lock path restarts the radio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DynamicBandFact(
    label: String,
    value: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMediumEmphasized)
    }
}

@Composable
private fun SummaryMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = MaterialTheme.typography.titleSmallEmphasized, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

private fun backendHint(state: UiState): String =
    when (state.modem.backend) {
        Backend.QUALCOMM -> {
            val afterReboot =
                if (state.modem.persist) {
                    "Locks stay after a reboot (you can change that in Menu)."
                } else {
                    "Locks are cleared when you reboot."
                }
            "Changes apply right away over QMI. $afterReboot"
        }

        Backend.MEDIATEK -> {
            "Changes are sent with AT+EPBSE and AT+EPBSEH on ${state.modem.atNode.ifEmpty { "the AT port found automatically" }}."
        }

        Backend.PIXEL -> {
            "Changes are saved to the modem's NV memory, and the radio restarts by itself to apply them."
        }

        Backend.ANDROID, null -> {
            "This uses the radio HAL's system selection channels. Whether the modem follows them depends on the phone maker."
        }
    }

private fun selectionSummary(
    bandGroup: BandGroupState,
    selected: Set<Int>,
): String =
    buildString {
        append("${selected.size} of ${bandGroup.supported.size} selected")
        val stray = bandGroup.stray
        if (stray !== null) {
            append(", locked but the phone is on ${bandGroup.group.bandLabel(stray)}")
        } else if (bandGroup.locked) {
            append(", locked")
        }
        if (selected != bandGroup.allowed) append(", not applied yet")
    }

@Composable
private fun BandGroup(
    bandGroup: BandGroupState,
    selected: Set<Int>,
    favorites: Set<Int>,
    busy: Boolean,
    simSelectionRequired: Boolean,
    onToggle: (Int) -> Unit,
    onToggleFavorite: (Int) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
) {
    var pickingFavorites by rememberSaveable { mutableStateOf(false) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val supportedFavorites = bandGroup.supported.filter { it in favorites }
    val canFilter = supportedFavorites.isNotEmpty() && !pickingFavorites
    val shownBands = if (canFilter && !showAll) supportedFavorites else bandGroup.supported

    fun pickFavorites(picking: Boolean) {
        pickingFavorites = picking
        showAll = false
    }

    Panel(Modifier.padding(top = 2.dp), padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(bandGroup.group.label, style = MaterialTheme.typography.titleLargeEmphasized)
                Text(
                    if (pickingFavorites) "Choose which bands stay pinned here." else selectionSummary(bandGroup, selected),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconToggleButton(checked = pickingFavorites, onCheckedChange = ::pickFavorites) {
                Icon(Icons.Rounded.Star, contentDescription = translateUiText("Pick favorite bands", LocalAppLanguage.current))
            }
            LockBadge(bandGroup.locked && bandGroup.stray === null, size = 38.dp, ignored = bandGroup.stray !== null)
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (pickingFavorites) "FAVORITES" else "${shownBands.size} BANDS",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            if (canFilter) {
                TextButton(onClick = { showAll = !showAll }, shapes = ButtonDefaults.shapes()) {
                    Text(if (showAll) "Favorites" else "Show all")
                }
            } else if (pickingFavorites) {
                TextButton(onClick = { pickFavorites(false) }, shapes = ButtonDefaults.shapes()) { Text("Done") }
            } else {
                TextButton(onClick = { onSelectAll(false) }, shapes = ButtonDefaults.shapes()) { Text("Clear") }
                TextButton(onClick = { onSelectAll(true) }, shapes = ButtonDefaults.shapes()) { Text("All") }
            }
        }

        BandCircleGrid(
            options = shownBands,
            isChecked = { if (pickingFavorites) it in favorites else it in selected },
            label = { bandGroup.group.bandLabel(it) },
            detail = { bandGroup.group.frequencyName(it).orEmpty() },
            onToggle = if (pickingFavorites) onToggleFavorite else onToggle,
            enabled = !busy,
            accent = pickingFavorites,
        )

        if (!pickingFavorites) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onUnlock,
                    enabled = !busy && !simSelectionRequired && bandGroup.locked,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) { Text("Unlock group") }
                Button(
                    onClick = onLock,
                    enabled = !busy && !simSelectionRequired && selected.isNotEmpty(),
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(BunnyIcons.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Apply selection")
                }
            }
        }
    }
}
