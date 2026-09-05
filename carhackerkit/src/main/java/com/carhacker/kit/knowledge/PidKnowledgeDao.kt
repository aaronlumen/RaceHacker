package com.carhacker.kit.knowledge

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PidKnowledgeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVehicleIdentity(entity: VehicleIdentityEntity)

    @Query("SELECT * FROM vehicle_identity WHERE vehicleKey = :vehicleKey LIMIT 1")
    suspend fun getVehicleIdentity(vehicleKey: String): VehicleIdentityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPid(entity: DiscoveredPidEntity)

    @Query("SELECT * FROM discovered_pids WHERE vehicleKey = :vehicleKey AND mode = :mode")
    suspend fun getPids(vehicleKey: String, mode: Int): List<DiscoveredPidEntity>

    @Query("SELECT COUNT(*) FROM discovered_pids WHERE vehicleKey = :vehicleKey")
    suspend fun countPids(vehicleKey: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEcu(entity: DiscoveredEcuEntity)

    @Query("SELECT * FROM discovered_ecus WHERE vehicleKey = :vehicleKey")
    suspend fun getEcus(vehicleKey: String): List<DiscoveredEcuEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertService(entity: DiscoveredServiceEntity)

    @Query("SELECT * FROM discovered_services WHERE vehicleKey = :vehicleKey")
    suspend fun getServices(vehicleKey: String): List<DiscoveredServiceEntity>

    @Query("SELECT DISTINCT vehicleKey FROM vehicle_identity")
    suspend fun getKnownVehicleKeys(): List<String>
}
