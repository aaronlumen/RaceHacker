package com.carhacker.kit.knowledge

import androidx.room.Entity

/**
 * Response learning — DIAGNOSTIC_PLATFORM_VISION.md's "Phase 1: local
 * persistence" for PID/ECU/service discovery. Every enumeration/brute-force/
 * security-scan pass was previously in-memory only (OBDProtocol's
 * discoveredPIDs/ecuAddresses, SecurityTester's ECUInfo/DiagService lists),
 * thrown away the moment the app disconnected. These entities are the first
 * real use of the `androidx.room` dependency this module already declared
 * but never actually implemented.
 *
 * Everything is keyed by `vehicleKey` (the VIN once read via
 * OBDProtocol.getVIN() — "unknown" as a fallback for sessions where it
 * couldn't be read) so knowledge accumulates per-vehicle across sessions
 * instead of being scoped to one connection.
 */

@Entity(tableName = "vehicle_identity", primaryKeys = ["vehicleKey"])
data class VehicleIdentityEntity(
    val vehicleKey: String,
    val vin: String?,
    val ecuName: String?,
    val calibrationId: String?,
    val firstSeenMs: Long,
    val lastSeenMs: Long
)

@Entity(tableName = "discovered_pids", primaryKeys = ["vehicleKey", "mode", "pid"])
data class DiscoveredPidEntity(
    val vehicleKey: String,
    val mode: Int,
    val pid: Int,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
    val timesConfirmed: Int
)

@Entity(tableName = "discovered_ecus", primaryKeys = ["vehicleKey", "address"])
data class DiscoveredEcuEntity(
    val vehicleKey: String,
    val address: Int,
    val responseAddress: Int,
    val name: String,
    val protocol: String,
    val firstSeenMs: Long,
    val lastSeenMs: Long
)

@Entity(tableName = "discovered_services", primaryKeys = ["vehicleKey", "ecuAddress", "serviceId"])
data class DiscoveredServiceEntity(
    val vehicleKey: String,
    // 0 when the discovery pass that found this service didn't attribute it
    // to a specific ECU address (SecurityTester's DiagService doesn't carry
    // one today) — a real "unknown/global" value, not a missing one.
    val ecuAddress: Int,
    val serviceId: Int,
    val name: String,
    val response: String,
    val requiresAuth: Boolean,
    val firstSeenMs: Long,
    val lastSeenMs: Long
)
