@file:Suppress("FunctionName")

package dev.ririxi.bunnylock.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.ririxi.bunnylock.data.Release
import dev.ririxi.bunnylock.modem.Access
import dev.ririxi.bunnylock.modem.RootState
import dev.ririxi.bunnylock.modem.SimInfo
import java.net.URI
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

// تقليل الاستطلاع الحي إلى دورة أبطأ يخفف قراءات الخلايا دون المساس بأوامر المودم أو مهلاتها.
private val LIVE_REFRESH_PAUSE = 1.seconds

enum class Tab(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Main", Icons.Rounded.Home),
    BANDS("Bands", BunnyIcons.Bands),
    CELL("Cell lock", BunnyIcons.Tower),
    TOOLS("Menu", Icons.Rounded.Build),
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BunnyApp(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableStateOf(Tab.HOME) }
    if (!state.exiting && state.modem.root == RootState.Granted && (selectedTab == Tab.HOME || selectedTab == Tab.CELL)) LiveRefresh(vm)
    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    LaunchedEffect(state.language) { vm.events.collect { snackbar.showSnackbar(translateUiText(it, state.language)) } }
    var pulledToRefresh by remember { mutableStateOf(false) }
    LaunchedEffect(state.modem.refreshing) { if (!state.modem.refreshing) pulledToRefresh = false }
    // Refreshes the app starts itself, like the one after every change, show in the app bar so the screen doesn't jump.
    val bunnyRefreshing = state.modem.refreshing && pulledToRefresh

    state.update?.let { UpdateDialog(it, onSkip = vm::skipUpdate, onDismiss = vm::dismissUpdate) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        Wordmark(style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${translateUiText(selectedTab.label, state.language)} · ${translateUiText(topBarSubtitle(state), state.language)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    if (state.modem.sims.size > 1 || state.modem.simSelectionRequired) {
                        SimChip(
                            state.modem.sims,
                            state.modem.sim,
                            enabled = !state.exiting && !state.busy && !state.modem.refreshing,
                            confirmSelection = state.modem.simSelectionRequired,
                            onSelect = vm::setSim,
                        )
                    }
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        when {
                            state.exiting || state.busy || (state.modem.refreshing && !pulledToRefresh) || state.modem.root == RootState.Checking -> {
                                LoadingIndicator(Modifier.size(36.dp))
                            }

                            state.modem.root == RootState.Granted -> {
                                IconButton(onClick = vm::refresh, enabled = !state.exiting && !state.modem.simSelectionRequired) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = translateUiText("Refresh", state.language))
                                }
                            }
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            if (state.modem.root == RootState.Granted) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 4.dp,
                ) {
                    Row(Modifier.fillMaxWidth()) {
                        Tab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            Surface(
                                onClick = { selectedTab = tab },
                                modifier = Modifier.weight(1f).padding(5.dp),
                                shape = RoundedCornerShape(18.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            ) {
                                Column(
                                    Modifier.padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Icon(tab.icon, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Text(tab.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        when (val root = state.modem.root) {
            RootState.Checking -> {
                Centered(innerPadding) {
                    HoppingBunny()
                    Text(state.modem.connectStep, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMediumEmphasized)
                }
            }

            is RootState.Denied -> {
                Centered(innerPadding) {
                    DroopyBunny()
                    Text("Root or Shizuku needed", style = MaterialTheme.typography.headlineMediumEmphasized)
                    Text(
                        root.reason,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = vm::connect, shapes = ButtonDefaults.shapes()) { Text("Try again") }
                    TextButton(onClick = vm::copyDiagnostics, shapes = ButtonDefaults.shapes()) { Text("Copy diagnostics") }
                }
            }

            RootState.Granted -> {
                val pullToRefreshState = rememberPullToRefreshState()
                val contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding())
                PullToRefreshBox(
                    isRefreshing = bunnyRefreshing,
                    onRefresh = {
                        pulledToRefresh = true
                        vm.refresh()
                    },
                    state = pullToRefreshState,
                    modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding()),
                    indicator = {
                        BunnyRefreshIndicator(
                            state = pullToRefreshState,
                            isRefreshing = bunnyRefreshing,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    },
                ) {
                    Column(Modifier.fillMaxSize()) {
                        if (state.modem.simSelectionRequired) {
                            SimSelectionWarning(state, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                        // Inside the refresh box so the app bar expands before an overscroll becomes a refresh.
                        AnimatedContent(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                                    .belowBunnyRefresh(pullToRefreshState),
                            targetState = selectedTab,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "tab",
                        ) { tab ->
                            when (tab) {
                                Tab.HOME -> HomeScreen(state, vm, contentPadding) { selectedTab = it }
                                Tab.BANDS -> BandsScreen(state, vm, contentPadding)
                                Tab.CELL -> CellScreen(state, vm, contentPadding)
                                Tab.TOOLS -> ToolsScreen(state, vm, contentPadding)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateDialog(
    release: Release,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val downloadUrlAvailable = isValidHttpUrl(release.url)
    var linkError by remember(release.url) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update available") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Version ${release.version} is out.")
                if (release.notes.isNotEmpty()) Text(release.notes, style = MaterialTheme.typography.bodyMedium)
                linkError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (downloadUrlAvailable) {
                    Button(
                        onClick = {
                            try {
                                uriHandler.openUri(release.url)
                                onDismiss()
                            } catch (e: Exception) {
                                linkError = e.message ?: "Couldn't open the update link."
                            }
                        },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Download") }
                } else {
                    Text("No valid update link is configured.", color = MaterialTheme.colorScheme.error)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onSkip, shapes = ButtonDefaults.shapes()) { Text("Skip this version") }
                    TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text("Later") }
                }
            }
        },
    )
}

private fun isValidHttpUrl(value: String): Boolean =
    runCatching { URI(value) }
        .getOrNull()
        ?.let { it.isAbsolute && it.scheme in setOf("https", "http") && !it.host.isNullOrBlank() }
        ?: false

@Composable
private fun SimSelectionWarning(
    state: UiState,
    modifier: Modifier = Modifier,
) {
    Panel(modifier, padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Text("SIM selection needs confirmation", style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.tertiary)
        state.modem.savedSimName?.let { Text("Previously selected SIM: $it", style = MaterialTheme.typography.bodyMediumEmphasized) }
        Text(
            "The active SIM mapping is not verified. Choose an active SIM from the selector; if none is listed, grant phone-state access. Modem controls stay disabled until the subscription ID and SIM slot can be matched.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LiveRefresh(vm: MainViewModel) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                vm.refreshConnection()
                delay(LIVE_REFRESH_PAUSE)
            }
        }
    }
}

private fun topBarSubtitle(state: UiState): String {
    val throughShizuku = state.modem.access == Access.SHIZUKU_ADB || state.modem.access == Access.SHIZUKU_ROOT
    val accessSuffix = if (throughShizuku) ", through Shizuku" else ""
    return (state.modem.backend?.label ?: "Band and cell locking") + accessSuffix
}

/** [selected] counts from 1. */
@Composable
private fun SimChip(
    sims: List<SimInfo>,
    selected: Int,
    enabled: Boolean,
    confirmSelection: Boolean,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = sims.firstOrNull { it.slot + 1 == selected } ?: return
    val language = LocalAppLanguage.current
    Box {
        FilledTonalButton(
            onClick = { expanded = true },
            enabled = enabled,
            shapes = ButtonDefaults.shapes(),
            contentPadding = PaddingValues(start = 8.dp, end = 6.dp),
            modifier = Modifier.semantics {
                contentDescription = translateUiText("SIM $selected, ${current.name}. Switch SIM", language)
            },
        ) {
            SimNumber(selected)
            Spacer(Modifier.width(8.dp))
            Text(current.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp))
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            sims.forEach { sim ->
                val number = sim.slot + 1
                DropdownMenuItem(
                    text = { Text("SIM $number: ${sim.name}") },
                    leadingIcon = { SimNumber(number) },
                    trailingIcon = { if (number == selected) Icon(Icons.Rounded.Check, contentDescription = translateUiText("Selected", LocalAppLanguage.current)) },
                    onClick = {
                        expanded = false
                        if (number != selected || confirmSelection) onSelect(number)
                    },
                )
            }
        }
    }
}

@Composable
private fun SimNumber(number: Int) {
    val colors = MaterialTheme.colorScheme
    val container = if (number == 1) colors.primary else colors.tertiary
    val content = if (number == 1) colors.onPrimary else colors.onTertiary
    Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(container), contentAlignment = Alignment.Center) {
        Text("$number", color = content, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun Centered(
    innerPadding: PaddingValues,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize().padding(innerPadding).padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            content()
        }
    }
}

fun PaddingValues.withScreenMargins(): PaddingValues =
    PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = calculateTopPadding() + 4.dp,
        bottom = calculateBottomPadding() + 24.dp,
    )
