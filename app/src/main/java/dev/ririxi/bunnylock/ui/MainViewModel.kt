package dev.ririxi.bunnylock.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.ririxi.bunnylock.core.Bands
import dev.ririxi.bunnylock.data.Cell
import dev.ririxi.bunnylock.data.LockNotifier
import dev.ririxi.bunnylock.data.Rat
import dev.ririxi.bunnylock.data.Updates
import dev.ririxi.bunnylock.modem.Backend
import dev.ririxi.bunnylock.modem.BandGroupState
import dev.ririxi.bunnylock.modem.CellLockRequest
import dev.ririxi.bunnylock.modem.Group
import dev.ririxi.bunnylock.modem.LockRat
import dev.ririxi.bunnylock.modem.LockStatus
import dev.ririxi.bunnylock.modem.ModemController
import dev.ririxi.bunnylock.modem.ModemState
import dev.ririxi.bunnylock.modem.NetMode
import dev.ririxi.bunnylock.modem.NrMode
import dev.ririxi.bunnylock.modem.RootState
import dev.ririxi.bunnylock.modem.reason
import dev.ririxi.bunnylock.shizuku.RootOrShizuku
import dev.ririxi.bunnylock.shizuku.ShizukuClient
import dev.ririxi.bunnylock.watchdog.Watchdog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
private const val MAX_TOOL_OUTPUT_CHARS = 8000
private const val DEFAULT_DYNAMIC_BAND_INTERVAL_SECONDS = 30
private const val DYNAMIC_BAND_VERIFY_ATTEMPTS = 4
private const val DYNAMIC_BAND_VERIFY_INTERVAL_MS = 1_000L
private const val MIN_DYNAMIC_BAND_INTERVAL_SECONDS = 10
private const val MAX_DYNAMIC_BAND_INTERVAL_SECONDS = 3600

