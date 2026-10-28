package dev.ririxi.bunnylock.watchdog

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import dev.ririxi.bunnylock.data.Carriers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

private val SAMPLE_GAP = 5.seconds
private val PROBE_TIMEOUT = 4.seconds
private const val SLOW_RTT_MS = 400L

private data class ProbeTarget(
    val name: String,
    val kind: Kind,
    val host: String,
    val port: Int = 443,
    val url: String? = null,
) {
    enum class Kind { TCP, HTTPS }
}

/** Independent targets: two IP/TCP paths plus one HTTPS hostname (includes DNS). */
private val TARGETS =
    listOf(
        ProbeTarget("tcp 1.1.1.1", ProbeTarget.Kind.TCP, "1.1.1.1"),
        ProbeTarget("tcp 8.8.8.8", ProbeTarget.Kind.TCP, "8.8.8.8"),
        ProbeTarget(
            "https gstatic",
            ProbeTarget.Kind.HTTPS,
            "www.gstatic.com",
            url = "https://www.gstatic.com/generate_204",
        ),
        ProbeTarget(
            "https cloudflare",
            ProbeTarget.Kind.HTTPS,
            "cp.cloudflare.com",
            url = "https://cp.cloudflare.com/generate_204",
        ),
    )

internal class WatchdogEngine(
    context: Context,
) {
    private val app = context.applicationContext
    private val connectivity = checkNotNull(app.getSystemService(ConnectivityManager::class.java))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null
    private val lock = Any()

    @Volatile
    private var lastAvgRttMs: Double? = null

    @Volatile
    private var jitterEstimateMs: Double? = null

    @Volatile
    private var defaultNetwork: Network? = null

    @Volatile
    private var defaultCaps: NetworkCapabilities? = null

    private val callback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                defaultNetwork = network
                defaultCaps = connectivity.getNetworkCapabilities(network)
            }

            override fun onLost(network: Network) {
                if (defaultNetwork == network) {
                    defaultNetwork = null
                    defaultCaps = null
                }
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                if (defaultNetwork == null || defaultNetwork == network) {
                    defaultNetwork = network
                    defaultCaps = networkCapabilities
                }
            }
        }

    fun start() {
        synchronized(lock) {
            if (loop?.isActive == true) return
            runCatching { connectivity.registerDefaultNetworkCallback(callback) }
            defaultNetwork = connectivity.activeNetwork
            defaultCaps = defaultNetwork?.let { connectivity.getNetworkCapabilities(it) }
            loop = scope.launch { runLoop() }
        }
    }

    fun stop() {
        synchronized(lock) {
            loop?.cancel()
            loop = null
            runCatching { connectivity.unregisterNetworkCallback(callback) }
            defaultNetwork = null
            defaultCaps = null
            lastAvgRttMs = null
            jitterEstimateMs = null
        }
        Watchdog.mutable.value =
            Watchdog.mutable.value.copy(running = false, starting = false, verdict = WatchdogVerdict.IDLE)
    }

    private suspend fun runLoop() {
        Watchdog.mutable.value =
            Watchdog.mutable.value.copy(running = true, starting = false, error = null)
        Watchdog.appendLog("${now()}  Watchdog started (observe only).")
        try {
            while (currentCoroutineContext().isActive) {
                val sample =
                    try {
                        sampleOnce()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        errorSample(e)
                    }
                val line = formatLog(sample)
                Watchdog.appendLog(line)
                Watchdog.mutable.value =
                    Watchdog.mutable.value.copy(
                        running = true,
                        starting = false,
                        verdict = sample.verdict,
                        lastSample = sample,
                        error = null,
                    )
                delay(SAMPLE_GAP)
            }
        } catch (_: CancellationException) {
            // stopped
        } finally {
            Watchdog.appendLog("${now()}  Watchdog stopped.")
        }
    }

    private suspend fun sampleOnce(): WatchdogSample {
        val network = defaultNetwork ?: connectivity.activeNetwork
        val caps = defaultCaps ?: network?.let { connectivity.getNetworkCapabilities(it) }
        val radio = radioSnapshot()
        val probes =
            if (network == null) {
                emptyList()
            } else {
                coroutineScope {
                    TARGETS.map { target ->
                        async { probe(network, target) }
                    }.map { it.await() }
                }
            }

        val successful = probes.filter { it.ok }
        val failed = probes.filter { !it.ok }
        val totalProbes = probes.size
        val successfulProbes = successful.size
        val failedProbes = failed.size
        val probeFailureRatePercent = if (totalProbes > 0) (failedProbes * 100) / totalProbes else 0

        val rttValues = successful.mapNotNull { it.rttMs }
        val minRttMs = rttValues.minOrNull()
        val maxRttMs = rttValues.maxOrNull()
        val avgRttDouble = if (rttValues.isNotEmpty()) rttValues.average() else null
        val avgRttMs = avgRttDouble?.toLong()

        val jitterMs: Long?
        if (avgRttDouble != null) {
            val prevAvg = lastAvgRttMs
            if (prevAvg != null) {
                val diff = kotlin.math.abs(avgRttDouble - prevAvg)
                val prevJitter = jitterEstimateMs
                val updatedJitter = if (prevJitter == null) diff else (prevJitter + (diff - prevJitter) * 0.125)
                jitterEstimateMs = updatedJitter
                jitterMs = updatedJitter.toLong()
            } else {
                jitterEstimateMs = null
                jitterMs = null
            }
            lastAvgRttMs = avgRttDouble
        } else {
            lastAvgRttMs = null
            jitterEstimateMs = null
            jitterMs = null
        }

        val verdict = classify(caps, radio.registeredPlmn, probes)
        return WatchdogSample(
            time = now(),
            timestampMs = System.currentTimeMillis(),
            transport = transportLabel(caps),
            validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            captive = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) == true,
            registeredPlmn = radio.registeredPlmn,
            roaming = radio.roaming,
            rsrp = radio.rsrp,
            rsrq = radio.rsrq,
            sinr = radio.sinr,
            rat = radio.rat,
            avgRttMs = avgRttMs,
            minRttMs = minRttMs,
            maxRttMs = maxRttMs,
            jitterMs = jitterMs,
            successfulProbes = successfulProbes,
            failedProbes = failedProbes,
            probeFailureRatePercent = probeFailureRatePercent,
            probes = probes,
            verdict = verdict,
        )
    }

    private fun errorSample(error: Throwable): WatchdogSample =
        WatchdogSample(
            time = now(),
            timestampMs = System.currentTimeMillis(),
            transport = "unknown",
            validated = null,
            captive = false,
            registeredPlmn = null,
            roaming = null,
            rsrp = null,
            rsrq = null,
            sinr = null,
            rat = null,
            avgRttMs = null,
            minRttMs = null,
            maxRttMs = null,
            jitterMs = null,
            successfulProbes = 0,
            failedProbes = 0,
            probeFailureRatePercent = 0,
            probes = emptyList(),
            verdict = WatchdogVerdict.NO_DEFAULT_NETWORK,
        ).also {
            Watchdog.appendLog("${now()}  Sample failed: ${error.message ?: error.javaClass.simpleName}")
        }

    private data class RadioSnapshot(
        val registeredPlmn: String?,
        val roaming: Boolean?,
        val rsrp: Int?,
        val rsrq: Int?,
        val sinr: Int?,
        val rat: String?,
    )

    private fun radioSnapshot(): RadioSnapshot {
        if (app.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return RadioSnapshot(null, null, null, null, null, null)
        }
        val subId = Watchdog.subscriptionId()
        val defaultTelephony = app.getSystemService(TelephonyManager::class.java) ?: return RadioSnapshot(null, null, null, null, null, null)
        val telephony =
            if (subId >= 0 && subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                defaultTelephony.createForSubscriptionId(subId)
            } else {
                defaultTelephony
            }
        val registered = telephony.networkOperator?.takeIf { it.length >= 5 }
        val roaming = runCatching { telephony.isNetworkRoaming }.getOrNull()
        val signals = Carriers.signals(app, subId)
        val primarySignal = signals.maxByOrNull { it.rsrp ?: Int.MIN_VALUE }
        val rsrp = primarySignal?.rsrp
        val rsrq = primarySignal?.rsrq
        val sinr = primarySignal?.sinr
        val rat = primarySignal?.rat?.name
        return RadioSnapshot(registered, roaming, rsrp, rsrq, sinr, rat)
    }

    private suspend fun probe(
        network: Network,
        target: ProbeTarget,
    ): ProbeResult =
        withContext(Dispatchers.IO) {
            val started = System.nanoTime()
            try {
                withTimeout(PROBE_TIMEOUT) {
                    when (target.kind) {
                        ProbeTarget.Kind.TCP -> tcpProbe(network, target.host, target.port)
                        ProbeTarget.Kind.HTTPS -> httpsProbe(network, target.url!!)
                    }
                }
                val rtt = (System.nanoTime() - started) / 1_000_000
                ProbeResult(target.name, ok = true, rttMs = rtt, detail = "${rtt}ms")
            } catch (e: TimeoutCancellationException) {
                ProbeResult(target.name, ok = false, rttMs = null, detail = "Timeout (${PROBE_TIMEOUT.inWholeSeconds}s)")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ProbeResult(target.name, ok = false, rttMs = null, detail = e.message?.take(80) ?: e.javaClass.simpleName)
            }
        }

    private fun tcpProbe(
        network: Network,
        host: String,
        port: Int,
    ) {
        Socket().use { socket ->
            network.bindSocket(socket)
            socket.connect(InetSocketAddress(host, port), PROBE_TIMEOUT.inWholeMilliseconds.toInt())
        }
    }

    private fun httpsProbe(
        network: Network,
        spec: String,
    ) {
        val connection = network.openConnection(URL(spec)) as HttpURLConnection
        try {
            connection.connectTimeout = PROBE_TIMEOUT.inWholeMilliseconds.toInt()
            connection.readTimeout = PROBE_TIMEOUT.inWholeMilliseconds.toInt()
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            connection.connect()
            val code = connection.responseCode
            check(code in 200..399 || code == HttpURLConnection.HTTP_NO_CONTENT) { "HTTP $code" }
        } finally {
            connection.disconnect()
        }
    }

    private fun classify(
        caps: NetworkCapabilities?,
        registeredPlmn: String?,
        probes: List<ProbeResult>,
    ): WatchdogVerdict {
        val registered = !registeredPlmn.isNullOrEmpty()
        if (caps == null) {
            return if (registered) WatchdogVerdict.NO_DEFAULT_NETWORK else WatchdogVerdict.NO_REGISTRATION
        }
        if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)) {
            return WatchdogVerdict.CAPTIVE_PORTAL
        }
        val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val cellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        if (wifi && !cellular) {
            return WatchdogVerdict.WIFI_DEFAULT
        }
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            return if (!registered && cellular) WatchdogVerdict.NO_REGISTRATION else WatchdogVerdict.UNVALIDATED
        }
        if (probes.isEmpty()) return WatchdogVerdict.NO_DEFAULT_NETWORK
        val ok = probes.filter { it.ok }
        val failed = probes.filter { !it.ok }
        if (ok.isEmpty()) {
            return if (registered) WatchdogVerdict.ALL_PROBES_FAILED else WatchdogVerdict.NO_REGISTRATION
        }
        if (failed.isNotEmpty()) return WatchdogVerdict.PARTIAL_PATH
        val slow = ok.all { it.rttMs != null && it.rttMs >= SLOW_RTT_MS }
        return if (slow) WatchdogVerdict.HIGH_LATENCY else WatchdogVerdict.OK
    }

    private fun transportLabel(caps: NetworkCapabilities?): String {
        if (caps == null) return "none"
        val kinds =
            buildList {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("cellular")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("wifi")
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("ethernet")
            }
        return kinds.joinToString("+").ifEmpty { "other" }
    }

    private fun formatLog(sample: WatchdogSample): String {
        val probes =
            if (sample.probes.isEmpty()) {
                "no probes"
            } else {
                sample.probes.joinToString(" ") { "${it.name}=${it.detail}" }
            }
        val plmn = sample.registeredPlmn ?: "none"
        val roam =
            when (sample.roaming) {
                true -> "roaming"
                false -> "home"
                null -> "roam?"
            }
        val rat = sample.rat?.let { " rat=$it" } ?: ""
        val rsrp = sample.rsrp?.let { " rsrp=$it" } ?: " rsrp=?"
        val rsrq = sample.rsrq?.let { " rsrq=$it" } ?: ""
        val sinr = sample.sinr?.let { " sinr=$it" } ?: ""
        val valid =
            when (sample.validated) {
                true -> "validated"
                false -> "unvalidated"
                null -> "valid?"
            }
        val rttSummary =
            if (sample.avgRttMs != null) {
                "RTT avg=${sample.avgRttMs}ms (min=${sample.minRttMs}ms, max=${sample.maxRttMs}ms)" +
                    (sample.jitterMs?.let { " jitter=${it}ms" } ?: "")
            } else {
                "RTT N/A"
            }
        val failureSummary = "Probe fail rate=${sample.probeFailureRatePercent}% (${sample.failedProbes}/${sample.probes.size})"
        return "${sample.time}  ${sample.verdict.name}  ${sample.transport} $valid  PLMN $plmn $roam$rat$rsrp$rsrq$sinr  $rttSummary  $failureSummary  $probes"
    }

    private fun now(): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
}
