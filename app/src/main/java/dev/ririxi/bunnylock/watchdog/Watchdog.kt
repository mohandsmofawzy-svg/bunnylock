package dev.ririxi.bunnylock.watchdog

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ProbeResult(
    val name: String,
    val ok: Boolean,
    val rttMs: Long?,
    val detail: String,
)

enum class WatchdogVerdict(
    val label: String,
) {
    IDLE("Idle"),
    OK("Internet reachable"),
    HIGH_LATENCY("High latency on every probe"),
    PARTIAL_PATH("Some probes failed; likely routing or a remote server"),
    ALL_PROBES_FAILED("Registered, but every probe failed"),
    NO_REGISTRATION("No cellular registration"),
    NO_DEFAULT_NETWORK("No default network"),
    UNVALIDATED("Default network is not validated yet"),
    CAPTIVE_PORTAL("Captive portal"),
    WIFI_DEFAULT("Default network is Wi‑Fi"),
}

data class WatchdogSample(
    val time: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val transport: String,
    val validated: Boolean?,
    val captive: Boolean,
    val registeredPlmn: String?,
    val roaming: Boolean?,
    val rsrp: Int?,
    val rsrq: Int? = null,
    val sinr: Int? = null,
    val rat: String? = null,
    val avgRttMs: Long? = null,
    val minRttMs: Long? = null,
    val maxRttMs: Long? = null,
    val jitterMs: Long? = null,
    val successfulProbes: Int = 0,
    val failedProbes: Int = 0,
    val probeFailureRatePercent: Int = 0,
    val probes: List<ProbeResult>,
    val verdict: WatchdogVerdict,
)

data class WatchdogState(
    val running: Boolean = false,
    val starting: Boolean = false,
    val verdict: WatchdogVerdict = WatchdogVerdict.IDLE,
    val lastSample: WatchdogSample? = null,
    val log: List<String> = emptyList(),
    val error: String? = null,
)

private const val MAX_LOG_LINES = 80

/**
 * Observe-only network monitor. The service owns the engine; UI reads [state].
 * Never issues modem or radio commands.
 */
object Watchdog {
    internal val mutable = MutableStateFlow(WatchdogState())
    val state = mutable.asStateFlow()

    @Volatile
    internal var subscriptionId: () -> Int = { -1 }

    fun start(
        context: Context,
        subscriptionId: () -> Int,
    ) {
        this.subscriptionId = subscriptionId
        mutable.update { it.copy(starting = true, error = null) }
        val intent = Intent(context, WatchdogService::class.java)
        context.startForegroundService(intent)
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, WatchdogService::class.java))
        subscriptionId = { -1 }
        mutable.update { it.copy(running = false, starting = false, verdict = WatchdogVerdict.IDLE) }
    }

    internal fun appendLog(line: String) {
        mutable.update { current ->
            current.copy(log = (listOf(line) + current.log).take(MAX_LOG_LINES))
        }
    }
}
