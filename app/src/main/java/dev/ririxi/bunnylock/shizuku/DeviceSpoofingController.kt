package dev.ririxi.bunnylock.shizuku

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class DeviceSpoofingResult { IDLE, LOADING, SUCCESS, ERROR, UNSUPPORTED, UNVERIFIED }

enum class DeviceSpoofingShizukuStatus { NOT_CHECKED, CHECKING, CONNECTED, NOT_INSTALLED, NOT_RUNNING, PERMISSION_DENIED, DISCONNECTED, ERROR }

enum class DeviceSpoofingProblem { NONE, SHIZUKU_NOT_INSTALLED, SHIZUKU_NOT_RUNNING, PERMISSION_DENIED, CONNECTION_LOST, OTHER }

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
)

/** ينفذ فحص هوية للقراءة فقط؛ لا يحاول كتابة خصائص ro.* غير القابلة للاستعادة وقت التشغيل. */
class DeviceSpoofingController(context: Context) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private val _state = MutableStateFlow(
        DeviceSpoofingState().let { initial ->
            val (currentRate, supportedRates) = displayRates()
            initial.copy(currentRefreshRate = currentRate, supportedRefreshRates = supportedRates)
        },
    )
    val state: StateFlow<DeviceSpoofingState> = _state.asStateFlow()

    suspend fun testProfile() {
        if (!mutex.tryLock()) return
        val client = ShizukuClient(appContext) {
            _state.update { it.copy(shizukuStatus = DeviceSpoofingShizukuStatus.DISCONNECTED) }
        }
        _state.update {
            it.copy(
                isOn = false,
                checking = true,
                result = DeviceSpoofingResult.LOADING,
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
            val identity = withContext(Dispatchers.IO) { client.call("deviceSpoof.inspect") }
            if (!ShizukuClient.granted()) {
                fail(DeviceSpoofingShizukuStatus.DISCONNECTED, DeviceSpoofingProblem.CONNECTION_LOST)
                return
            }
            val properties = identity.optJSONObject("properties").toStringMap()
            val buildValues =
                linkedMapOf(
                    "Build.MODEL" to Build.MODEL.orEmpty(),
                    "Build.BRAND" to Build.BRAND.orEmpty(),
                    "Build.MANUFACTURER" to Build.MANUFACTURER.orEmpty(),
                )
            val propertiesReadable = listOf("ro.product.model", "ro.product.brand", "ro.product.manufacturer").all { !properties[it].isNullOrBlank() }
            val (currentRate, supportedRates) = displayRates()

            _state.update {
                it.copy(
                    isOn = false,
                    result = if (propertiesReadable) DeviceSpoofingResult.UNSUPPORTED else DeviceSpoofingResult.UNVERIFIED,
                    shizukuStatus = DeviceSpoofingShizukuStatus.CONNECTED,
                    problem = DeviceSpoofingProblem.NONE,
                    originalProperties = properties,
                    appBuildValues = buildValues,
                    serviceUid = identity.optInt("uid", hello.optInt("uid", -1)).takeIf { uid -> uid >= 0 },
                    currentRefreshRate = currentRate,
                    supportedRefreshRates = supportedRates,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Device identity inspection failed", e)
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
            _state.update { it.copy(isOn = false, result = DeviceSpoofingResult.ERROR, shizukuStatus = status, problem = problem) }
        } finally {
            client.stop()
            _state.update { it.copy(checking = false) }
            mutex.unlock()
        }
    }

    fun disable() {
        // لم تُكتب أي قيمة للنظام، لذلك لا توجد استعادة أو قيمة افتراضية لتطبيقها.
        _state.update { it.copy(isOn = false) }
    }

    fun refreshShizukuStatus() {
        if (_state.value.result == DeviceSpoofingResult.IDLE || _state.value.checking) return
        val status = when {
            ShizukuClient.installedVersion(appContext) == null -> DeviceSpoofingShizukuStatus.NOT_INSTALLED
            !ShizukuClient.granted() -> DeviceSpoofingShizukuStatus.DISCONNECTED
            else -> DeviceSpoofingShizukuStatus.CONNECTED
        }
        _state.update { it.copy(shizukuStatus = status) }
    }

    private fun fail(status: DeviceSpoofingShizukuStatus, problem: DeviceSpoofingProblem) {
        _state.update { it.copy(result = DeviceSpoofingResult.ERROR, shizukuStatus = status, problem = problem) }
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

    private companion object {
        const val TAG = "DeviceSpoofing"
    }
}