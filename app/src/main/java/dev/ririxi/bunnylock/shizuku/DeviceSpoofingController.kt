package dev.ririxi.bunnylock.shizuku

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Process
import android.view.Display
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class DeviceSpoofingResult { IDLE, LOADING, SUCCESS, ERROR, UNSUPPORTED, UNVERIFIED }

enum class DeviceSpoofingShizukuStatus { NOT_CHECKED, CHECKING, CONNECTED, NOT_INSTALLED, NOT_RUNNING, PERMISSION_DENIED, DISCONNECTED, ERROR }

enum class DeviceSpoofingProblem { NONE, SHIZUKU_NOT_INSTALLED, SHIZUKU_NOT_RUNNING, PERMISSION_DENIED, CONNECTION_LOST, NON_SHELL, OTHER }

enum class DeviceDisplayResult { IDLE, CHECKING, APPLIED, SETTING_ONLY, UNSUPPORTED, ERROR, RESTORED, RESTORE_PENDING }

data class DeviceSpoofingState(
    val isOn: Boolean = false,
    val checking: Boolean = false,
    val result: DeviceSpoofingResult = DeviceSpoofingResult.IDLE,
    val shizukuStatus: DeviceSpoofingShizukuStatus = DeviceSpoofingShizukuStatus.NOT_CHECKED,
    val problem: DeviceSpoofingProblem = DeviceSpoofingProblem.NONE,
    val originalProperties: Map<String, String> = emptyMap(),
    val appBuildValues: Map<String, String> = emptyMap(),
    val serviceUid: Int? = null,
    val currentRefreshRate: Float? = null,
    val supportedRefreshRates: List<Float> = emptyList(),
    val selectedRefreshRate: Float? = null,
    val minRefreshSetting: String? = null,
    val peakRefreshSetting: String? = null,
    val originalMinRefreshSetting: String? = null,
    val originalPeakRefreshSetting: String? = null,
    val displayResult: DeviceDisplayResult = DeviceDisplayResult.IDLE,
    val restorationPending: Boolean = false,
)