class MainViewModel(
    app: Application,
) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("bunnylock", Context.MODE_PRIVATE)

    private val _ui =
        MutableStateFlow(
            UiState(
                checkUpdates = prefs.getBoolean("checkUpdates", true),
                language = prefs.getString("language", if (java.util.Locale.getDefault().language == "ar") "ar" else "en") ?: "en",
                favoriteBands = loadFavoriteBands(),
                dynamicBandIntervalSeconds =
                    prefs.getInt("dynamicBandIntervalSeconds", DEFAULT_DYNAMIC_BAND_INTERVAL_SECONDS)
                        .coerceIn(MIN_DYNAMIC_BAND_INTERVAL_SECONDS, MAX_DYNAMIC_BAND_INTERVAL_SECONDS),
            ),
        )

    private val ui get() = _ui.value

    private inline fun updateState(transform: UiState.() -> UiState) = _ui.update(transform)

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)

    private var pendingUnlock = false
    private var exitRequested = false
    private var updateCheckJob: Job? = null
    private var dynamicBandJob: Job? = null
    private var dynamicBandSelectionSnapshot: Map<Group, Set<Int>> = emptyMap()

    private data class DynamicBandTarget(
        val group: Group,
        val band: Int,
    ) {
        val label: String get() = group.bandLabel(band)
    }

    private data class DynamicBandVerification(
        val result: String,
        val detail: String,
        val succeeded: Boolean,
    )

    private val modem =
        ModemController(app, viewModelScope, RootOrShizuku(app, ::shizukuLost), onConnected = ::unlockIfPending, onRefreshed = ::refreshed)

    // Shizuku hands over its binder a moment after the app starts, or whenever it is started later, so a failed attempt is redone then.
    private val stopWatchingShizuku = ShizukuClient.onBinderReceived(modem::reconnect)

    val state =
        combine(modem.state, _ui, Watchdog.state) { modemState, uiState, watchdog ->
            uiState.copy(modem = modemState, watchdog = watchdog)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ui.copy(modem = modem.state.value, watchdog = Watchdog.state.value))

    val events = merge(_events, modem.events)

    init {
        modem.connect()
        checkForUpdate()
    }

    private fun appVersion(): String? {
        val app = getApplication<Application>()
        return runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull()
    }

    private fun checkForUpdate() {
        updateCheckJob?.cancel()
        updateCheckJob = viewModelScope.launch {
            if (!ui.checkUpdates) return@launch
            val installed = appVersion() ?: return@launch
            try {
                val release = Updates.newerThan(installed)?.takeIf { it.version != prefs.getString("skippedUpdate", null) }
                if (ui.checkUpdates) updateState { copy(update = release) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                modem.log("Couldn't check for updates. ${reason(e)}")
            }
        }
    }

    fun dismissUpdate() {
        if (!exitRequested) updateState { copy(update = null) }
    }

    fun skipUpdate() {
        if (exitRequested) return
        ui.update?.let { prefs.edit().putString("skippedUpdate", it.version).apply() }
        dismissUpdate()
    }

    fun setCheckUpdates(enabled: Boolean) {
        if (exitRequested) return
        prefs.edit().putBoolean("checkUpdates", enabled).apply()
        updateState { copy(checkUpdates = enabled) }
        if (enabled) checkForUpdate() else {
            updateCheckJob?.cancel()
            dismissUpdate()
        }
    }

    override fun onCleared() {
        dynamicBandJob?.cancel()
        stopWatchingShizuku()
        runCatching { Watchdog.stop(getApplication()) }
        modem.close()
    }

    fun connect() {
        if (!exitRequested) modem.connect()
    }

    /** Coming back from the Shizuku app after allowing bunnylock there shouldn't need a tap on "Try again". */
    fun resumed() {
        if (!exitRequested && modem.state.value.root is RootState.Denied && ShizukuClient.granted()) modem.connect()
    }

    private fun shizukuLost() {
        modem.helperLost("The Shizuku service stopped. Try again to restart it.")
    }

    fun refresh() {
        if (exitRequested) return
        if (ui.dynamicBandEnabled) {
            _events.tryEmit("Stop Dynamic Band Switching before refreshing modem state.")
            return
        }
        if (modem.state.value.simSelectionRequired) {
            _events.tryEmit("Select an active SIM again before using modem controls.")
            return
        }
        modem.refresh()
    }

    suspend fun refreshConnection(skipCellScan: Boolean = false) {
        if (!exitRequested && !ui.dynamicBandEnabled && !modem.state.value.simSelectionRequired) modem.refreshConnection(skipCellScan)
    }

    fun exit(onStopped: () -> Unit) {
        if (exitRequested) return
        stopDynamicBandSwitching()
        exitRequested = true
        updateState { copy(exiting = true) }
        try {
            stopWatchingShizuku()
        } catch (e: Exception) {
            _events.tryEmit(reason(e))
        }
        try {
            Watchdog.stop(getApplication())
        } catch (e: Exception) {
            _events.tryEmit(reason(e))
        }
        modem.preventNewOperations()
        viewModelScope.launch {
            try {
                updateCheckJob?.cancel()
                var shutdownError: String? = null
                val stopped =
                    try {
                        withTimeoutOrNull(90.seconds) {
                            modem.shutdown()
                            true
                        } ?: false
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        shutdownError = reason(e)
                        false
                    }
                if (!stopped) {
                    modem.close()
                    _events.tryEmit(shutdownError ?: "The active modem operation took too long to finish; its helper was closed safely.")
                }
                withTimeoutOrNull(12.seconds) { updateCheckJob?.join() }
            } finally {
                try {
                    withContext(kotlinx.coroutines.Dispatchers.IO) { prefs.edit().commit() }
                } finally {
                    onStopped()
                }
            }
        }
    }

    /** "Unlock all" from the notification. Waits for the helper if the app was just started. */
    fun unlockFromNotification() {
        if (exitRequested) return
        if (modem.state.value.root == RootState.Granted && modem.helperRunning) unlockEverything() else pendingUnlock = true
    }

    private fun unlockIfPending() {
        if (exitRequested) {
            pendingUnlock = false
            return
        }
        if (!pendingUnlock) return
        pendingUnlock = false
        unlockEverything()
    }

    // A refresh replaces the band groups, so picks that weren't applied start over.
    private fun refreshed(modemState: ModemState) {
        updateState {
            copy(bandSelections = if (dynamicBandEnabled) dynamicBandSelectionSnapshot else emptyMap())
        }
        updateNotification(modemState)
    }

    /** Lists the locks that hold. One the phone has strayed from is left out. */
    private fun updateNotification(modemState: ModemState) {
        val context = getApplication<Application>()
        val lines =
            buildList {
                modemState.groups.filter { it.locked && it.stray === null }.forEach { groupState ->
                    val bands =
                        groupState.current
                            .orEmpty()
                            .sorted()
                            .joinToString(" ") { groupState.group.bandLabel(it) }
                    add("${groupState.group.label}: $bands")
                }
                modemState.plmnLock?.let { add("Network: $it") }
                if (modemState.strayChannel === null) {
                    modemState.lteLock?.let { add("LTE cell: $it") }
                    modemState.nrLock?.let { add("NR cell: $it") }
                    modemState.legacyCellLock?.let { add("2G/3G cell: $it") }
                }
            }
        if (lines.isEmpty()) {
            prefs.edit().remove("lockedSince").apply()
            LockNotifier.cancel(context)
            return
        }
        val lockedSince =
            prefs.getLong("lockedSince", 0L).takeIf { it > 0 }
                ?: System.currentTimeMillis().also { prefs.edit().putLong("lockedSince", it).apply() }
        LockNotifier.show(context, lines, lockedSince)
    }

    fun loadSims() {
        if (!exitRequested) modem.loadSims()
    }

    fun setSim(sim: Int) {
        if (exitRequested) return
        if (ui.dynamicBandEnabled) {
            _events.tryEmit("Stop Dynamic Band Switching before changing SIM.")
            return
        }
        val selected = modem.state.value.sims.filter { it.slot == sim - 1 }.singleOrNull()
        if (selected == null || selected.subId < 0) {
            _events.tryEmit("This device's SIM-to-modem mapping could not be verified; modem controls remain disabled.")
            return
        }
        modem.setSim(sim)
    }

    fun setBackend(backend: Backend) {
        if (exitRequested) return
        if (ui.dynamicBandEnabled) {
            _events.tryEmit("Stop Dynamic Band Switching before changing the modem backend.")
            return
        }
        if (modem.state.value.simSelectionRequired) {
            _events.tryEmit("Select an active SIM again before using modem controls.")
            return
        }
        modem.setBackend(backend)
    }

    fun setPersist(enabled: Boolean) {
        if (exitRequested) return
        if (ui.dynamicBandEnabled) {
            _events.tryEmit("Stop Dynamic Band Switching before changing modem persistence.")
            return
        }
        modem.setPersist(enabled)
    }

    fun setLanguage(language: String) {
        if (exitRequested) return
        if (language != "en" && language != "ar") return
        // يحفظ الخيار فورًا كي يبقى محددًا بعد إعادة إنشاء Activity أو إعادة تشغيل التطبيق.
        prefs.edit().putString("language", language).apply()
        updateState { copy(language = language) }
    }

    fun setAtNode(node: String) {
        if (exitRequested) return
        if (ui.dynamicBandEnabled) {
            _events.tryEmit("Stop Dynamic Band Switching before changing the modem backend.")
            return
        }
        modem.setAtNode(node)
    }

    // The modem has already logged the outcome.
    private fun launchAction(
        refreshAfter: Boolean = true,
        block: suspend () -> String,
    ) = viewModelScope.launch {
        if (exitRequested || ui.busy) return@launch
        if (ui.dynamicBandEnabled) {
            _events.emit("Stop Dynamic Band Switching before using other modem controls.")
            return@launch
        }
        if (modem.state.value.simSelectionRequired) {
            _events.emit("Select an active SIM again before using modem controls.")
            return@launch
        }
        if (!modem.helperRunning) {
            _events.emit("The helper isn't running, so nothing was changed. Reconnecting.")
            modem.reconnect()
            return@launch
        }
        updateState { copy(busy = true) }
        try {
            _events.emit(block())
            if (refreshAfter && !exitRequested) modem.refreshNow()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _events.emit(reason(e))
        } finally {
            updateState { copy(busy = false) }
        }
    }

    private suspend fun verifyDynamicBand(target: DynamicBandTarget): DynamicBandVerification {
        var lockReadKnown = false
        var lockApplied = false
        var servingReadSucceeded = false

        repeat(DYNAMIC_BAND_VERIFY_ATTEMPTS) { attempt ->
            if (exitRequested || !ui.dynamicBandEnabled) {
                return DynamicBandVerification(
                    "Stopped; verification skipped",
                    "Dynamic Band Switching was stopped before verification completed.",
                    false,
                )
            }

            modem.refreshNow()
            val refreshed = modem.state.value
            if (!refreshed.readbackValid) {
                if (attempt < DYNAMIC_BAND_VERIFY_ATTEMPTS - 1) delay(DYNAMIC_BAND_VERIFY_INTERVAL_MS)
                return@repeat
            }
            val reportedLock = refreshed.groups.firstOrNull { it.group == target.group }?.current
            if (reportedLock != null) {
                lockReadKnown = true
                lockApplied = reportedLock == setOf(target.band)
            }

            val servingCarriers = refreshed.carriers
            if (servingCarriers.isNotEmpty()) servingReadSucceeded = true
            val targetIsServing =
                servingCarriers.any { carrier ->
                    carrier.band == target.band &&
                        if (target.group == Group.LTE) carrier.rat == Rat.LTE else carrier.rat == Rat.NR
                }
            if (lockApplied && targetIsServing) {
                return DynamicBandVerification(
                    "Lock applied; serving band confirmed",
                    "",
                    true,
                )
            }

            if (attempt < DYNAMIC_BAND_VERIFY_ATTEMPTS - 1) delay(DYNAMIC_BAND_VERIFY_INTERVAL_MS)
        }

        return when {
            !lockReadKnown ->
                DynamicBandVerification(
                    "Lock state unknown",
                    "The modem did not provide a readable ${target.label} band-lock state. No further lock command was sent.",
                    false,
                )
            !lockApplied ->
                DynamicBandVerification(
                    "Lock rejected by modem",
                    "The modem readback did not show ${target.label} as the active band lock. Switching stopped.",
                    false,
                )
            !servingReadSucceeded ->
                DynamicBandVerification(
                    "Serving band read failed",
                    "${target.label} is confirmed as the active lock, but no serving-carrier data was available. Switching stopped.",
                    false,
                )
            else ->
                DynamicBandVerification(
                    "Lock applied; serving band not yet confirmed",
                    "${target.label} is confirmed as the active lock, but it was not reported among serving carriers after bounded refreshes. Switching stopped without repeating the lock command.",
                    false,
                )
        }
    }

    private fun groupState(group: Group): BandGroupState? = modem.state.value.groups.firstOrNull { it.group == group }

    private fun updateSelection(
        group: Group,
        transform: BandGroupState.(selected: Set<Int>) -> Set<Int>,
    ) {
        val groupState = groupState(group) ?: return
        updateState { copy(bandSelections = bandSelections + (group to groupState.transform(selectedBands(groupState)))) }
    }

    fun toggleBand(
        group: Group,
        band: Int,
    ) {
        if (exitRequested) return
        if (ui.dynamicBandEnabled) {
            _events.tryEmit("Stop Dynamic Band Switching before changing the band selection.")
            return
        }
        updateSelection(group) { selected ->
            if (band in selected) selected - band else selected + band
        }
    }

    fun toggleFavorite(
        group: Group,
        band: Int,
    ) {
        if (exitRequested) return
        val favorites = ui.favoritesOf(group).let { if (band in it) it - band else it + band }
        prefs.edit().putString(favoritesPrefKey(group.favoritesKey), favorites.sorted().joinToString(",")).apply()
        updateState { copy(favoriteBands = favoriteBands + (group.favoritesKey to favorites)) }
    }

    private fun loadFavoriteBands(): Map<String, Set<Int>> =
        Group.entries.map { it.favoritesKey }.distinct().associateWith { key ->
            prefs.getString(favoritesPrefKey(key), null).orEmpty().split(',').mapNotNull { it.toIntOrNull() }.toSet()
        }

    private fun favoritesPrefKey(key: String) = "favoriteBands.$key"

    fun selectAll(
        group: Group,
        all: Boolean,
    ) {
        if (exitRequested) return
        if (ui.dynamicBandEnabled) {
            _events.tryEmit("Stop Dynamic Band Switching before changing the band selection.")
            return
        }
        updateSelection(group) {
            if (all) supported.toSet() else emptySet()
        }
    }

    fun applyGroup(
        group: Group,
        unlock: Boolean = false,
    ) = launchAction {
        if (unlock) {
            modem.unlockBands(group)
        } else {
            modem.lockBands(group, groupState(group)?.let(ui::selectedBands).orEmpty())
        }
    }

    fun setDynamicBandSwitching(
        enabled: Boolean,
        intervalSeconds: Int = DEFAULT_DYNAMIC_BAND_INTERVAL_SECONDS,
    ) {
        if (exitRequested) return
        if (!enabled) {
            stopDynamicBandSwitching()
            return
        }
        if (ui.dynamicBandEnabled) return
        if (dynamicBandJob?.isActive == true) {
            _events.tryEmit("Wait for the current Dynamic Band Switching task to stop.")
            return
        }
        if (intervalSeconds !in MIN_DYNAMIC_BAND_INTERVAL_SECONDS..MAX_DYNAMIC_BAND_INTERVAL_SECONDS) {
            _events.tryEmit("Enter an interval between 10 and 3600 seconds.")
            return
        }
        saveDynamicBandInterval(intervalSeconds)
        if (ui.busy || modem.state.value.refreshing) {
            _events.tryEmit("Wait for the current modem operation to finish.")
            return
        }

        if (modem.state.value.simSelectionRequired) {
            _events.tryEmit("Select an active SIM again before using modem controls.")
            return
        }
        if (!modem.helperRunning) {
            _events.tryEmit("The modem helper is not ready. Reconnect before starting Dynamic Band Switching.")
            return
        }
        when (modem.state.value.backend) {
            Backend.PIXEL -> {
                _events.tryEmit("Dynamic Band Switching is unavailable on Pixel because applying band locks restarts the radio.")
                return
            }
            Backend.QUALCOMM, Backend.MEDIATEK, Backend.ANDROID -> Unit
            null -> {
                _events.tryEmit("Connect to a supported modem backend before starting Dynamic Band Switching.")
                return
            }
        }

        val selectionSnapshot = ui.bandSelections
        updateState { copy(busy = true) }
        viewModelScope.launch {
            try {
                modem.refreshNow()
                if (exitRequested) return@launch
                updateState { copy(bandSelections = selectionSnapshot) }
                startDynamicBandSwitchingAfterRefresh(intervalSeconds)
            } finally {
                updateState { copy(busy = false) }
            }
        }
    }

    fun setDynamicBandInterval(intervalSeconds: Int) {
        if (exitRequested || intervalSeconds !in MIN_DYNAMIC_BAND_INTERVAL_SECONDS..MAX_DYNAMIC_BAND_INTERVAL_SECONDS) return
        saveDynamicBandInterval(intervalSeconds)
    }

    private fun saveDynamicBandInterval(intervalSeconds: Int) {
        prefs.edit().putInt("dynamicBandIntervalSeconds", intervalSeconds).apply()
        updateState { copy(dynamicBandIntervalSeconds = intervalSeconds) }
    }

    // يفصل هذا المسار بين قائمة الباندات المختارة وقفل الخلية الفعلي، ويمنع أي تغيير في الراديو إذا تعذّر التحقق من القفل.
    private fun startDynamicBandSwitchingAfterRefresh(intervalSeconds: Int) {
        if (exitRequested || ui.dynamicBandEnabled) return
        when {
            modem.state.value.cellLockStatus == LockStatus.ACTIVE -> {
                _events.tryEmit("Dynamic Band Switching is blocked by an active cell lock.")
                return
            }
            modem.state.value.cellLockStatus == LockStatus.UNKNOWN -> {
                _events.tryEmit("Couldn't verify that the cell lock is cleared. Refresh modem state and try again.")
                return
            }
        }
        val serving = modem.state.value.carriers
        val hasNr = serving.any { it.rat == Rat.NR }
        val hasLte = serving.any { it.rat == Rat.LTE }
        if (!hasNr && !hasLte) {
            _events.tryEmit("Couldn't determine whether the active connection is LTE or NR. Refresh the connection and try again.")
            return
        }
        val activeGroup =
            when {
                !hasNr -> Group.LTE
                modem.state.value.backend == Backend.QUALCOMM && hasLte -> Group.NR_NSA
                modem.state.value.backend == Backend.QUALCOMM -> Group.NR_SA
                else -> Group.NR
            }

        val targets =
            modem.state.value.groups
                .filter { it.group == activeGroup }
                .flatMap { groupState ->
                    val selected = ui.selectedBands(groupState)
                    groupState.supported.filter { it in selected }.map { DynamicBandTarget(groupState.group, it) }
                }.distinctBy { it.band }
        dynamicBandSelectionSnapshot =
            modem.state.value.groups.associate { groupState ->
                groupState.group to ui.selectedBands(groupState)
            }
        if (targets.isEmpty()) {
            _events.tryEmit("Select at least one supported ${activeGroup.label} band before starting.")
            return
        }
        // نطاق واحد يعني مراقبة دورية للحالة فقط؛ تبديل عدة نطاقات وحده يستخدم واجهة القفل القائمة.
        val readOnly = targets.size == 1

        updateState {
            copy(
                dynamicBandEnabled = true,
                dynamicBandReadOnly = readOnly,
                dynamicBandCurrent = null,
                dynamicBandNext = null,
                dynamicBandSecondsRemaining = 0,
                dynamicBandLastResult = if (readOnly) "Read-only refresh mode" else "Waiting to start",
                dynamicBandError = null,
                bandSelections = dynamicBandSelectionSnapshot,
            )
        }
        dynamicBandJob =
            viewModelScope.launch {
                val currentIndex =
                    targets.indexOfFirst { target ->
                        serving.any { carrier ->
                            carrier.band == target.band &&
                                if (target.group == Group.LTE) carrier.rat == Rat.LTE else carrier.rat == Rat.NR
                        }
                    }
                var index = if (currentIndex >= 0) (currentIndex + 1) % targets.size else 0
                while (!exitRequested && ui.dynamicBandEnabled) {
                    if (ui.busy || modem.state.value.refreshing) {
                        delay(1000)
                        continue
                    }
                    val target = targets[index]
                    updateState {
                        copy(
                            dynamicBandNext = if (readOnly) null else target.label,
                            dynamicBandSecondsRemaining = 0,
                            dynamicBandLastResult = "Switching",
                        )
                    }
                    if (!applyDynamicBand(target, readOnly)) break
                    if (!readOnly) index = (index + 1) % targets.size
                    val cycleIntervalSeconds = ui.dynamicBandIntervalSeconds
                    for (secondsRemaining in cycleIntervalSeconds downTo 1) {
                        if (exitRequested || !ui.dynamicBandEnabled) break
                        updateState {
                            copy(
                                dynamicBandNext = if (readOnly) null else targets[index].label,
                                dynamicBandSecondsRemaining = secondsRemaining,
                            )
                        }
                        delay(1000)
                    }
                }
                if (!ui.dynamicBandEnabled) updateState { copy(dynamicBandSecondsRemaining = 0) }
                dynamicBandJob = null
            }
    }

    private suspend fun applyDynamicBand(
        target: DynamicBandTarget,
        readOnly: Boolean,
    ): Boolean {
        updateState { copy(busy = true, dynamicBandError = null) }
        return try {
            // A single selection is monitoring-only: never write band state because that could trigger reselection.
            if (!readOnly) modem.lockBands(target.group, listOf(target.band), persistOverride = false)
            if (!ui.dynamicBandEnabled || exitRequested) {
                updateState { copy(dynamicBandLastResult = "Stopped; verification skipped") }
                return false
            }
            if (readOnly) {
                // النطاق الواحد وضع مراقبة فقط؛ إعادة تطبيق القفل قد تجبر الشبكة على إعادة الاختيار.
                modem.refreshNow()
                val refreshed = modem.state.value
                if (!refreshed.readbackValid) {
                    val detail = "The modem status refresh failed. No band command was sent."
                    updateState {
                        copy(
                            dynamicBandEnabled = false,
                            dynamicBandReadOnly = false,
                            dynamicBandNext = null,
                            dynamicBandSecondsRemaining = 0,
                            dynamicBandLastResult = "Serving band read failed",
                            dynamicBandError = detail,
                        )
                    }
                    _events.tryEmit("Dynamic Band Switching stopped. $detail")
                    return false
                }
                val servingBand =
                    refreshed.carriers.firstOrNull { carrier ->
                        if (target.group == Group.LTE) carrier.rat == Rat.LTE else carrier.rat == Rat.NR
                    }?.band
                updateState {
                    copy(
                        dynamicBandCurrent = servingBand?.let { target.group.bandLabel(it) },
                        dynamicBandNext = null,
                        dynamicBandLastResult = "Read-only status refreshed",
                        dynamicBandError = null,
                    )
                }
                return true
            }
            val verification = verifyDynamicBand(target)
            if (!verification.succeeded) {
                updateState {
                    copy(
                        dynamicBandEnabled = false,
                        dynamicBandReadOnly = false,
                        dynamicBandNext = null,
                        dynamicBandSecondsRemaining = 0,
                        dynamicBandLastResult = verification.result,
                        dynamicBandError = verification.detail,
                    )
                }
                _events.tryEmit("Dynamic Band Switching stopped. ${verification.detail}")
                return false
            }
            updateState {
                copy(
                    dynamicBandCurrent = target.label,
                    dynamicBandLastResult = verification.result,
                    dynamicBandError = null,
                )
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val detail = reason(e)
            updateState {
                copy(
                    dynamicBandEnabled = false,
                    dynamicBandReadOnly = false,
                    dynamicBandNext = null,
                    dynamicBandSecondsRemaining = 0,
                    dynamicBandLastResult = "Switch failed",
                    dynamicBandError = detail,
                )
            }
            _events.tryEmit("Dynamic Band Switching stopped. $detail")
            false
        } finally {
            updateState { copy(busy = false) }
        }
    }

    private fun stopDynamicBandSwitching() {
        if (!ui.dynamicBandEnabled) return
        updateState {
            copy(
                dynamicBandEnabled = false,
                dynamicBandReadOnly = false,
                dynamicBandNext = null,
                dynamicBandSecondsRemaining = 0,
                dynamicBandLastResult = "Stopped; last band lock kept",
                dynamicBandError = null,
            )
        }
        dynamicBandJob?.cancel()
    }

    fun setNetMode(mode: NetMode) = launchAction { modem.setNetMode(mode) }

    fun setNrMode(mode: NrMode) = launchAction { modem.setNrMode(mode) }

    fun setNrDc(enabled: Boolean) = launchAction { modem.setNrDc(enabled) }

    fun updateForm(transform: CellForm.() -> CellForm) {
        if (!exitRequested) updateState { copy(form = form.transform()) }
    }

    fun fillFrom(cell: Cell) =
        updateForm {
            when (cell.rat) {
                Rat.LTE -> {
                    copy(
                        lteEarfcn = cell.arfcn.toString(),
                        ltePcis = cell.pci.toString(),
                        rat = if (rat == LockRat.NSA) rat else LockRat.LTE,
                    )
                }

                Rat.NR -> {
                    val band = cell.band ?: Bands.nrBandsOf(cell.arfcn.toInt()).firstOrNull()
                    copy(
                        nrArfcn = cell.arfcn.toString(),
                        nrPcis = cell.pci.takeIf { it >= 0 }?.toString() ?: nrPcis,
                        nrBand = band,
                        nrScs = band?.let { Bands.nrDefaultScs(it) } ?: nrScs,
                        nrScsPicked = nrScsPicked && band === null,
                        rat = if (rat == LockRat.NSA) rat else LockRat.NR,
                    )
                }

                else -> {
                    this
                }
            }
        }

    private fun parseInts(text: String) = text.split(',', ' ', ';').mapNotNull { it.trim().toIntOrNull() }

    fun lockCells() =
        launchAction {
            val form = ui.form
            val request =
                CellLockRequest(
                    rat = state.value.lockRat,
                    earfcn = form.lteEarfcn.toLongOrNull(),
                    ltePcis = parseInts(form.ltePcis),
                    nrArfcn = form.nrArfcn.toLongOrNull(),
                    nrPcis = parseInts(form.nrPcis),
                    nrBand = form.nrBand ?: form.nrCandidates.firstOrNull(),
                    nrScs = form.nrScs,
                )
            modem.lockCells(request)
        }

    fun unlockCells() = launchAction { modem.unlockCells() }

    fun setPlmnForm(
        mcc: String? = null,
        mnc: String? = null,
    ) {
        if (exitRequested) return
        updateState {
            copy(plmnMcc = (mcc ?: plmnMcc).filter(Char::isDigit).take(3), plmnMnc = (mnc ?: plmnMnc).filter(Char::isDigit).take(3))
        }
    }

    fun pickPlmn(numeric: String) {
        if (!exitRequested) updateState { copy(plmnMcc = numeric.take(3), plmnMnc = numeric.drop(3)) }
    }

    fun lockPlmn() = launchAction { modem.lockPlmn(ui.plmnMcc, ui.plmnMnc) }

    fun unlockPlmn() = launchAction { modem.unlockPlmn() }

    fun unlockEverything() = launchAction { modem.unlockEverything() }

    fun radioCycle() = launchAction { modem.radioCycle() }

    fun reattach() = launchAction { modem.reattach() }

    fun rebootModem() =
        launchAction(refreshAfter = false) {
            val message = modem.rebootModem()
            delay(12.seconds)
            var attempt = 0
            while (attempt < 3) {
                if (attempt > 0) delay(4.seconds)
                attempt++
                if (!modem.helperRunning) {
                    modem.reconnect()
                    delay(3.seconds)
                }
                if (modem.helperRunning) {
                    modem.refreshNow()
                    break
                }
            }
            message
        }

    fun startWatchdog() {
        if (exitRequested) return
        if (modem.state.value.simSelectionRequired) {
            _events.tryEmit("Select an active SIM again before using modem controls.")
            return
        }
        try {
            Watchdog.start(getApplication()) {
                val modemState = modem.state.value
                if (modemState.simSelectionRequired) {
                    -1
                } else {
                    modemState.sims.firstOrNull { it.slot == modemState.sim - 1 }?.subId ?: -1
                }
            }
        } catch (e: Exception) {
            _events.tryEmit(reason(e))
        }
    }

    fun stopWatchdog() = Watchdog.stop(getApplication())

    fun samsungMenu() = launchAction(refreshAfter = false) { modem.samsungMenu() }

    fun sendAt(command: String) =
        launchAction(refreshAfter = false) {
            val reply = modem.sendAt(command)
            updateState { copy(toolOutput = "> $command\n$reply\n\n$toolOutput".take(MAX_TOOL_OUTPUT_CHARS)) }
            "The modem replied. See the output below.".also(modem::log)
        }

    fun sendQmi(
        service: Int,
        msg: Int,
        payload: String,
    ) = launchAction(refreshAfter = false) {
        val response = modem.sendQmi(service, msg, payload)
        val entry = "> svc $service msg 0x%04X %s\n%s".format(msg, payload, response.toString(2))
        updateState { copy(toolOutput = "$entry\n\n$toolOutput".take(MAX_TOOL_OUTPUT_CHARS)) }
        "Sent message 0x%04X. The modem replied: %s.".format(msg, response.optString("error")).also(modem::log)
    }

    fun clearOutput() {
        if (!exitRequested) updateState { copy(toolOutput = "") }
    }

    fun copyDiagnostics() {
        if (exitRequested) return
        val title = listOfNotNull("bunnylock", appVersion(), "diagnostics").joinToString(" ")
        val text = modem.diagnostics(title, listOf("Shizuku: ${ShizukuClient.describe(getApplication())}"))
        val clipboard = getApplication<Application>().getSystemService(ClipboardManager::class.java)
        // Kotlin sees primaryClip as read-only because its getter is nullable and its setter isn't.
        @Suppress("UsePropertyAccessSyntax")
        clipboard.setPrimaryClip(ClipData.newPlainText("bunnylock diagnostics", text))
        _events.tryEmit("Diagnostics copied to the clipboard.")
    }
}
