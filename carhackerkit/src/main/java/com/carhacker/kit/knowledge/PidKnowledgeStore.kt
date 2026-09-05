package com.carhacker.kit.knowledge

import android.content.Context
import com.carhacker.kit.security.DiagService
import com.carhacker.kit.security.ECUInfo

/**
 * Facade over [PidKnowledgeDao] — this is what the UI layer (MainActivity)
 * calls after each discovery pass, so a vehicle's known PIDs/ECUs/services
 * accumulate across sessions instead of being re-discovered from scratch
 * every connection. See DIAGNOSTIC_PLATFORM_VISION.md's "Response learning"
 * section for the full design (why, and the future webserver-sync phase
 * this deliberately does NOT attempt yet).
 *
 * Kept as a thin layer deliberately separate from OBDProtocol/CANProtocol/
 * SecurityTester themselves — those are plain engine classes with no
 * Android Context and shouldn't need one just to persist their findings.
 */
class PidKnowledgeStore(context: Context) {
    private val dao = PidKnowledgeDatabase.getInstance(context).dao()

    /** Call once per session, as soon as a VIN/ECU name/calibration ID is read. */
    suspend fun recordVehicleIdentity(vehicleKey: String, vin: String?, ecuName: String?, calibrationId: String?) {
        val now = System.currentTimeMillis()
        val existing = dao.getVehicleIdentity(vehicleKey)
        dao.upsertVehicleIdentity(
            VehicleIdentityEntity(
                vehicleKey = vehicleKey,
                vin = vin ?: existing?.vin,
                ecuName = ecuName ?: existing?.ecuName,
                calibrationId = calibrationId ?: existing?.calibrationId,
                firstSeenMs = existing?.firstSeenMs ?: now,
                lastSeenMs = now
            )
        )
    }

    /** Call with whatever a PID enumeration/brute-force pass just found for one Mode. */
    suspend fun recordPids(vehicleKey: String, mode: Int, pids: Set<Int>) {
        val now = System.currentTimeMillis()
        for (pid in pids) {
            val existing = dao.getPids(vehicleKey, mode).find { it.pid == pid }
            dao.upsertPid(
                DiscoveredPidEntity(
                    vehicleKey = vehicleKey,
                    mode = mode,
                    pid = pid,
                    firstSeenMs = existing?.firstSeenMs ?: now,
                    lastSeenMs = now,
                    timesConfirmed = (existing?.timesConfirmed ?: 0) + 1
                )
            )
        }
    }

    /** Call with a SecurityReport's ecuInfo list after a security scan. */
    suspend fun recordEcus(vehicleKey: String, ecus: List<ECUInfo>) {
        val now = System.currentTimeMillis()
        val known = dao.getEcus(vehicleKey)
        for (ecu in ecus) {
            val existing = known.find { it.address == ecu.address }
            dao.upsertEcu(
                DiscoveredEcuEntity(
                    vehicleKey = vehicleKey,
                    address = ecu.address,
                    responseAddress = ecu.responseAddress,
                    name = ecu.name,
                    protocol = ecu.protocol,
                    firstSeenMs = existing?.firstSeenMs ?: now,
                    lastSeenMs = now
                )
            )
            // Each ECU also carries its own supported-PID set (Mode 01, from
            // the security scan's own enumeration) — worth recording too,
            // not just what enumeratePIDs()/bruteForcePIDs() found.
            if (ecu.supportedPIDs.isNotEmpty()) recordPids(vehicleKey, 0x01, ecu.supportedPIDs)
        }
    }

    /**
     * Call with a SecurityReport's services list. ecuAddress is 0 (unknown/
     * global) since DiagService itself doesn't carry which ECU answered it.
     */
    suspend fun recordServices(vehicleKey: String, services: List<DiagService>) {
        val now = System.currentTimeMillis()
        val known = dao.getServices(vehicleKey)
        for (service in services) {
            val existing = known.find { it.serviceId == service.serviceId && it.ecuAddress == 0 }
            dao.upsertService(
                DiscoveredServiceEntity(
                    vehicleKey = vehicleKey,
                    ecuAddress = 0,
                    serviceId = service.serviceId,
                    name = service.name,
                    response = service.response.name,
                    requiresAuth = service.requiresAuth,
                    firstSeenMs = existing?.firstSeenMs ?: now,
                    lastSeenMs = now
                )
            )
        }
    }

    /** Every vehicle this device has ever recorded knowledge for. */
    suspend fun allKnownVehicleKeys(): List<String> = dao.getKnownVehicleKeys()

    /** A short human-readable summary of what's known about this vehicle so far. */
    suspend fun summarize(vehicleKey: String): String {
        val identity = dao.getVehicleIdentity(vehicleKey)
        val pidCount = dao.countPids(vehicleKey)
        val ecus = dao.getEcus(vehicleKey)
        val services = dao.getServices(vehicleKey)

        return buildString {
            appendLine("Known vehicle: $vehicleKey")
            if (identity != null) {
                appendLine("  ECU: ${identity.ecuName ?: "unknown"}  Calibration: ${identity.calibrationId ?: "unknown"}")
            }
            appendLine("  $pidCount known PID(s) across all modes")
            appendLine("  ${ecus.size} known ECU(s): " + ecus.joinToString { "${it.name} (0x${it.address.toString(16)})" })
            appendLine("  ${services.size} known UDS service(s)")
        }
    }
}
