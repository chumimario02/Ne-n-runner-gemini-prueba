package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Runner",
    val totalStars: Long = 0L,
    val unlockedCampaignIndex: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class CampaignProgress(
    @DocumentId val id: String = "",
    val levelId: String = "",
    val stars: Long = 0L,
    val bestTimeMs: Long = 0L,
    val bestShards: Long = 0L,
    val deaths: Long = 0L,
    val completed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class DraftLevel(
    @DocumentId val id: String = "",
    val title: String = "",
    val description: String = "",
    val difficulty: String = "normal",
    val theme: String = "cyan",
    val width: Long = 40L,
    val height: Long = 14L,
    val rows: List<String> = emptyList(),
    val verified: Boolean = false,
    val publishedLevelId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun toLevelData(authorName: String): LevelData = LevelData(
        id = id,
        title = title.ifBlank { "Sin título" },
        description = description,
        authorName = authorName,
        difficulty = LevelDifficulty.fromWire(difficulty),
        theme = LevelTheme.fromWire(theme),
        width = width.toInt().coerceIn(24, 80),
        height = height.toInt().coerceIn(12, 16),
        rows = rows,
        isCampaign = false,
        verified = verified,
        publishedLevelId = publishedLevelId,
        draftId = id,
    )
}

@Keep
@IgnoreExtraProperties
data class CommunityLevel(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorName: String = "Runner",
    val title: String = "",
    val description: String = "",
    val difficulty: String = "normal",
    val theme: String = "cyan",
    val width: Long = 40L,
    val height: Long = 14L,
    val rows: List<String> = emptyList(),
    val verified: Boolean = false,
    val status: String = "published",
    val playsCount: Long = 0L,
    val likesCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun toLevelData(): LevelData = LevelData(
        id = id,
        title = title.ifBlank { "Nivel de Comunidad" },
        description = description,
        authorName = authorName.ifBlank { "Runner" },
        difficulty = LevelDifficulty.fromWire(difficulty),
        theme = LevelTheme.fromWire(theme),
        width = width.toInt().coerceIn(24, 80),
        height = height.toInt().coerceIn(12, 16),
        rows = rows,
        isCampaign = false,
        verified = verified,
        publishedLevelId = id,
    )
}

@Keep
@IgnoreExtraProperties
data class UserLikedLevel(
    @DocumentId val id: String = "",
    val levelId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
)

data class LevelData(
    val id: String,
    val title: String,
    val description: String,
    val authorName: String,
    val difficulty: LevelDifficulty,
    val theme: LevelTheme,
    val width: Int,
    val height: Int,
    val rows: List<String>,
    val isCampaign: Boolean = false,
    val campaignIndex: Int = -1,
    val parTimeMs: Long = 45_000L,
    val verified: Boolean = true,
    val publishedLevelId: String? = null,
    val draftId: String? = null,
) {
    fun tileAt(x: Int, y: Int): TileType {
        if (y !in rows.indices) return TileType.EMPTY
        val row = rows[y]
        if (x !in row.indices) return TileType.EMPTY
        return TileType.fromCode(row[x])
    }

    fun countShards(): Int = rows.sumOf { row -> row.count { it == TileType.COIN.code } }

    fun countEnemies(): Int = rows.sumOf { row ->
        row.count { TileType.fromCode(it).isEnemy }
    }

    fun hasSpawnAndGoal(): Boolean {
        var hasSpawn = false
        var hasGoal = false
        for (row in rows) {
            if (TileType.SPAWN.code in row) hasSpawn = true
            if (TileType.GOAL.code in row) hasGoal = true
        }
        return hasSpawn && hasGoal
    }

    companion object {
        fun createBlank(width: Int = 40, height: Int = 14): MutableList<String> {
            val w = width.coerceIn(24, 80)
            val h = height.coerceIn(12, 16)
            return MutableList(h) { y ->
                when (y) {
                    h - 1 -> "#".repeat(w)
                    h - 2 -> buildString {
                        append("..")
                        append('S')
                        repeat(w - 6) { append('.') }
                        append('G')
                        append("..")
                    }
                    else -> ".".repeat(w)
                }
            }
        }
    }
}
