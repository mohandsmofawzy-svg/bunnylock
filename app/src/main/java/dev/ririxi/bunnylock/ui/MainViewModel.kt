package dev.ririxi.bunnylock.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.ririxi.bunnylock.R
import dev.ririxi.bunnylock.core.Bands
import dev.ririxi.bunnylock.data.Cell
import dev.ririxi.bunnylock.data.DynamicPlmnService
import dev.ririxi.bunnylock.data.LockNotifier
import dev.ririxi.bunnylock.data.ProfileStore
import dev.ririxi.bunnylock.data.Rat
import dev.ririxi.bunnylock.data.Updates
import dev.ririxi.bunnylock.modem.Backend
import dev.ririxi.bunnylock.modem.BandGroupState
import dev.ririxi.bunnylock.modem.CellLockRequest
import dev.ririxi.bunnylock.modem.Group
import dev.ririxi.bunnylock.modem.LockRat
import dev.ririxi.bunnylock.modem.ModemController
import dev.ririxi.bunnylock.modem.ModemState
import dev.ririxi.bunnylock.modem.NetMode
import dev.ririxi.bunnylock.modem.NrMode
import dev.ririxi.bunnylock.modem.RootState
import dev.ririxi.bunnylock.modem.Text
import dev.ririxi.bunnylock.modem.asSentence
import dev.ririxi.bunnylock.modem.optText
import dev.ririxi.bunnylock.modem.reason
import dev.ririxi.bunnylock.modem.string
import dev.ririxi.bunnylock.shizuku.RootOrShizuku
import dev.ririxi.bunnylock.shizuku.ShizukuClient
import dev.ririxi.bunnylock.shizuku.DeviceSpoofingController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

private const val MAX_TOOL_OUTPUT_CHARS = 8000

