package com.carhacker.kit.diagnostics

import com.carhacker.kit.knowledge.PidKnowledgeStore
import com.carhacker.kit.obd.DTC
import com.carhacker.kit.obd.OBDProtocol
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * S3 Diagnostic Intelligence — see S3_VISION.md's "§0 signature feature."
 *
 * Runs the SCAN → IDENTIFY → DISCOVER MODULES → READ CODES → COLLECT LIVE
 * DATA → CORRELATE → DIAGNOSE pipeline end to end, entirely on top of
 * OBDProtocol/PidKnowledgeStore methods that already exist — no new wire
 * protocol work, just orchestration + correlation logic. TEST (bidirectional
 * active tests), VERIFY REPAIR, and GENERATE REPORT (PDF) are deliberately
 * not part of this — see S3_VISION.md for why those are separate, larger,
 * and in TEST's case genuinely riskier pieces of work.
 *
 * DIAGNOSE here is rule-based, not LLM-backed — same "zero dependencies,
 * always works" principle as xyz.surina.racehacker.voice.NarrationEngine's
 * v1, and for the same reason: this should give a real answer with no
 * network/model dependency, with a richer LLM-backed narrator as a later,
 * additive upgrade rather than a prerequisite. Only handles the DTC
 * families where real correlated evidence already exists (lean codes today
 * — STFT/LTFT/MAF/O2 are all already read). Anything else gets an honest
 * "no correlation rule yet, here's the raw data" rather than a fabricated
 * diagnosis — the same "when genuinely unsure, say less" principle
 * SENSOR_DIAGNOSTICS.md already states for xyz.surina.racehacker.
 */
class DiagnosticIntelligenceEngine(
    private val obdProtocol: OBDProtocol,
    private val knowledgeStore: PidKnowledgeStore
) {
    /** The fixed set of Mode 01 PIDs collected for every diagnostic run — see LiveSnapshot. */
    private val snapshotPids = listOf(0x0C, 0x05, 0x06, 0x07, 0x10, 0x14, 0x11, 0x0E)

    suspend fun runWorkflow(vehicleKeyHint: String = "unknown"): DiagnosticSession {
        // IDENTIFY
        val vin = obdProtocol.getVIN().getOrNull()
        val ecuName = obdProtocol.getECUName().getOrNull()
        val calId = obdProtocol.getCalibrationID().getOrNull()
        val vehicleKey = if (!vin.isNullOrBlank()) vin else vehicleKeyHint
        knowledgeStore.recordVehicleIdentity(vehicleKey, vin, ecuName, calId)

        // READ CODES — stored, pending, and permanent, all three (permanent
        // codes are easy to forget: readDTCs() already parses mode 0x0A
        // correctly, it's just never been called with it anywhere before).
        val stored = obdProtocol.readDTCs(OBDProtocol.MODE_DTC_STORED).getOrNull() ?: emptyList()
        val pending = obdProtocol.readDTCs(OBDProtocol.MODE_DTC_PENDING).getOrNull() ?: emptyList()
        val permanent = obdProtocol.readDTCs(OBDProtocol.MODE_PERMANENT_DTC).getOrNull() ?: emptyList()

        // COLLECT LIVE DATA
        val snapshot = collectLiveSnapshot()

        // Feed this run's discoveries back into response learning — a
        // diagnostic run is itself a discovery pass, not just PID
        // enumeration/brute-force.
        knowledgeStore.recordPids(vehicleKey, 0x01, snapshotPids.toSet())

        // DISCOVER MODULES — ecuAddresses accumulates passively as a side
        // effect of every query made above (OBDProtocol parses the ECU
        // address header off of each response when available), so by this
        // point it already reflects what actually responded this session.
        val discoveredEcus = obdProtocol.getDiscoveredECUs()

        // CORRELATE + DIAGNOSE
        val allCodes = (stored + pending + permanent).distinctBy { it.code }
        val findings = allCodes.map { dtc -> diagnose(dtc, snapshot) }

        return DiagnosticSession(
            vehicleKey = vehicleKey,
            discoveredEcus = discoveredEcus,
            storedDtcs = stored,
            pendingDtcs = pending,
            permanentDtcs = permanent,
            liveSnapshot = snapshot,
            findings = findings
        )
    }

    private suspend fun collectLiveSnapshot(): LiveSnapshot {
        val values = mutableMapOf<Int, Float>()
        for (pid in snapshotPids) {
            val response = obdProtocol.queryPID(0x01, pid).getOrNull()
            val data = response?.data
            if (data != null) decodeNumeric(pid, data)?.let { values[pid] = it }
            delay(OBDProtocol.INTER_QUERY_DELAY_MS)
        }
        return LiveSnapshot(
            rpm = values[0x0C],
            coolantC = values[0x05],
            stftPct = values[0x06],
            ltftPct = values[0x07],
            mafGps = values[0x10],
            o2Volts = values[0x14],
            throttlePct = values[0x11],
            timingDeg = values[0x0E]
        )
    }

    /**
     * Numeric equivalents of PIDDefinitions' decode*() functions, which
     * return formatted display strings — this needs actual floats to reason
     * about (is STFT+LTFT past a threshold?), not "+18.5%" as text. Same SAE
     * J1979 formulas, just returning Float instead of String.
     */
    private fun decodeNumeric(pid: Int, data: ByteArray): Float? {
        val a = data.getOrNull(0)?.toInt()?.and(0xFF) ?: return null
        return when (pid) {
            0x05 -> (a - 40).toFloat() // coolant temp, °C
            0x06, 0x07 -> (a - 128) * 100f / 128f // fuel trim, %
            0x0C -> { // RPM
                val b = data.getOrNull(1)?.toInt()?.and(0xFF) ?: return null
                (256 * a + b) / 4f
            }
            0x0E -> (a - 128) / 2f // timing advance, degrees
            0x10 -> { // MAF, g/s
                val b = data.getOrNull(1)?.toInt()?.and(0xFF) ?: return null
                (256 * a + b) / 100f
            }
            0x11 -> a * 100f / 255f // throttle, %
            0x14 -> a / 200f // O2 sensor 1 voltage
            else -> null
        }
    }

    // ─── Correlation / diagnosis rules ──────────────────────────────────

    private fun diagnose(dtc: DTC, snapshot: LiveSnapshot): DiagnosticFinding {
        return when (dtc.code) {
            "P0171", "P0174" -> diagnoseLean(dtc, snapshot)
            else -> DiagnosticFinding(
                code = dtc.code,
                description = dtc.description ?: dtc.type.name,
                evidence = snapshot.evidenceLines(),
                likelyCauses = listOf(
                    "No correlation rule implemented yet for ${dtc.code} — " +
                        "showing raw live data only, not a guessed diagnosis."
                ),
                recommendedTests = emptyList()
            )
        }
    }

    /**
     * Lean-code correlation (P0171 Bank 1 / P0174 Bank 2) — the exact worked
     * example from the S3 vision discussion: STFT/LTFT confirming the ECU is
     * adding fuel, cross-referenced with MAF and O2 sensor readings.
     */
    private fun diagnoseLean(dtc: DTC, snapshot: LiveSnapshot): DiagnosticFinding {
        val combinedTrim = (snapshot.stftPct ?: 0f) + (snapshot.ltftPct ?: 0f)
        // Positive combined trim = ECU adding fuel = compensating for an
        // actually-lean condition. See RuleBasedNarrationEngine's identical
        // sign convention on the RaceHacker (:app) side.
        val trimConfirmsLean = combinedTrim > 5f

        val causes = if (trimConfirmsLean) {
            listOf(
                "Vacuum/intake leak",
                "MAF under-reporting",
                "Fuel-delivery restriction",
                "Exhaust leak upstream of the O2 sensor"
            )
        } else {
            listOf(
                "Fuel trims right now don't show the ECU compensating for a lean " +
                    "condition — this may be an intermittent code from a past drive " +
                    "cycle rather than a current fault; live data doesn't confirm it."
            )
        }

        val bank = if (dtc.code == "P0171") "Bank 1" else "Bank 2"
        return DiagnosticFinding(
            code = dtc.code,
            description = "System Too Lean ($bank)",
            evidence = snapshot.evidenceLines(),
            likelyCauses = causes,
            recommendedTests = if (trimConfirmsLean) listOf(
                "Smoke-test the intake for vacuum leaks",
                "Check MAF sensor cleanliness and wiring",
                "Inspect fuel pressure and injector delivery",
                "Check the exhaust manifold gasket upstream of the O2 sensor"
            ) else emptyList()
        )
    }
}

