package com.example.data

import androidx.annotation.Keep
import com.example.model.Difficulty
import com.example.model.LEVEL_GRID_HEIGHT
import com.example.model.LevelData
import com.example.model.resizeGridRows
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class CampaignProgressDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val levelIndex: Long = 0L,
    val completed: Boolean = false,
    val stars: Long = 0L,
    val bestTimeMs: Long = 0L,
    val bestScore: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CustomDraftDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val description: String = "",
    val difficulty: String = Difficulty.MEDIUM.name,
    val width: Long = 36L,
    val height: Long = LEVEL_GRID_HEIGHT.toLong(),
    val gridRows: List<String> = emptyList(),
    val verified: Boolean = false,
    val publishedLevelId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toLevelData(): LevelData {
        val safeWidth = width.toInt().coerceIn(24, 64)
        return LevelData(
            id = id,
            title = title.ifBlank { "Sin título" },
            description = description,
            difficulty = Difficulty.fromRaw(difficulty),
            width = safeWidth,
            height = LEVEL_GRID_HEIGHT,
            gridRows = resizeGridRows(gridRows, safeWidth),
            authorId = userId,
            authorName = "Tú",
            verified = verified,
            published = false,
            isCampaign = false,
            publishedLevelId = publishedLevelId
        )
    }
}

@Keep
@IgnoreExtraProperties
data class CommunityLevelDoc(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "Runner",
    val title: String = "",
    val description: String = "",
    val difficulty: String = Difficulty.MEDIUM.name,
    val width: Long = 36L,
    val height: Long = LEVEL_GRID_HEIGHT.toLong(),
    val gridRows: List<String> = emptyList(),
    val verified: Boolean = false,
    val published: Boolean = true,
    val playsCount: Long = 0L,
    val likesCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toLevelData(): LevelData {
        val safeWidth = width.toInt().coerceIn(24, 64)
        return LevelData(
            id = id,
            title = title.ifBlank { "Mapa Comunitario" },
            description = description,
            difficulty = Difficulty.fromRaw(difficulty),
            width = safeWidth,
            height = LEVEL_GRID_HEIGHT,
            gridRows = resizeGridRows(gridRows, safeWidth),
            authorId = authorId,
            authorName = authorName.ifBlank { "Runner" },
            verified = verified,
            published = published,
            playsCount = playsCount,
            likesCount = likesCount,
            isCampaign = false,
            publishedLevelId = id
        )
    }
}
