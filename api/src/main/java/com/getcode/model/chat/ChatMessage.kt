package com.getcode.model.chat

import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.Cursor
import com.getcode.model.ID
import com.getcode.model.MessageStatus

/**
 * A message in a chat
 *
 * @param id Globally unique ID for this message
 * @param senderId The chat member that sent the message.
 * For [ChatType.Notification] chats, this field is omitted since the chat has exactly 1 member.
 * @param cursor Cursor value for this message for reference in a paged GetMessagesRequest
 * @param dateMillis Timestamp this message was generated at
 * @param contents Ordered message content. A message may have more than one piece of content.
 * @param status Derived [MessageStatus] from [Pointer]'s in [ChatMember].
 */
data class ChatMessage(
    val id: ID,
    val senderId: ID?,
    val isFromSelf: Boolean,
    val cursor: Cursor,
    val dateMillis: Long,
    val contents: List<MessageContent>,
    val status: MessageStatus,
) {
    val hasEncryptedContent: Boolean
        get() {
            return contents.firstOrNull { it is MessageContent.SodiumBox } != null
        }

    fun decryptingUsing(keyPair: KeyPair): ChatMessage {
        return ChatMessage(
            id = id,
            senderId = senderId,
            status = status,
            isFromSelf = isFromSelf,
            dateMillis = dateMillis,
            cursor = cursor,
            contents = contents.map {
                when (it) {
                    is MessageContent.Exchange,
                    is MessageContent.Localized,
                    is MessageContent.Decrypted,
                    is MessageContent.IdentityRevealed,
                    is MessageContent.RawText,
                    is MessageContent.ThankYou -> it // passthrough
                    is MessageContent.SodiumBox -> {
                        val decrypted = it.data.decryptMessageUsingNaClBox(keyPair = keyPair)
                        if (decrypted != null) {
                            MessageContent.Decrypted(data = decrypted, isFromSelf = isFromSelf)
                        } else {
                            it
                        }
                    }


                }
            }
        )
    }
}