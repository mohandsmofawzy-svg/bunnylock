@file:Suppress("FunctionName")

package dev.ririxi.bunnylock.ui

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.ririxi.bunnylock.modem.Backend
import dev.ririxi.bunnylock.modem.DeviceInfo
import dev.ririxi.bunnylock.modem.andList

private val AT_COMMAND_PRESETS = listOf("ATI", "AT+EPBSE?", "AT+EPBSEH?", "AT+EMMCHLCK?", "AT+GOOGGETNV=\"NR.CONFIG.MODE\"")
private val CFUN_MODES =
    listOf(
        Triple("AT+CFUN=1", "Full functionality", "Enables full modem functionality; the radio may register or reconnect."),
        Triple("AT+CFUN=2", "RF transmit disabled", "Disables RF transmission on common implementations; service or data may be interrupted."),
        Triple("AT+CFUN=3", "RF transmit and receive disabled", "Disables RF transmit and receive on common implementations; cellular service may be lost."),
    )

@Composable
fun ToolsScreen(
    state: UiState,
    vm: MainViewModel,
    contentPadding: PaddingValues,
) {
    var atCommand by rememberSaveable { mutableStateOf("") }
    // The QMI console starts on NAS "get system info" (service 3, message 0x004D), a read-only request.
    var qmiService by rememberSaveable { mutableStateOf("3") }
    var qmiMessage by rememberSaveable { mutableStateOf("004D") }
    var qmiPayload by rememberSaveable { mutableStateOf("") }
    var checkedServicesOpen by rememberSaveable { mutableStateOf(false) }
    var pendingCfunCommand by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val arrowAngle by animateFloatAsState(if (checkedServicesOpen) 180f else 0f, label = "arrow")
    fun sendAtFromConsole() {
        val command = atCommand.trim()
        val containsCfunSetting = Regex("(?i)(?:^|[;\\r\\n])\\s*AT\\+CFUN\\s*=").containsMatchIn(command)
        if (containsCfunSetting) {
            pendingCfunCommand = command
        } else if (command.isNotEmpty()) {
            vm.sendAt(command)
        }
    }

    LazyColumn(contentPadding = contentPadding.withScreenMargins(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            SectionHeader("Control room")
            val selectedSim = state.modem.sims.firstOrNull { it.slot + 1 == state.modem.sim }
            Panel(padding = PaddingValues(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(state.modem.backend?.label ?: "Modem backend", style = MaterialTheme.typography.headlineSmallEmphasized)
                        Text(
                            if (state.modem.simSelectionRequired) "SIM confirmation required" else "Control path ready",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (state.modem.simSelectionRequired) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(BunnyIcons.Bands, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MenuFact("SIM", selectedSim?.name ?: "Not selected", Modifier.weight(1f))
                    MenuFact("Chipset", state.modem.device?.vendorLabel ?: "Detecting", Modifier.weight(1f))
                    MenuFact("Access", state.modem.access?.label ?: "Unavailable", Modifier.weight(1f))
                }
            }
        }

        item {
            SectionHeader("Radio maintenance")
            Panel {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RadioAction(
                        "Restart radio",
                        "Temporarily toggles airplane mode.",
                        Icons.Rounded.Refresh,
                        !state.busy && !state.dynamicBandEnabled && !state.modem.simSelectionRequired,
                        vm::radioCycle,
                        Modifier.weight(1f),
                    )
                    if (state.modem.backend == Backend.QUALCOMM) {
                        RadioAction(
                            "Re-attach",
                            "Reconnects data using QMI.",
                            Icons.Rounded.Refresh,
                            !state.busy && !state.dynamicBandEnabled && !state.modem.simSelectionRequired,
                            vm::reattach,
                            Modifier.weight(1f),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RadioAction(
                        "Reboot modem",
                        "Restarts the selected SIM.",
                        Icons.Rounded.Warning,
                        !state.busy && !state.dynamicBandEnabled && !state.modem.simSelectionRequired,
                        vm::rebootModem,
                        Modifier.weight(1f),
                    )
                    if (state.modem.device?.samsungMenu == true) {
                        RadioAction(
                            "Samsung bands",
                            "Opens the OEM band menu.",
                            Icons.Rounded.Settings,
                            !state.dynamicBandEnabled && !state.modem.simSelectionRequired,
                            vm::samsungMenu,
                            Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        item {
            SectionHeader("Control method")
            Segmented {
                Backend.entries.forEach { backend ->
                    val selected = state.modem.backend == backend
                    val needsRoot = !state.modem.allows(backend)
                    val hint =
                        when {
                            needsRoot -> "Needs root"
                            state.modem.autoBackend == backend -> "Detected for this device"
                            else -> null
                        }
                    item { shapes ->
                        SegmentedListItem(
                            selected = selected,
                            onClick = { vm.setBackend(backend) },
                            enabled = !needsRoot && !state.busy && !state.dynamicBandEnabled && !state.modem.refreshing && !state.modem.simSelectionRequired,
                            shapes = shapes,
                            colors = segmentColors(),
                            supportingContent = if (hint !== null) ({ Text(hint) }) else null,
                            trailingContent = if (selected) ({ Icon(Icons.Rounded.Check, contentDescription = null) }) else null,
                        ) { Text(backend.label) }
                    }
                }
                if (state.modem.backend == Backend.QUALCOMM) {
                    item {
                        SwitchItem(
                            it,
                            "Keep band locks after reboot",
                            "When this is off, locks are cleared on reboot.",
                            checked = state.modem.persist,
                            enabled = !state.busy && !state.dynamicBandEnabled && !state.modem.refreshing,
                            onCheckedChange = vm::setPersist,
                        )
                    }
                }
            }
        }

        item {
            SectionHeader("Language and display")
            ConnectedChoice(
                options = listOf("en", "ar"),
                selected = state.language,
                label = { if (it == "ar") "العربية" else "English" },
                onSelect = { language ->
                    vm.setLanguage(language)
                    (context as? Activity)?.recreate()
                },
            )
        }

        item {
            SectionHeader("Updates")
            Segmented {
                item {
                    SwitchItem(
                        it,
                        "Check for updates",
                        "Checks for a release on launch when a releases API URL is configured.",
                        checked = state.checkUpdates,
                        onCheckedChange = vm::setCheckUpdates,
                    )
                }
            }
        }

        item {
            SectionHeader("Engineering console · AT")
            Panel {
                OutlinedTextField(
                    state.modem.atNode,
                    vm::setAtNode,
                    label = { Text("AT port") },
                    singleLine = true,
                    textStyle = ltrInputTextStyle(),
                    enabled = !state.busy && !state.dynamicBandEnabled && !state.modem.refreshing && !state.modem.simSelectionRequired,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text(atNodesHint(state.modem.device)) },
                )
                OutlinedTextField(
                    atCommand,
                    { atCommand = it },
                    label = { Text("Command") },
                    singleLine = true,
                    textStyle = ltrInputTextStyle(),
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(
                            onClick = ::sendAtFromConsole,
                            enabled = !state.busy && !state.dynamicBandEnabled && !state.modem.simSelectionRequired && state.modem.atNode.isNotBlank(),
                        ) { Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = translateUiText("Send", state.language)) }
                    },
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AT_COMMAND_PRESETS.forEach { preset ->
                        FilledTonalButton(onClick = { atCommand = preset }, shapes = ButtonDefaults.shapes()) { Text(preset) }
                    }
                }
                Text("Phone functionality · AT+CFUN", style = MaterialTheme.typography.titleSmallEmphasized)
                CFUN_MODES.forEach { (command, title, description) ->
                    FilledTonalButton(
                        onClick = { atCommand = command },
                        enabled = !state.dynamicBandEnabled,
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("$command · $title")
                            Text(description, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Text(
                    "Choose a preset to fill the AT field; it is not sent until you press Send. CFUN behavior varies by modem firmware and may interrupt mobile service.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }

        if (state.modem.backend == Backend.QUALCOMM) {
            item {
                SectionHeader("Engineering console · QMI")
                Panel {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            qmiService,
                            { qmiService = it.filter(Char::isDigit) },
                            label = { Text("Service") },
                            singleLine = true,
                            textStyle = ltrInputTextStyle(),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            qmiMessage,
                            { qmiMessage = it.filter(Char::isLetterOrDigit) },
                            label = { Text("Message (hex)") },
                            singleLine = true,
                            textStyle = ltrInputTextStyle(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedTextField(
                        qmiPayload,
                        { qmiPayload = it },
                        label = { Text("TLV payload (hex)") },
                        singleLine = true,
                        textStyle = ltrInputTextStyle(),
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = { Text("NAS (service 3) talks to the selected SIM.") },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val service = qmiService.toIntOrNull()
                                    val message = qmiMessage.toIntOrNull(16)
                                    if (service !== null && message !== null) vm.sendQmi(service, message, qmiPayload)
                                },
                                enabled = !state.busy && !state.dynamicBandEnabled && !state.modem.simSelectionRequired,
                            ) { Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = translateUiText("Send", state.language)) }
                        },
                    )
                }
            }
        }

        if (state.toolOutput.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth()) {
                    SectionHeader("Output", Modifier.weight(1f))
                    TextButton(onClick = vm::clearOutput) { Text("Clear") }
                }
                MonospacePanel(state.toolOutput)
            }
        }

        item {
            val device = state.modem.device
            SectionHeader("Device")
            Segmented {
                item {
                    ActionItem(
                        it,
                        "Copy diagnostics",
                        "Device details, the passthrough check and the log",
                        Icons.Rounded.Share,
                        enabled = true,
                        onClick = vm::copyDiagnostics,
                    )
                }
                item { InfoItem(it, "Access", state.modem.access?.label.orEmpty()) }
                if (device !== null) {
                    item { InfoItem(it, "Modem command passthrough", device.mtkOemHookStatus) }
                    if (device.mtkOemHookChecked.isNotEmpty()) {
                        item { shapes ->
                            val checkedServicesText: @Composable () -> Unit = {
                                Text(device.mtkOemHookChecked.joinToString("\n"), style = MaterialTheme.typography.bodySmall)
                            }
                            SegmentedListItem(
                                onClick = { checkedServicesOpen = !checkedServicesOpen },
                                shapes = shapes,
                                colors = segmentColors(),
                                supportingContent = if (checkedServicesOpen) checkedServicesText else null,
                                trailingContent = {
                                    Icon(
                                        Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = translateUiText(if (checkedServicesOpen) "Collapse" else "Expand", state.language),
                                        modifier = Modifier.rotate(arrowAngle),
                                    )
                                },
                            ) {
                                Text(
                                    "Services checked (${device.mtkOemHookChecked.size})",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                item { InfoItem(it, "Modem", device?.vendorLabel.orEmpty()) }
                item { InfoItem(it, "SoC", device?.socModel.orEmpty()) }
                item { InfoItem(it, "Platform", device?.platform.orEmpty()) }
                item { InfoItem(it, "RIL", device?.ril.orEmpty()) }
                item { InfoItem(it, "Baseband", device?.baseband.orEmpty()) }
                item { InfoItem(it, "Android SDK", device?.sdk?.toString().orEmpty()) }
                item { InfoItem(it, "QMI over QRTR", if (device?.qrtr == true) "Yes" else "No") }
                item { InfoItem(it, "HAL channel-lock API", if (device?.aospChannels == true) "Present" else "Absent") }
            }
        }

        if (state.modem.log.isNotEmpty()) {
            item {
                SectionHeader("Log")
                MonospacePanel(state.modem.log.joinToString("\n"))
            }
        }

        item {
            SectionHeader("Application")
            Segmented {
                item {
                    ActionItem(
                        it,
                        "Exit",
                        "Stop app tasks and close safely.",
                        Icons.Rounded.Close,
                        enabled = true,
                        onClick = { vm.exit { (context as? Activity)?.finishAndRemoveTask() } },
                    )
                }
            }
        }

        item {
            SectionHeader("About")
            About()
        }
    }

    pendingCfunCommand?.let { command ->
        val mode = command.substringAfter('=').trim()
        val explanation =
            CFUN_MODES.firstOrNull { it.first.endsWith("=$mode") }?.let { it.second + ". " + it.third }
                ?: "CFUN settings can change radio functionality; exact behavior depends on modem firmware."
        AlertDialog(
            onDismissRequest = { pendingCfunCommand = null },
            title = { Text("Confirm modem functionality change") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(command, style = MaterialTheme.typography.titleMediumEmphasized)
                    Text(explanation)
                    Text(
                        "This may interrupt cellular data. The application cannot guarantee a no-disconnect transition.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingCfunCommand = null
                        vm.sendAt(command)
                    },
                ) { Text("Send anyway") }
            },
            dismissButton = {
                TextButton(onClick = { pendingCfunCommand = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun MenuFact(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodySmallEmphasized, maxLines = 1)
        }
    }
}

@Composable
private fun RadioAction(
    title: String,
    description: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(title, modifier = Modifier.padding(start = 6.dp), maxLines = 1)
        }
        Text(
            description,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

private val BUNNY_FACTS =
    listOf(
        "A rabbit's teeth never stop growing, so they have to keep chewing to wear them down.",
        "A happy rabbit does a \"binky\": a jump with a twist in mid-air.",
        "Rabbits can see almost all the way around themselves. Their one blind spot is right in front of the nose.",
        "Rabbits can't vomit, so they're very careful about what they eat.",
        "A content rabbit \"purrs\" by gently grinding its teeth.",
        "Baby rabbits are called kits.",
        "Rabbits are most active at dawn and dusk.",
        "Those big ears also work as radiators and help a rabbit cool down.",
        "A group of rabbits is sometimes called a fluffle.",
        "A thumping rabbit is warning everyone nearby that something seems off.",
    )

private const val TAPS_FOR_FACT = 5

@Composable
private fun About() {
    val context = LocalContext.current
    val version =
        remember {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
        }
    var taps by rememberSaveable { mutableIntStateOf(0) }
    val fact = if (taps >= TAPS_FOR_FACT) BUNNY_FACTS[(taps - TAPS_FOR_FACT) % BUNNY_FACTS.size] else null
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LockBadge(locked = fact !== null, size = 56.dp, spin = true)
            Column(Modifier.weight(1f)) {
                Wordmark(
                    style = MaterialTheme.typography.headlineMediumEmphasized,
                    modifier =
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { taps++ },
                )
                Text("by @ririxi (BTSearch.pl developer)", style = MaterialTheme.typography.bodyMedium)
                if (version.isNotEmpty()) {
                    Text(
                        "Version $version",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        AnimatedVisibility(visible = fact !== null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(BunnyIcons.Bunny, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Text(
                    "Bunny fact: ${fact.orEmpty()}",
                    style = MaterialTheme.typography.bodyMediumEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun atNodesHint(device: DeviceInfo?): String {
    val rilPorts = if (device?.mtkOemHook == true) listOf("ril:1 and ril:2 (through the RIL)") else emptyList()
    val nodes = device?.atNodes.orEmpty() + rilPorts
    return if (nodes.isEmpty()) "No AT ports found." else "Found ${andList(nodes)}."
}

@Composable
private fun ActionItem(
    shapes: ListItemShapes,
    title: String,
    subtitle: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        enabled = enabled,
        shapes = shapes,
        colors = segmentColors(),
        leadingContent = { Icon(icon, contentDescription = null) },
        supportingContent = { Text(subtitle) },
    ) { Text(title) }
}

@Composable
private fun SwitchItem(
    shapes: ListItemShapes,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        shapes = shapes,
        colors = segmentColors(),
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
    ) { Text(title) }
}

@Composable
private fun MonospacePanel(text: String) {
    Panel {
        SelectionContainer {
            Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
    }
}
