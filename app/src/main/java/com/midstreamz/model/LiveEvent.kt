package com.midstreamz.model

enum class EventType {
    CHAT,
    SUPER_CHAT,
    MEMBERSHIP,
    STICKER,
    SYSTEM,
    SUBSCRIBER,
    LIKE,
    SHARE
}

data class LiveChatMessage(
    val id: String,
    val type: EventType,
    val authorName: String,
    val authorPhotoUrl: String? = null,
    val message: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val amountFormatted: String? = null,
    val amountColorHex: String? = null,
    val isModerator: Boolean = false,
    val isOwner: Boolean = false,
    val isMember: Boolean = false,
    val isTikTok: Boolean = false
) {
    fun toTtsText(includeAuthor: Boolean = true): String {
        return when (type) {
            EventType.SUPER_CHAT -> {
                val amount = amountFormatted ?: if (isTikTok) "Gift TikTok" else "super chat"
                if (includeAuthor) "$authorName mengirim $amount: $message" else message
            }
            EventType.SHARE -> {
                if (includeAuthor) "$authorName membagikan siaran langsung!" else "Live stream dibagikan"
            }
            EventType.MEMBERSHIP -> {
                if (includeAuthor) "$authorName bergabung menjadi member!" else "Member baru bergabung"
            }
            EventType.SUBSCRIBER -> {
                if (isTikTok) {
                    if (includeAuthor) "$authorName telah follow!" else "Follower baru bergabung"
                } else {
                    if (includeAuthor) "$authorName baru saja subscribe!" else "Subscriber baru bergabung"
                }
            }
            EventType.LIKE -> {
                if (includeAuthor) "$authorName menyukai live stream!" else "Like baru masuk"
            }
            EventType.CHAT -> {
                if (includeAuthor) "$authorName: $message" else message
            }
            else -> message
        }
    }
}
