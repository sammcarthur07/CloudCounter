// app/src/main/java/com/sam/cloudcounter/Smoker.kt

package com.sam.cloudcounter

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "smokers",
    indices = [Index(value = ["uid"], unique = true)] // Add index for fast UID lookups
)
data class Smoker(
    @PrimaryKey(autoGenerate = true)
    val smokerId: Long = 0,

    // This is the new globally unique identifier for the smoker.
    // It will be used for all synchronization purposes.
    val uid: String = UUID.randomUUID().toString(),

    val name: String,
    
    // Display order for custom sorting (lower values appear first)
    val displayOrder: Int = 0,

    // Cloud sync fields
    val isCloudSmoker: Boolean = false,
    val cloudUserId: String? = null,
    val shareCode: String? = null,
    val lastSyncTime: Long = 0L,
    val needsSync: Boolean = false,

    // Password protection fields
    val passwordHash: String? = null,
    val isPasswordVerified: Boolean = false,
    val isOwner: Boolean = false
)