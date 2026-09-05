package com.carhacker.kit.knowledge

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        VehicleIdentityEntity::class,
        DiscoveredPidEntity::class,
        DiscoveredEcuEntity::class,
        DiscoveredServiceEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PidKnowledgeDatabase : RoomDatabase() {
    abstract fun dao(): PidKnowledgeDao

    companion object {
        @Volatile
        private var instance: PidKnowledgeDatabase? = null

        fun getInstance(context: Context): PidKnowledgeDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PidKnowledgeDatabase::class.java,
                    "pid_knowledge.db"
                ).build().also { instance = it }
            }
    }
}
