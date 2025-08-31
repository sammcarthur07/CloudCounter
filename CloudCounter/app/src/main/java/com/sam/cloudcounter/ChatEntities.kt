package com.sam.cloudcounter

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for storing chat messages
 */
@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val messageId: String = "", // Firebase document ID for syncing
    val roomId: String = "", // "sesh_[shareCode]" or "public"
    val senderId: String = "", // Firebase user ID
    val senderName: String = "",
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val isDeleted: Boolean = false,
    val likeCount: Int = 0, // Total number of likes
    val reportCount: Int = 0, // Number of reports
    val isDeveloperDeleted: Boolean = false,
    val isEdited: Boolean = false, // Track if message has been edited
    val lastEditTime: Long? = null // Track when message was last edited
)

@Entity(
    tableName = "user_deleted_messages",
    primaryKeys = ["userId", "messageId"]
)
data class UserDeletedMessage(
    val userId: String,
    val messageId: String,
    val deletedAt: Long = System.currentTimeMillis()
)

/**
 * Entity for tracking message likes
 */
@Entity(
    tableName = "message_likes",
    primaryKeys = ["messageId", "userId"]
)
data class MessageLike(
    val messageId: String, // References ChatMessage.messageId
    val userId: String, // User who liked the message
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Entity for tracking reports
 */
@Entity(tableName = "message_reports")
data class MessageReport(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val messageId: String, // References ChatMessage.messageId
    val reportedUserId: String, // User being reported
    val reportedUserName: String,
    val reporterUserId: String, // User making the report
    val reporterUserName: String,
    val reason: String, // Report reason
    val messageContent: String? = null, // Content of reported message
    val timestamp: Long = System.currentTimeMillis(),
    val isSent: Boolean = false // Whether email was sent
)

/**
 * Entity for tracking video call reports
 */
@Entity(tableName = "video_reports")
data class VideoReport(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val reportedUserId: String,
    val reportedUserName: String,
    val reporterUserId: String,
    val reporterUserName: String,
    val roomId: String,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSent: Boolean = false
)

/**
 * Entity for tracking chat room membership
 */
@Entity(tableName = "chat_rooms")
data class ChatRoom(
    @PrimaryKey
    val roomId: String = "", // "sesh_[shareCode]" or "public"
    val roomName: String = "",
    val roomType: String = "", // "sesh" or "public"
    val shareCode: String? = null, // Only for sesh rooms
    val lastMessageTime: Long = 0L,
    val unreadCount: Int = 0,
    val isActive: Boolean = true
)

/**
 * Entity for tracking online users in chat
 */
@Entity(tableName = "chat_users")
data class ChatUser(
    @PrimaryKey
    val userId: String = "",
    val userName: String = "",
    val roomId: String = "",
    val isOnline: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis(),
    val isTyping: Boolean = false
)