package com.sam.cloudcounter

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface ChatDao {
    // Message operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    @Update
    suspend fun updateMessage(message: ChatMessage)

    @Query("SELECT * FROM chat_messages WHERE roomId = :roomId ORDER BY timestamp DESC LIMIT :limit")
    fun getMessagesForRoom(roomId: String, limit: Int = 100): LiveData<List<ChatMessage>>

    @Query("DELETE FROM chat_messages WHERE messageId = :messageId")
    suspend fun permanentlyDeleteMessageByMessageId(messageId: String)

    @Query("UPDATE chat_messages SET isDeveloperDeleted = 1 WHERE messageId = :messageId")
    suspend fun markMessageDeveloperDeleted(messageId: String)

    @Query("UPDATE chat_messages SET message = :newMessage, isEdited = 1, lastEditTime = :editTime WHERE messageId = :messageId")
    suspend fun updateMessageContent(messageId: String, newMessage: String, editTime: Long)

    @Query("SELECT * FROM chat_messages WHERE messageId = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): ChatMessage?

    @Delete
    suspend fun deleteUserDeletedMessage(userDeletedMessage: UserDeletedMessage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserDeletedMessage(deletion: UserDeletedMessage)

    @Query("SELECT * FROM chat_messages WHERE messageId = :messageId LIMIT 1")
    suspend fun getMessageByMessageId(messageId: String): ChatMessage?

    @Query("DELETE FROM chat_messages WHERE messageId = :messageId")
    suspend fun permanentlyDeleteMessage(messageId: String)

    @Query("SELECT messageId FROM user_deleted_messages WHERE userId = :userId")
    suspend fun getUserDeletedMessageIds(userId: String): List<String>

    @Query("SELECT * FROM chat_messages WHERE roomId = :roomId ORDER BY timestamp ASC")
    suspend fun getMessagesForRoomSync(roomId: String): List<ChatMessage>

    @Query("DELETE FROM chat_messages WHERE roomId = :roomId")
    suspend fun clearRoomMessages(roomId: String)

    @Query("UPDATE chat_messages SET isDeleted = 1 WHERE id = :messageId")
    suspend fun markMessageDeleted(messageId: Long)

    // Like operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLike(like: MessageLike)

    @Delete
    suspend fun deleteLike(like: MessageLike)

    @Query("SELECT * FROM message_likes WHERE messageId = :messageId AND userId = :userId LIMIT 1")
    suspend fun getLike(messageId: String, userId: String): MessageLike?

    @Query("SELECT COUNT(*) FROM message_likes WHERE messageId = :messageId")
    suspend fun getLikeCount(messageId: String): Int

    @Query("SELECT * FROM message_likes WHERE messageId = :messageId")
    suspend fun getLikesForMessage(messageId: String): List<MessageLike>

    @Query("UPDATE chat_messages SET likeCount = :count WHERE messageId = :messageId")
    suspend fun updateMessageLikeCount(messageId: String, count: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM message_likes WHERE messageId = :messageId AND userId = :userId)")
    suspend fun isMessageLikedByUser(messageId: String, userId: String): Boolean

    // Report operations
    @Insert
    suspend fun insertMessageReport(report: MessageReport): Long

    @Insert
    suspend fun insertVideoReport(report: VideoReport): Long

    @Query("SELECT * FROM message_reports WHERE isSent = 0")
    suspend fun getUnsentMessageReports(): List<MessageReport>

    @Query("SELECT * FROM video_reports WHERE isSent = 0")
    suspend fun getUnsentVideoReports(): List<VideoReport>

    @Query("UPDATE message_reports SET isSent = 1 WHERE id = :reportId")
    suspend fun markMessageReportSent(reportId: Long)

    @Query("UPDATE video_reports SET isSent = 1 WHERE id = :reportId")
    suspend fun markVideoReportSent(reportId: Long)

    @Query("UPDATE chat_messages SET reportCount = reportCount + 1 WHERE messageId = :messageId")
    suspend fun incrementMessageReportCount(messageId: String)

    // Room operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateRoom(room: ChatRoom)

    @Query("SELECT * FROM chat_rooms WHERE isActive = 1 ORDER BY lastMessageTime DESC")
    fun getActiveRooms(): LiveData<List<ChatRoom>>

    @Query("UPDATE chat_rooms SET unreadCount = 0 WHERE roomId = :roomId")
    suspend fun markRoomAsRead(roomId: String)

    @Query("UPDATE chat_rooms SET unreadCount = unreadCount + 1 WHERE roomId = :roomId")
    suspend fun incrementUnreadCount(roomId: String)

    // User operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateUser(user: ChatUser)

    @Query("SELECT * FROM chat_users WHERE roomId = :roomId AND isOnline = 1")
    fun getOnlineUsersForRoom(roomId: String): LiveData<List<ChatUser>>

    @Query("UPDATE chat_users SET isOnline = :isOnline, lastSeen = :lastSeen WHERE userId = :userId AND roomId = :roomId")
    suspend fun updateUserStatus(userId: String, roomId: String, isOnline: Boolean, lastSeen: Long)

    @Query("UPDATE chat_users SET isTyping = :isTyping WHERE userId = :userId AND roomId = :roomId")
    suspend fun updateTypingStatus(userId: String, roomId: String, isTyping: Boolean)

    @Query("DELETE FROM chat_users WHERE roomId = :roomId")
    suspend fun clearRoomUsers(roomId: String)

    // Get all user's sesh messages
    @Query("""
        SELECT * FROM chat_messages 
        WHERE senderId = :userId 
        AND roomId LIKE 'sesh_%' 
        AND isDeveloperDeleted = 0 
        ORDER BY timestamp ASC
    """)
    suspend fun getUserSeshMessages(userId: String): List<ChatMessage>

    // Get all user's public messages
    @Query("""
        SELECT * FROM chat_messages 
        WHERE senderId = :userId 
        AND roomId = 'public' 
        AND isDeveloperDeleted = 0 
        ORDER BY timestamp ASC
    """)
    suspend fun getUserPublicMessages(userId: String): List<ChatMessage>

    // Get all user's log entries (for sesh logs)
    @Query("""
        SELECT * FROM chat_messages 
        WHERE senderId = :userId 
        AND roomId = :logRoomId 
        ORDER BY timestamp ASC
    """)
    suspend fun getUserLogMessages(userId: String, logRoomId: String): List<ChatMessage>


}