data class LiveSnapshot(
    val rpm: Float?,
    val coolantC: Float?,
    val stftPct: Float?,
    val ltftPct: Float?,
    val mafGps: Float?,
    val o2Volts: Float?,
    val throttlePct: Float?,
    val timingDeg: Float?
) {
    fun evidenceLines(): List<String> = listOfNotNull(
        rpm?.let { "RPM: ${it.toInt()}" },
        coolantC?.let { "Coolant temp: ${it.toInt()}°C" },
        stftPct?.let { "STFT (Bank 1): ${fmt(it)}%" },
        ltftPct?.let { "LTFT (Bank 1): ${fmt(it)}%" },
        mafGps?.let { "MAF: ${fmt(it)} g/s" },
        o2Volts?.let { "O2 sensor (Bank1/Sensor1): ${fmt(it)}V" },
        throttlePct?.let { "Throttle: ${fmt(it)}%" },
        timingDeg?.let { "Ignition timing: ${fmt(it)}°" }
    )

    private fun fmt(v: Float) = String.format(Locale.US, "%.1f", v)
}

data class DiagnosticFinding(
    val code: String,
    val description: String,
    val evidence: List<String>,
    val likelyCauses: List<String>,
    val recommendedTests: List<String>
)

data class DiagnosticSession(
    val vehicleKey: String,
    val discoveredEcus: Set<Int>,
    val storedDtcs: List<DTC>,
    val pendingDtcs: List<DTC>,
    val permanentDtcs: List<DTC>,
    val liveSnapshot: LiveSnapshot,
    val findings: List<DiagnosticFinding>
) {
    /** Formatted for the on-screen log — see S3_VISION.md's P0302 worked example for the target style. */
    fun toReport(): String = buildString {
        appendLine("Vehicle: $vehicleKey")
        appendLine("ECUs responding: " + if (discoveredEcus.isEmpty()) "none detected"
            else discoveredEcus.joinToString { "0x${it.toString(16).uppercase()}" })
        appendLine("DTCs — stored: ${storedDtcs.size}, pending: ${pendingDtcs.size}, permanent: ${permanentDtcs.size}")
        appendLine()

        if (findings.isEmpty()) {
            appendLine("No DTCs found — nothing to diagnose. Live snapshot for reference:")
            liveSnapshot.evidenceLines().forEach { appendLine("  $it") }
            return@buildString
        }

        for (finding in findings) {
            appendLine("${finding.code} — ${finding.description}")
            finding.evidence.forEach { appendLine("  $it") }
            appendLine("  Likely causes:")
            finding.likelyCauses.forEach { appendLine("    - $it") }
            if (finding.recommendedTests.isNotEmpty()) {
                appendLine("  Recommended next tests:")
                finding.recommendedTests.forEach { appendLine("    - $it") }
            }
            appendLine()
        }
    }
}
