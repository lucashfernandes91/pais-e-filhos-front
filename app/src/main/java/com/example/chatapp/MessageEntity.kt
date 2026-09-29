package com.example.chatapp

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "messages",
    primaryKeys = ["conversationId", "localKey"],
    indices = [Index(value = ["conversationId", "createdAt"])]
)
data class MessageEntity(
    val conversationId: Int,
    val localKey: String,
    val serverId: Int,
    val sender: String,
    val content: String,
    val createdAt: String,
    val readByJson: String?,
    val attachmentUrl: String?,
    val attachmentType: String?,
    val attachmentName: String?,
    val clientMessageId: String?
) {
    fun toMessage(): Message = Message(
        id = serverId,
        sender = sender,
        content = content,
        created_at = createdAt,
        conversation = conversationId,
        read_by = readByJson?.let { MessageJson.decodeReadBy(it) },
        attachment_url = attachmentUrl,
        attachment_type = attachmentType,
        attachment_name = attachmentName,
        client_message_id = clientMessageId
    )

    companion object {
        fun fromMessage(message: Message): MessageEntity = MessageEntity(
            conversationId = message.conversation,
            localKey = message.client_message_id ?: "server:${message.id}",
            serverId = message.id,
            sender = message.sender,
            content = message.content,
            createdAt = message.created_at,
            readByJson = message.read_by?.let(MessageJson::encodeReadBy),
            attachmentUrl = message.attachment_url,
            attachmentType = message.attachment_type,
            attachmentName = message.attachment_name,
            clientMessageId = message.client_message_id
        )
    }
}