/** يفحص الهوية للقراءة فقط ويختبر إعداد معدل الشاشة القابل للتحقق والاستعادة. */
class DeviceSpoofingController(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(
        DeviceSpoofingState().let { initial ->
            val (currentRate, supportedRates) = displayRates()
            val pending = preferences.getBoolean(KEY_ACTIVE, false)
            initial.copy(
                isOn = pending,
                currentRefreshRate = currentRate,
                supportedRefreshRates = supportedRates,
                selectedRefreshRate = selectedRate(currentRate, supportedRates),
                originalMinRefreshSetting = preferences.getString(KEY_ORIGINAL_MIN, null),
                originalPeakRefreshSetting = preferences.getString(KEY_ORIGINAL_PEAK, null),
                displayResult = if (pending) DeviceDisplayResult.RESTORE_PENDING else DeviceDisplayResult.IDLE,
                restorationPending = pending,
            )
        },
    )
    val state: StateFlow<DeviceSpoofingState> = _state.asStateFlow()

    suspend fun setHighRefreshRate(enabled: Boolean) {
        if (!enabled && !preferences.getBoolean(KEY_ACTIVE, false)) {
            val previousResult = state.value.displayResult
            _state.update {
                it.copy(
                    isOn = false,
                    displayResult = if (previousResult == DeviceDisplayResult.APPLIED || previousResult == DeviceDisplayResult.SETTING_ONLY) {
                        DeviceDisplayResult.RESTORED
                    } else {
                        DeviceDisplayResult.IDLE
                    },
                )
            }
            return
        }
        if (!mutex.tryLock()) return
        val client = ShizukuClient(appContext) {
            _state.update { it.copy(shizukuStatus = DeviceSpoofingShizukuStatus.DISCONNECTED) }
        }
        _state.update {
            it.copy(
                isOn = preferences.getBoolean(KEY_ACTIVE, false),
                checking = true,
                result = DeviceSpoofingResult.LOADING,
                displayResult = DeviceDisplayResult.CHECKING,
                shizukuStatus = DeviceSpoofingShizukuStatus.CHECKING,
                problem = DeviceSpoofingProblem.NONE,
                originalProperties = emptyMap(),
                appBuildValues = emptyMap(),
            )
        }
        try {
            if (ShizukuClient.installedVersion(appContext) == null) {
                fail(DeviceSpoofingShizukuStatus.NOT_INSTALLED, DeviceSpoofingProblem.SHIZUKU_NOT_INSTALLED)
                return
            }
            if (!ShizukuClient.awaitBinder()) {
                fail(DeviceSpoofingShizukuStatus.NOT_RUNNING, DeviceSpoofingProblem.SHIZUKU_NOT_RUNNING)
                return
            }

            val hello = client.start()
            val uid = hello.optInt("uid", -1)
            if (uid != Process.SHELL_UID) {
                _state.update {
                    it.copy(
                        isOn = preferences.getBoolean(KEY_ACTIVE, false),
                        result = DeviceSpoofingResult.UNSUPPORTED,
                        displayResult = DeviceDisplayResult.UNSUPPORTED,
                        shizukuStatus = DeviceSpoofingShizukuStatus.CONNECTED,
                        problem = DeviceSpoofingProblem.NON_SHELL,
                        serviceUid = uid.takeIf { value -> value >= 0 },
                    )
                }
                return
            }

            val identity =
                try {
                    withContext(Dispatchers.IO) { client.call("deviceSpoof.inspect") }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Could not read system identity properties", e)
                    null
                }
            val properties = identity?.optJSONObject("properties").toStringMap()
            val buildValues =
                linkedMapOf(
                    "Build.MODEL" to Build.MODEL.orEmpty(),
                    "Build.BRAND" to Build.BRAND.orEmpty(),
                    "Build.MANUFACTURER" to Build.MANUFACTURER.orEmpty(),
                    "Build.VERSION.SDK_INT" to Build.VERSION.SDK_INT.toString(),
                )
            val propertiesReadable = listOf("ro.product.model", "ro.product.brand", "ro.product.manufacturer").all { !properties[it].isNullOrBlank() }
            val (currentRate, supportedRates) = displayRates()
            val displaySettings = withContext(Dispatchers.IO) { client.call("deviceSpoof.display.read") }
            val minSetting = displaySettings.optString("minRefreshRate", "null")
            val peakSetting = displaySettings.optString("peakRefreshRate", "null")

            if (!ShizukuClient.granted()) {
                fail(DeviceSpoofingShizukuStatus.DISCONNECTED, DeviceSpoofingProblem.CONNECTION_LOST)
                return
            }
            _state.update {
                it.copy(
                    isOn = preferences.getBoolean(KEY_ACTIVE, false),
                    result = if (propertiesReadable) DeviceSpoofingResult.UNSUPPORTED else DeviceSpoofingResult.UNVERIFIED,
                    shizukuStatus = DeviceSpoofingShizukuStatus.CONNECTED,
                    problem = DeviceSpoofingProblem.NONE,
                    originalProperties = properties,
                    appBuildValues = buildValues,
                    serviceUid = uid,
                    currentRefreshRate = currentRate,
                    supportedRefreshRates = supportedRates,
                    selectedRefreshRate = state.value.selectedRefreshRate?.takeIf { target -> supportedRates.any { it.closeTo(target) } }
                        ?: selectedRate(currentRate, supportedRates),
                    minRefreshSetting = minSetting,
                    peakRefreshSetting = peakSetting,
                    originalMinRefreshSetting = preferences.getString(KEY_ORIGINAL_MIN, null),
                    originalPeakRefreshSetting = preferences.getString(KEY_ORIGINAL_PEAK, null),
                    restorationPending = preferences.getBoolean(KEY_ACTIVE, false),
                )
            }

            if (!enabled) {
                restoreOriginalPeakRate(client)
                return
            }

            val targetRate = state.value.selectedRefreshRate
            if (targetRate == null || supportedRates.none { it.closeTo(targetRate) }) {
                _state.update { it.copy(isOn = false, displayResult = DeviceDisplayResult.UNSUPPORTED) }
                return
            }
            if (!canRestoreSetting(minSetting) || !canRestoreSetting(peakSetting)) {
                _state.update { it.copy(isOn = false, displayResult = DeviceDisplayResult.UNSUPPORTED) }
                return
            }
            if (preferences.getBoolean(KEY_ACTIVE, false)) {
                _state.update { it.copy(isOn = true, displayResult = DeviceDisplayResult.RESTORE_PENDING) }
                return
            }

            val saved = preferences.edit()
                .putString(KEY_ORIGINAL_MIN, minSetting)
                .putString(KEY_ORIGINAL_PEAK, peakSetting)
                .putBoolean(KEY_ACTIVE, true)
                .commit()
            if (!saved) throw IllegalStateException("Could not save the original display setting")

            val applied = withContext(Dispatchers.IO) {
                client.call("deviceSpoof.display.setRate") { put("rate", targetRate.toDouble()) }
            }
            val appliedMin = applied.optString("minRefreshRate", "null")
            val appliedPeak = applied.optString("peakRefreshRate", "null")
            _state.update {
                it.copy(
                    isOn = true,
                    restorationPending = true,
                    minRefreshSetting = appliedMin,
                    peakRefreshSetting = appliedPeak,
                    originalMinRefreshSetting = minSetting,
                    originalPeakRefreshSetting = peakSetting,
                )
            }
            if (!sameSetting(appliedMin, targetRate.toString()) || !sameSetting(appliedPeak, targetRate.toString())) {
                restoreOriginalPeakRate(client)
                _state.update {
                    it.copy(displayResult = if (it.restorationPending) DeviceDisplayResult.RESTORE_PENDING else DeviceDisplayResult.ERROR)
                }
                return
            }

            var observedRate: Float? = null
            for (attempt in 0 until 8) {
                observedRate = displayRates().first
                if (observedRate?.closeTo(targetRate) == true) break
                if (attempt < 7) delay(400)
            }
            _state.update {
                it.copy(
                    isOn = true,
                    displayResult = if (observedRate?.closeTo(targetRate) == true) DeviceDisplayResult.APPLIED else DeviceDisplayResult.SETTING_ONLY,
                    currentRefreshRate = observedRate ?: it.currentRefreshRate,
                )
            }
        } catch (e: CancellationException) {
            val pending = preferences.getBoolean(KEY_ACTIVE, false)
            _state.update {
                it.copy(
                    isOn = pending,
                    result = if (pending) it.result else DeviceSpoofingResult.ERROR,
                    displayResult = if (pending) DeviceDisplayResult.RESTORE_PENDING else DeviceDisplayResult.ERROR,
                    restorationPending = pending,
                )
            }
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "High refresh rate test failed", e)
            val status =
                when {
                    ShizukuClient.installedVersion(appContext) == null -> DeviceSpoofingShizukuStatus.NOT_INSTALLED
                    !ShizukuClient.awaitBinder() -> DeviceSpoofingShizukuStatus.NOT_RUNNING
                    !ShizukuClient.granted() -> DeviceSpoofingShizukuStatus.PERMISSION_DENIED
                    else -> DeviceSpoofingShizukuStatus.ERROR
                }
            val problem = when (status) {
                DeviceSpoofingShizukuStatus.NOT_INSTALLED -> DeviceSpoofingProblem.SHIZUKU_NOT_INSTALLED
                DeviceSpoofingShizukuStatus.NOT_RUNNING -> DeviceSpoofingProblem.SHIZUKU_NOT_RUNNING
                DeviceSpoofingShizukuStatus.PERMISSION_DENIED -> DeviceSpoofingProblem.PERMISSION_DENIED
                DeviceSpoofingShizukuStatus.DISCONNECTED -> DeviceSpoofingProblem.CONNECTION_LOST
                else -> DeviceSpoofingProblem.OTHER
            }
            val pending = preferences.getBoolean(KEY_ACTIVE, false)
            _state.update {
                it.copy(
                    isOn = pending,
                    result = if (status == DeviceSpoofingShizukuStatus.ERROR) DeviceSpoofingResult.ERROR else it.result,
                    displayResult = if (pending) DeviceDisplayResult.RESTORE_PENDING else DeviceDisplayResult.ERROR,
                    shizukuStatus = status,
                    problem = problem,
                    restorationPending = pending,
                )
            }
        } finally {
            client.stop()
            _state.update { it.copy(checking = false) }
            mutex.unlock()
        }
    }

    private suspend fun restoreOriginalPeakRate(client: ShizukuClient) {
        if (!preferences.getBoolean(KEY_ACTIVE, false)) {
            _state.update { it.copy(isOn = false, displayResult = DeviceDisplayResult.RESTORED, restorationPending = false) }
            return
        }
        val originalMin = preferences.getString(KEY_ORIGINAL_MIN, null)
            ?: throw IllegalStateException("Saved original minimum display setting is missing")
        val originalPeak = preferences.getString(KEY_ORIGINAL_PEAK, null)
            ?: throw IllegalStateException("Saved original peak display setting is missing")
        val restored = withContext(Dispatchers.IO) {
            client.call("deviceSpoof.display.restoreRate") {
                put("minOriginal", originalMin)
                put("peakOriginal", originalPeak)
            }
        }
        val restoredMin = restored.optString("minRefreshRate", "null")
        val restoredPeak = restored.optString("peakRefreshRate", "null")
        if (!sameSetting(restoredMin, originalMin) || !sameSetting(restoredPeak, originalPeak)) {
            _state.update { it.copy(isOn = true, displayResult = DeviceDisplayResult.RESTORE_PENDING, restorationPending = true) }
            return
        }
        val cleared = preferences.edit().remove(KEY_ORIGINAL_MIN).remove(KEY_ORIGINAL_PEAK).putBoolean(KEY_ACTIVE, false).commit()
        _state.update {
            it.copy(
                isOn = !cleared,
                displayResult = if (cleared) DeviceDisplayResult.RESTORED else DeviceDisplayResult.RESTORE_PENDING,
                restorationPending = !cleared,
                originalMinRefreshSetting = if (cleared) null else originalMin,
                originalPeakRefreshSetting = if (cleared) null else originalPeak,
                minRefreshSetting = restoredMin,
                peakRefreshSetting = restoredPeak,
                currentRefreshRate = displayRates().first ?: it.currentRefreshRate,
            )
        }
    }

    fun selectRefreshRate(rate: Float) {
        if (state.value.checking || state.value.isOn) return
        if (state.value.supportedRefreshRates.any { it.closeTo(rate) }) {
            _state.update { it.copy(selectedRefreshRate = rate) }
        }
    }

    fun refreshShizukuStatus() {
        if (_state.value.checking) return
        val status = when {
            ShizukuClient.installedVersion(appContext) == null -> DeviceSpoofingShizukuStatus.NOT_INSTALLED
            !ShizukuClient.granted() -> DeviceSpoofingShizukuStatus.DISCONNECTED
            else -> DeviceSpoofingShizukuStatus.CONNECTED
        }
        _state.update { it.copy(shizukuStatus = status) }
    }

    private fun fail(status: DeviceSpoofingShizukuStatus, problem: DeviceSpoofingProblem) {
        val pending = preferences.getBoolean(KEY_ACTIVE, false)
        _state.update {
            it.copy(
                isOn = pending,
                result = DeviceSpoofingResult.ERROR,
                displayResult = if (pending) DeviceDisplayResult.RESTORE_PENDING else DeviceDisplayResult.ERROR,
                shizukuStatus = status,
                problem = problem,
                restorationPending = pending,
            )
        }
    }

    private fun JSONObject?.toStringMap(): Map<String, String> {
        if (this == null) return emptyMap()
        return keys().asSequence().associateWith { optString(it) }
    }

    private fun displayRates(): Pair<Float?, List<Float>> =
        try {
            val manager = appContext.getSystemService(DisplayManager::class.java)
            val display = manager?.getDisplay(Display.DEFAULT_DISPLAY)
            val current = display?.refreshRate?.takeIf { it > 0f }
            val supported = display?.supportedModes.orEmpty().map { it.refreshRate }.filter { it > 0f }.distinct().sorted()
            current to supported
        } catch (_: RuntimeException) {
            null to emptyList()
        }

    private fun selectedRate(currentRate: Float?, supportedRates: List<Float>): Float? =
        supportedRates.lastOrNull { currentRate == null || it > currentRate + RATE_EPSILON } ?: supportedRates.maxOrNull()

    private fun Float.closeTo(other: Float): Boolean = kotlin.math.abs(this - other) < RATE_EPSILON

    private fun sameSetting(actual: String, expected: String): Boolean {
        if (actual == "null" || expected == "null") return actual == expected
        val actualRate = actual.toFloatOrNull() ?: return false
        val expectedRate = expected.toFloatOrNull() ?: return false
        return actualRate.closeTo(expectedRate)
    }

    private fun canRestoreSetting(value: String): Boolean =
        value == "null" || value.toDoubleOrNull()?.let { it.isFinite() && it in 0.0..1000.0 } == true

    private companion object {
        const val TAG = "DeviceSpoofing"
        const val PREFERENCES = "device_display_rate"
        const val KEY_ACTIVE = "active"
        const val KEY_ORIGINAL_MIN = "original_min_refresh_rate"
        const val KEY_ORIGINAL_PEAK = "original_peak_refresh_rate"
        const val RATE_EPSILON = 0.6f
    }
}