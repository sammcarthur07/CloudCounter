package com.sam.cloudcounter

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ActivityLog::class,
        Smoker::class,
        SessionSummary::class,
        Stash::class,
        StashEntry::class,
        ConsumptionRatio::class,
        ChatMessage::class,
        ChatRoom::class,
        ChatUser::class,
        MessageLike::class,
        MessageReport::class,
        VideoReport::class,
        UserDeletedMessage::class,
        Goal::class
    ],
    version = 22, // Changed from 20 to 21
    exportSchema = false
)
@TypeConverters(Converters::class, GoalConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun activityLogDao(): ActivityLogDao
    abstract fun smokerDao(): SmokerDao
    abstract fun sessionSummaryDao(): SessionSummaryDao
    abstract fun stashDao(): StashDao
    abstract fun chatDao(): ChatDao
    abstract fun goalDao(): GoalDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Existing migrations...
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE smokers ADD COLUMN isCloudSmoker INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE smokers ADD COLUMN cloudUserId TEXT")
                database.execSQL("ALTER TABLE smokers ADD COLUMN shareCode TEXT")
                database.execSQL("ALTER TABLE smokers ADD COLUMN lastSyncTime INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE smokers ADD COLUMN needsSync INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE session_summaries ADD COLUMN liveSyncEnabled INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Empty migration if needed
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create stash tables
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `stash` (
                        `id` INTEGER NOT NULL DEFAULT 1, 
                        `totalGrams` REAL NOT NULL DEFAULT 0.0, 
                        `currentGrams` REAL NOT NULL DEFAULT 0.0, 
                        `pricePerGram` REAL NOT NULL DEFAULT 0.0, 
                        `gramsPerBowl` REAL NOT NULL DEFAULT 0.2, 
                        `conesPerBowl` REAL NOT NULL DEFAULT 6.0, 
                        `consumeFromStash` INTEGER NOT NULL DEFAULT 0, 
                        `lastUpdated` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                """)

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS stash_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        grams REAL NOT NULL,
                        pricePerGram REAL NOT NULL,
                        totalCost REAL NOT NULL,
                        activityType TEXT,
                        smokerName TEXT,
                        notes TEXT
                    )
                """)

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS consumption_ratios (
                        id INTEGER PRIMARY KEY NOT NULL DEFAULT 1,
                        coneGrams REAL NOT NULL DEFAULT 0.3,
                        jointGrams REAL NOT NULL DEFAULT 0.5,
                        bowlGrams REAL NOT NULL DEFAULT 0.2,
                        lastUpdated INTEGER NOT NULL
                    )
                """)

                // Insert default rows
                database.execSQL("""
                    INSERT OR IGNORE INTO stash (id, totalGrams, currentGrams, pricePerGram, gramsPerBowl, conesPerBowl, consumeFromStash, lastUpdated)
                    VALUES (1, 0.0, 0.0, 0.0, 0.2, 6.0, 0, ${System.currentTimeMillis()})
                """)

                database.execSQL("""
                    INSERT OR IGNORE INTO consumption_ratios (id, coneGrams, jointGrams, bowlGrams, lastUpdated)
                    VALUES (1, 0.3, 0.5, 0.2, ${System.currentTimeMillis()})
                """)
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create chat_messages table with correct structure
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS chat_messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        messageId TEXT NOT NULL,
                        roomId TEXT NOT NULL,
                        senderId TEXT NOT NULL,
                        senderName TEXT NOT NULL,
                        message TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        isSynced INTEGER NOT NULL,
                        isDeleted INTEGER NOT NULL
                    )
                """)

                // Create index for room queries
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_chat_messages_roomId_timestamp 
                    ON chat_messages (roomId, timestamp DESC)
                """)

                // Create chat_rooms table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS chat_rooms (
                        roomId TEXT PRIMARY KEY NOT NULL,
                        roomName TEXT NOT NULL,
                        roomType TEXT NOT NULL,
                        shareCode TEXT,
                        lastMessageTime INTEGER NOT NULL,
                        unreadCount INTEGER NOT NULL,
                        isActive INTEGER NOT NULL
                    )
                """)

                // Create chat_users table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS chat_users (
                        userId TEXT PRIMARY KEY NOT NULL,
                        userName TEXT NOT NULL,
                        roomId TEXT NOT NULL,
                        isOnline INTEGER NOT NULL,
                        lastSeen INTEGER NOT NULL,
                        isTyping INTEGER NOT NULL
                    )
                """)

                // Create index for room user queries
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_chat_users_roomId_isOnline 
                    ON chat_users (roomId, isOnline)
                """)
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS goals (
                        goalId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        goalType TEXT NOT NULL,
                        timeBasedType TEXT,
                        timeDuration INTEGER,
                        timeUnit TEXT,
                        targetJoints INTEGER NOT NULL DEFAULT 0,
                        targetCones INTEGER NOT NULL DEFAULT 0,
                        targetBowls INTEGER NOT NULL DEFAULT 0,
                        currentJoints INTEGER NOT NULL DEFAULT 0,
                        currentCones INTEGER NOT NULL DEFAULT 0,
                        currentBowls INTEGER NOT NULL DEFAULT 0,
                        isRecurring INTEGER NOT NULL DEFAULT 0,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL,
                        startedAt INTEGER NOT NULL,
                        completedAt INTEGER,
                        lastResetAt INTEGER NOT NULL,
                        progressNotificationsEnabled INTEGER NOT NULL DEFAULT 1,
                        completionNotificationsEnabled INTEGER NOT NULL DEFAULT 1,
                        sessionShareCode TEXT,
                        goalName TEXT NOT NULL DEFAULT '',
                        lastNotificationPercentage INTEGER NOT NULL DEFAULT 0,
                        isPaused INTEGER NOT NULL DEFAULT 0,
                        allowOverflow INTEGER NOT NULL DEFAULT 1,
                        completedRounds INTEGER NOT NULL DEFAULT 0
                    )
                """)

                // Create index for active goals queries
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_goals_isActive_createdAt 
                    ON goals (isActive, createdAt DESC)
                """)
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add new columns to activity_logs table for ratio storage
                database.execSQL("ALTER TABLE activity_logs ADD COLUMN gramsPerActivity REAL")
                database.execSQL("ALTER TABLE activity_logs ADD COLUMN pricePerGram REAL")

                // Add new columns to chat_messages table
                database.execSQL("ALTER TABLE chat_messages ADD COLUMN likeCount INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE chat_messages ADD COLUMN reportCount INTEGER NOT NULL DEFAULT 0")

                // Create message_likes table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS message_likes (
                        messageId TEXT NOT NULL,
                        userId TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        PRIMARY KEY(messageId, userId)
                    )
                """)

                // Create message_reports table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS message_reports (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        messageId TEXT NOT NULL,
                        reportedUserId TEXT NOT NULL,
                        reportedUserName TEXT NOT NULL,
                        reporterUserId TEXT NOT NULL,
                        reporterUserName TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        messageContent TEXT,
                        timestamp INTEGER NOT NULL,
                        isSent INTEGER NOT NULL DEFAULT 0
                    )
                """)

                // Create video_reports table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS video_reports (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        reportedUserId TEXT NOT NULL,
                        reportedUserName TEXT NOT NULL,
                        reporterUserId TEXT NOT NULL,
                        reporterUserName TEXT NOT NULL,
                        roomId TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        isSent INTEGER NOT NULL DEFAULT 0
                    )
                """)

                // Create indices for better query performance
                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_message_likes_messageId 
                    ON message_likes (messageId)
                """)

                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_message_reports_isSent 
                    ON message_reports (isSent)
                """)

                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_video_reports_isSent 
                    ON video_reports (isSent)
                """)
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(database: SupportSQLiteDatabase) {
                Log.d("Migration", "Starting migration from 16 to 17")

                // Step 1: Add new columns to activity_logs
                database.execSQL("ALTER TABLE activity_logs ADD COLUMN consumerId INTEGER")
                database.execSQL("ALTER TABLE activity_logs ADD COLUMN payerStashOwnerId TEXT")

                // Step 2: Rename existing columns for clarity (SQLite doesn't support RENAME COLUMN in older versions)
                // We'll create new columns and copy data
                database.execSQL("ALTER TABLE activity_logs ADD COLUMN gramsAtLog REAL NOT NULL DEFAULT 0.0")
                database.execSQL("ALTER TABLE activity_logs ADD COLUMN pricePerGramAtLog REAL NOT NULL DEFAULT 0.0")

                // Step 3: Copy data from old columns to new ones
                database.execSQL("""
                    UPDATE activity_logs 
                    SET gramsAtLog = COALESCE(gramsPerActivity, 
                        CASE 
                            WHEN type = 'CONE' THEN 0.3
                            WHEN type = 'JOINT' THEN 0.5
                            WHEN type = 'BOWL' THEN 0.2
                            ELSE 0.0
                        END),
                        pricePerGramAtLog = COALESCE(pricePerGram, 0.0)
                """)

                // Step 4: Populate consumerId with smokerId for existing records (backward compatibility)
                database.execSQL("UPDATE activity_logs SET consumerId = smokerId WHERE consumerId IS NULL")

                // Step 5: For existing records, set payerStashOwnerId to null (will be treated as MY_STASH)
                // This is already null by default from the ALTER TABLE statement

                // Step 6: Create indexes for better query performance
                database.execSQL("CREATE INDEX IF NOT EXISTS index_activity_logs_consumerId ON activity_logs(consumerId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_activity_logs_payerStashOwnerId ON activity_logs(payerStashOwnerId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_activity_logs_timestamp ON activity_logs(timestamp)")

                Log.d("Migration", "Migration from 16 to 17 completed successfully")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(database: SupportSQLiteDatabase) {
                Log.d("Migration", "Starting migration from 17 to 18")

                // Add session tracking columns
                database.execSQL("ALTER TABLE activity_logs ADD COLUMN sessionId INTEGER")
                database.execSQL("ALTER TABLE activity_logs ADD COLUMN sessionStartTime INTEGER")

                // Create index for session queries
                database.execSQL("CREATE INDEX IF NOT EXISTS index_activity_logs_sessionId ON activity_logs(sessionId)")

                Log.d("Migration", "Migration from 17 to 18 completed successfully")
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(database: SupportSQLiteDatabase) {
                Log.d("Migration", "Starting migration from 18 to 19 - Fixing 'null' string issue")

                // Fix the "null" string issue - convert string "null" to actual NULL
                database.execSQL("""
                    UPDATE activity_logs 
                    SET payerStashOwnerId = NULL 
                    WHERE payerStashOwnerId = 'null'
                """)

                Log.d("Migration", "Fixed 'null' string values in payerStashOwnerId")

                // Also ensure consumerId is properly set
                database.execSQL("""
                    UPDATE activity_logs 
                    SET consumerId = smokerId 
                    WHERE consumerId IS NULL
                """)

                Log.d("Migration", "Migration from 18 to 19 completed successfully")
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(database: SupportSQLiteDatabase) {
                Log.d("Migration", "Starting migration from 19 to 20 - Adding user_deleted_messages table")

                // Create the user_deleted_messages table for tracking locally deleted messages
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS user_deleted_messages (
                        userId TEXT NOT NULL,
                        messageId TEXT NOT NULL,
                        deletedAt INTEGER NOT NULL,
                        PRIMARY KEY(userId, messageId)
                    )
                """)

                Log.d("Migration", "Migration from 19 to 20 completed successfully")
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(database: SupportSQLiteDatabase) {
                Log.d("Migration", "Starting migration from 20 to 21 - Adding userDefinedConeGrams to consumption_ratios")

                // Add the new column with a default value of NULL
                database.execSQL("ALTER TABLE consumption_ratios ADD COLUMN userDefinedConeGrams REAL DEFAULT NULL")

                Log.d("Migration", "Migration from 20 to 21 completed successfully")
            }
        }

        val MIGRATION_21_TO_22 = object : Migration(21, 22) { // Replace X and Y with your version numbers
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add new columns to consumption_ratios table
                database.execSQL("ALTER TABLE consumption_ratios ADD COLUMN deductConesFromStash INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE consumption_ratios ADD COLUMN deductJointsFromStash INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE consumption_ratios ADD COLUMN deductBowlsFromStash INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cloudcounter-db"
                )
                    // Add all migrations including the new one
                    .addMigrations(
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_9_10,
                        MIGRATION_12_13,
                        MIGRATION_13_14,
                        MIGRATION_14_15,
                        MIGRATION_15_16,
                        MIGRATION_16_17,
                        MIGRATION_17_18,
                        MIGRATION_18_19,
                        MIGRATION_19_20,
                        MIGRATION_20_21,
                        MIGRATION_21_TO_22// Added new migration
                    )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
    }
}