/** ينسق حالة الواجهة مع ModemController ضمن نطاق ViewModel قابل للإلغاء عند إنهاء الشاشة. */
class MainViewModel(
    private val app: Application,
) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("bunnylock", Context.MODE_PRIVATE)
    private val deviceSpoofing = DeviceSpoofingController(app)
    private val gameModePreferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "gameModeEnabled") {
            val enabled = prefs.getBoolean("gameModeEnabled", false)
            modem.setGameModeEnabled(enabled)
            updateState { copy(gameModeEnabled = enabled) }
        }
    }
    private val dynamicPlmnPreferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key?.startsWith("dynamicPlmn") == true) syncDynamicPlmnStatus()
    }

    private val _ui =
        MutableStateFlow(
            UiState(
                checkUpdates = prefs.getBoolean("checkUpdates", true),
                favoriteBands = loadFavoriteBands(),
                profiles = ProfileStore.load(app),
                gameModeEnabled = prefs.getBoolean("gameModeEnabled", false),
                dynamicPlmn = DynamicPlmnService.status(app),
            ),
        )

    private val ui get() = _ui.value

    private inline fun updateState(transform: UiState.() -> UiState) = _ui.update(transform)

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)

    private var pendingSystemAction: String? = null

    private val modem =
        ModemController(app, viewModelScope, RootOrShizuku(app, ::shizukuLost), onConnected = ::runPendingSystemAction, onRefreshed = ::refreshed)

    // Shizuku hands over its binder a moment after the app starts, or whenever it is started later, so a failed attempt is redone then.
    private val stopWatchingShizuku = ShizukuClient.onBinderReceived(modem::reconnect)

    val state =
        combine(modem.state, _ui, deviceSpoofing.state) { modemState, uiState, spoofingState ->
            uiState.copy(modem = modemState, deviceSpoofing = spoofingState)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ui.copy(modem = modem.state.value, deviceSpoofing = deviceSpoofing.state.value))

    val events = merge(_events, modem.events)

    init {
        prefs.registerOnSharedPreferenceChangeListener(gameModePreferenceListener)
        prefs.registerOnSharedPreferenceChangeListener(dynamicPlmnPreferenceListener)
        modem.connect()
        checkForUpdate()
    }

    private fun appVersion(): String? = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull()

    private fun checkForUpdate() =
        viewModelScope.launch {
            if (!ui.checkUpdates) return@launch
            val installed = appVersion() ?: return@launch
            try {
                val release = Updates.newerThan(installed)?.takeIf { it.version != prefs.getString("skippedUpdate", null) }
                if (ui.checkUpdates) updateState { copy(update = release) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                modem.log("Couldn't check for updates. ${e.message?.takeIf { it.isNotBlank() }?.asSentence() ?: "Something went wrong."}")
            }
        }

    fun dismissUpdate() = updateState { copy(update = null) }

    fun skipUpdate() {
        ui.update?.let { prefs.edit().putString("skippedUpdate", it.version).apply() }
        dismissUpdate()
    }

    fun setCheckUpdates(enabled: Boolean) {
        prefs.edit().putBoolean("checkUpdates", enabled).apply()
        updateState { copy(checkUpdates = enabled) }
        if (enabled) checkForUpdate() else dismissUpdate()
    }

    /** يفك مستمع التفضيلات وShizuku ويوقف المساعد قبل انتهاء نطاق ViewModel. */
    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(gameModePreferenceListener)
        prefs.unregisterOnSharedPreferenceChangeListener(dynamicPlmnPreferenceListener)
        stopWatchingShizuku()
        modem.close()
    }

    fun connect() = modem.connect()

    private var shownLocales = app.resources.configuration.locales

    /** Coming back from the Shizuku app after allowing bunnylock there shouldn't need a tap on "Try again". */
    fun resumed() {
        syncDynamicPlmnStatus()
        deviceSpoofing.refreshShizukuStatus()
        if (modem.state.value.root is RootState.Denied && ShizukuClient.granted()) modem.connect()
        val locales = app.resources.configuration.locales
        if (locales == shownLocales) return
        shownLocales = locales
        if (modem.state.value.root == RootState.Granted) refresh()
    }

    private fun shizukuLost() {
        modem.helperLost(Text(R.string.system_shizuku_lost))
    }

    fun refresh() = modem.refresh()

    fun setDynamicPlmnEnabled(enabled: Boolean) {
        if (enabled) {
            try {
                DynamicPlmnService.start(app)
            } catch (e: RuntimeException) {
                DynamicPlmnService.recordStartFailure(app, e)
                _events.tryEmit(e.message ?: app.getString(R.string.dynamic_plmn_start_failed))
            }
        } else {
            DynamicPlmnService.stop(app)
        }
        syncDynamicPlmnStatus()
    }

    fun retryDynamicPlmn() {
        try {
            DynamicPlmnService.retry(app)
        } catch (e: RuntimeException) {
            DynamicPlmnService.recordStartFailure(app, e)
            _events.tryEmit(e.message ?: app.getString(R.string.dynamic_plmn_start_failed))
        }
        syncDynamicPlmnStatus()
    }

    private fun syncDynamicPlmnStatus() {
        val status = DynamicPlmnService.status(app)
        updateState { copy(dynamicPlmn = status) }
    }

    fun setDeviceSpoofing(enabled: Boolean) {
        viewModelScope.launch { deviceSpoofing.setHighRefreshRate(enabled) }
    }

    fun selectDeviceRefreshRate(rate: Float) = deviceSpoofing.selectRefreshRate(rate)

    suspend fun refreshConnection() = modem.refreshConnection()

    fun handleSystemAction(action: String?) {
        if (action == null || action !in setOf(
                LockNotifier.ACTION_UNLOCK_ALL,
                LockNotifier.ACTION_TOGGLE_LOCKS,
                LockNotifier.ACTION_RESTART_RADIO,
                LockNotifier.ACTION_REBOOT_MODEM,
            )
        ) {
            return
        }
        if (modem.state.value.root == RootState.Granted && modem.helperRunning) {
            runSystemAction(action)
        } else {
            pendingSystemAction = action
            modem.reconnect()
        }
    }

    private fun runPendingSystemAction() {
        val action = pendingSystemAction ?: return
        pendingSystemAction = null
        runSystemAction(action)
    }

    private fun runSystemAction(action: String) {
        when (action) {
            LockNotifier.ACTION_UNLOCK_ALL -> unlockEverything()
            LockNotifier.ACTION_TOGGLE_LOCKS -> toggleLocks()
            LockNotifier.ACTION_RESTART_RADIO -> radioCycle()
            LockNotifier.ACTION_REBOOT_MODEM -> rebootModem()
        }
    }

    // A refresh replaces the band groups, so picks that weren't applied start over.
    private fun refreshed(modemState: ModemState) {
        updateState { copy(bandSelections = emptyMap()) }
        updateNotification(modemState)
    }

    /** Lists the locks that hold. One the phone has strayed from is left out. */
    private fun updateNotification(modemState: ModemState) {
        val lines =
            buildList {
                modemState.groups.filter { it.locked && it.stray === null }.forEach { groupState ->
                    val bands =
                        groupState.current
                            .orEmpty()
                            .sorted()
                            .joinToString(" ") { groupState.group.bandLabel(it) }
                    add(app.getString(R.string.system_notify_line_bands, groupState.group.label, bands))
                }
                modemState.plmnLock?.let { add(app.getString(R.string.system_notify_line_network, it)) }
                if (modemState.strayChannel === null) {
                    modemState.lteLock?.let { add(app.getString(R.string.system_notify_line_lte_cell, it)) }
                    modemState.nrLock?.let { add(app.getString(R.string.system_notify_line_nr_cell, it)) }
                    modemState.legacyCellLock?.let { add(app.getString(R.string.system_notify_line_legacy_cell, it)) }
                }
            }
        if (lines.isEmpty()) {
            prefs.edit().remove("lockedSince").apply()
            LockNotifier.cancel(app)
            return
        }
        val lockedSince =
            prefs.getLong("lockedSince", 0L).takeIf { it > 0 }
                ?: System.currentTimeMillis().also { prefs.edit().putLong("lockedSince", it).apply() }
        LockNotifier.show(app, lines, lockedSince)
    }

    fun loadSims() = modem.loadSims()

    fun setSim(sim: Int) = modem.setSim(sim)

    fun setBackend(backend: Backend) = modem.setBackend(backend)

    fun setPersist(enabled: Boolean) = modem.setPersist(enabled)

    fun setGameModeEnabled(enabled: Boolean) {
        modem.setGameModeEnabled(enabled)
    }

    fun setAtNode(node: String) = modem.setAtNode(node)

    // The modem has already logged the outcome.
    /** يشغل إجراء مودم واحدًا ويضبط حالة الانشغال ويحدث الحالة بعد النجاح. */
    private fun launchAction(
        refreshAfter: Boolean = true,
        block: suspend () -> String,
    ) = viewModelScope.launch {
        if (!modem.helperRunning) {
            _events.emit(app.getString(R.string.system_helper_not_running))
            modem.reconnect()
            return@launch
        }
        updateState { copy(busy = true) }
        try {
            _events.emit(block())
            if (refreshAfter) modem.refreshNow()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _events.emit(app.reason(e))
        } finally {
            updateState { copy(busy = false) }
        }
    }

    private fun groupState(group: Group): BandGroupState? = modem.state.value.groups.firstOrNull { it.group == group }

    private fun updateSelection(
        group: Group,
        transform: BandGroupState.(selected: Set<Int>) -> Set<Int>,
    ) {
        val groupState = groupState(group) ?: return
        val selected = groupState.transform(ui.selectedBands(groupState))
        prefs.edit()
            .putString(LockNotifier.bandTargetPreferenceKey(group.name), selected.sorted().joinToString(","))
            .apply()
        updateState { copy(bandSelections = bandSelections + (group to selected)) }
    }

    fun toggleBand(
        group: Group,
        band: Int,
    ) = updateSelection(group) { selected ->
        if (band in selected) selected - band else selected + band
    }

    fun toggleFavorite(
        group: Group,
        band: Int,
    ) {
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
    ) = updateSelection(group) {
        if (all) supported.toSet() else emptySet()
    }

    fun applyGroup(
        group: Group,
        unlock: Boolean = false,
    ) = launchAction {
        if (unlock) {
            modem.unlockBands(group).also {
                prefs.edit().remove(LockNotifier.bandTargetPreferenceKey(group.name)).apply()
            }
        } else {
            val bands = groupState(group)?.let(ui::selectedBands).orEmpty()
            modem.lockBands(group, bands).also {
                prefs.edit().putString(LockNotifier.bandTargetPreferenceKey(group.name), bands.sorted().joinToString(",")).apply()
            }
        }
    }

    fun setNetMode(mode: NetMode) = launchAction { modem.setNetMode(mode) }

    fun setNrMode(mode: NrMode) = launchAction { modem.setNrMode(mode) }

    fun setNrDc(enabled: Boolean) = launchAction { modem.setNrDc(enabled) }

    fun updateForm(transform: CellForm.() -> CellForm) = updateState { copy(form = form.transform()) }

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
    ) = updateState {
        copy(plmnMcc = (mcc ?: plmnMcc).filter(Char::isDigit).take(3), plmnMnc = (mnc ?: plmnMnc).filter(Char::isDigit).take(3))
    }

    fun pickPlmn(numeric: String) = updateState { copy(plmnMcc = numeric.take(3), plmnMnc = numeric.drop(3)) }

    fun lockPlmn() = launchAction { modem.lockPlmn(ui.plmnMcc, ui.plmnMnc) }

    fun unlockPlmn() = launchAction { modem.unlockPlmn() }

    fun unlockEverything() =
        launchAction {
            modem.unlockEverything().also {
                val editor = prefs.edit()
                Group.entries.forEach { group -> editor.remove(LockNotifier.bandTargetPreferenceKey(group.name)) }
                editor.apply()
            }
        }

    fun radioCycle() = launchAction { modem.radioCycle() }

    fun reattach() = launchAction { modem.reattach() }

    fun rebootModem() = launchAction(refreshAfter = false) { modem.rebootModem() }

    fun saveProfile(name: String) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            _events.tryEmit(app.getString(R.string.profiles_name_required))
            return
        }
        val bands =
            buildMap {
                modem.state.value.groups.forEach { groupState ->
                    put(groupState.group, ui.bandSelections[groupState.group] ?: groupState.allowed)
                }
            }
        val cell = ui.form.takeIf(::hasCellFormInput)
        if (bands.isEmpty() && cell == null) {
            _events.tryEmit(app.getString(R.string.profiles_nothing_to_save))
            return
        }
        val profile = LockProfile(UUID.randomUUID().toString(), cleanName, bands, cell)
        val updated = ui.profiles + profile
        ProfileStore.save(app, updated)
        updateState { copy(profiles = updated) }
        _events.tryEmit(app.getString(R.string.profiles_saved, cleanName))
    }

    fun deleteProfile(profile: LockProfile) {
        val updated = ui.profiles.filterNot { it.id == profile.id }
        ProfileStore.save(app, updated)
        updateState { copy(profiles = updated) }
    }

    fun applyProfile(profile: LockProfile) {
        profile.cell?.let { form -> updateState { copy(form = form) } }
        launchAction {
            profile.bands.forEach { (group, bands) ->
                if (bands.isEmpty()) {
                    modem.unlockBands(group)
                    prefs.edit().remove(LockNotifier.bandTargetPreferenceKey(group.name)).apply()
                } else {
                    modem.lockBands(group, bands)
                    prefs.edit().putString(LockNotifier.bandTargetPreferenceKey(group.name), bands.sorted().joinToString(",")).apply()
                }
            }
            if (profile.cell == null) {
                modem.unlockCells()
            } else {
                val form = profile.cell
                modem.lockCells(
                    CellLockRequest(
                        rat = if (modem.state.value.supports5g) form.rat else LockRat.LTE,
                        earfcn = form.lteEarfcn.toLongOrNull(),
                        ltePcis = parseInts(form.ltePcis),
                        nrArfcn = form.nrArfcn.toLongOrNull(),
                        nrPcis = parseInts(form.nrPcis),
                        nrBand = form.nrBand ?: form.nrCandidates.firstOrNull(),
                        nrScs = form.nrScs,
                    ),
                )
            }
            app.getString(R.string.profiles_applied, profile.name)
        }
    }

    /** يرفع الأقفال الحالية أو يطبق أول ملف محفوظ عند عدم وجود قفل. */
    fun toggleLocks() {
        val current = modem.state.value
        if (current.anyBandLock || current.anyCellLock || current.anyPlmnLock) {
            unlockEverything()
            return
        }
        val profile = ui.profiles.firstOrNull()
        if (profile == null) {
            _events.tryEmit(app.getString(R.string.profiles_no_profile_for_tile))
        } else {
            applyProfile(profile)
        }
    }

    private fun hasCellFormInput(form: CellForm) =
        form.lteEarfcn.isNotBlank() || form.ltePcis.isNotBlank() || form.nrArfcn.isNotBlank() || form.nrPcis.isNotBlank()

    fun samsungMenu() = launchAction(refreshAfter = false) { modem.samsungMenu() }

    fun sendAt(command: String) =
        launchAction(refreshAfter = false) {
            val reply = modem.sendAt(command)
            updateState { copy(toolOutput = "> $command\n$reply\n\n$toolOutput".take(MAX_TOOL_OUTPUT_CHARS)) }
            modem.log("The modem replied. See the output below.")
            app.getString(R.string.system_at_replied)
        }

    fun sendQmi(
        service: Int,
        msg: Int,
        payload: String,
    ) = launchAction(refreshAfter = false) {
        val response = modem.sendQmi(service, msg, payload)
        val error = response.optText("error")
        val englishError = error?.let(modem::inEnglish).orEmpty()
        if (error !== null) response.put("error", englishError)
        val entry = "> svc $service msg 0x%04X %s\n%s".format(msg, payload, response.toString(2))
        updateState { copy(toolOutput = "$entry\n\n$toolOutput".take(MAX_TOOL_OUTPUT_CHARS)) }
        modem.log("Sent message 0x%04X. The modem replied: %s.".format(msg, englishError))
        app.getString(R.string.system_qmi_replied, msg, error?.let { app.string(it) }.orEmpty())
    }

    fun clearOutput() = updateState { copy(toolOutput = "") }

    fun copyDiagnostics() {
        val title = listOfNotNull("bunnylock", appVersion(), "diagnostics").joinToString(" ")
        val text = modem.diagnostics(title, listOf("Shizuku: ${ShizukuClient.describe(app)}"))
        val clipboard = app.getSystemService(ClipboardManager::class.java)
        // Kotlin sees primaryClip as read-only because its getter is nullable and its setter isn't.
        @Suppress("UsePropertyAccessSyntax")
        clipboard.setPrimaryClip(ClipData.newPlainText(app.getString(R.string.system_clip_label_diagnostics), text))
        _events.tryEmit(app.getString(R.string.system_diagnostics_copied))
    }
}
