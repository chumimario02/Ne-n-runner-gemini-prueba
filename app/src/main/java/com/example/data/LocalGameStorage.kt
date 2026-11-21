package com.example.data

import android.content.Context
import com.example.model.Difficulty
import com.example.model.LEVEL_GRID_HEIGHT
import com.example.model.LevelData
import com.example.model.MAX_LEVEL_WIDTH
import com.example.model.MIN_LEVEL_WIDTH
import com.example.model.resizeGridRows
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class LocalGameStorage(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _username = MutableStateFlow(loadUsername())
    val username: StateFlow<String?> = _username.asStateFlow()

    private val _progress = MutableStateFlow(loadProgress())
    val progress: StateFlow<List<CampaignProgressDoc>> = _progress.asStateFlow()

    private val _drafts = MutableStateFlow(loadDrafts())
    val drafts: StateFlow<List<LevelData>> = _drafts.asStateFlow()

    fun setUsername(rawName: String) {
        val clean = rawName.trim().take(24)
        if (clean.length < 2) return
        prefs.edit().putString(KEY_USERNAME, clean).apply()
        _username.value = clean
    }

    fun saveCampaignProgress(
        levelId: String,
        levelIndex: Int,
        stars: Int,
        timeMs: Long,
        score: Long
    ) {
        val current = _progress.value.toMutableList()
        val existingIdx = current.indexOfFirst { it.id == levelId }
        val safeStars = stars.coerceIn(0, 3).toLong()
        val safeTime = timeMs.coerceAtLeast(0L)
        val safeScore = score.coerceAtLeast(0L)

        val updatedDoc = if (existingIdx >= 0) {
            val prev = current[existingIdx]
            val bestTime = if (prev.bestTimeMs <= 0L) safeTime else minOf(prev.bestTimeMs, safeTime)
            prev.copy(
                completed = true,
                stars = maxOf(prev.stars, safeStars),
                bestTimeMs = bestTime,
                bestScore = maxOf(prev.bestScore, safeScore)
            )
        } else {
            CampaignProgressDoc(
                id = levelId,
                userId = "local",
                levelIndex = levelIndex.toLong(),
                completed = true,
                stars = safeStars,
                bestTimeMs = safeTime,
                bestScore = safeScore
            )
        }

        if (existingIdx >= 0) {
            current[existingIdx] = updatedDoc
        } else {
            current.add(updatedDoc)
        }
        persistProgress(current)
        _progress.value = current
    }

    fun saveDraft(level: LevelData, verified: Boolean): LevelData {
        val current = _drafts.value.toMutableList()
        val draftId = level.id.takeIf { it.isNotBlank() && !level.published }
            ?: "local_${UUID.randomUUID().toString().take(12)}"
        val safeWidth = level.width.coerceIn(MIN_LEVEL_WIDTH, MAX_LEVEL_WIDTH)
        val cleanLevel = level.copy(
            id = draftId,
            title = level.title.trim().take(40).ifBlank { "Sector Personalizado" },
            description = level.description.trim().take(160),
            width = safeWidth,
            height = LEVEL_GRID_HEIGHT,
            gridRows = resizeGridRows(level.gridRows, safeWidth),
            authorName = _username.value ?: "Runner",
            verified = verified,
            published = false
        )

        val existingIdx = current.indexOfFirst { it.id == draftId }
        if (existingIdx >= 0) {
            current[existingIdx] = cleanLevel
        } else {
            current.add(0, cleanLevel)
        }
        persistDrafts(current)
        _drafts.value = current
        return cleanLevel
    }

    fun linkDraftPublishedId(draftId: String, publishedId: String, verified: Boolean) {
        val current = _drafts.value.toMutableList()
        val idx = current.indexOfFirst { it.id == draftId }
        if (idx >= 0) {
            current[idx] = current[idx].copy(publishedLevelId = publishedId, verified = verified)
            persistDrafts(current)
            _drafts.value = current
        }
    }

    fun deleteDraft(draftId: String) {
        val updated = _drafts.value.filterNot { it.id == draftId }
        persistDrafts(updated)
        _drafts.value = updated
    }

    private fun loadUsername(): String? {
        return prefs.getString(KEY_USERNAME, null)?.trim()?.takeIf { it.length >= 2 }
    }

    private fun loadProgress(): List<CampaignProgressDoc> {
        val raw = prefs.getString(KEY_PROGRESS_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        CampaignProgressDoc(
                            id = obj.optString("id", ""),
                            userId = "local",
                            levelIndex = obj.optLong("levelIndex", 0L),
                            completed = obj.optBoolean("completed", false),
                            stars = obj.optLong("stars", 0L),
                            bestTimeMs = obj.optLong("bestTimeMs", 0L),
                            bestScore = obj.optLong("bestScore", 0L)
                        )
                    )
                }
            }.filter { it.id.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persistProgress(list: List<CampaignProgressDoc>) {
        val arr = JSONArray()
        list.forEach { doc ->
            arr.put(
                JSONObject().apply {
                    put("id", doc.id)
                    put("levelIndex", doc.levelIndex)
                    put("completed", doc.completed)
                    put("stars", doc.stars)
                    put("bestTimeMs", doc.bestTimeMs)
                    put("bestScore", doc.bestScore)
                }
            )
        }
        prefs.edit().putString(KEY_PROGRESS_JSON, arr.toString()).apply()
    }

    private fun loadDrafts(): List<LevelData> {
        val raw = prefs.getString(KEY_DRAFTS_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val rowsJson = obj.optJSONArray("gridRows") ?: continue
                    val rows = List(rowsJson.length()) { r -> rowsJson.optString(r, "") }
                    val w = obj.optInt("width", 36).coerceIn(MIN_LEVEL_WIDTH, MAX_LEVEL_WIDTH)
                    add(
                        LevelData(
                            id = obj.optString("id", ""),
                            title = obj.optString("title", "Mapa"),
                            description = obj.optString("description", ""),
                            difficulty = Difficulty.fromRaw(obj.optString("difficulty", "MEDIUM")),
                            width = w,
                            height = LEVEL_GRID_HEIGHT,
                            gridRows = resizeGridRows(rows, w),
                            authorName = _username.value ?: "Runner",
                            verified = obj.optBoolean("verified", false),
                            published = false,
                            publishedLevelId = obj.optString("publishedLevelId", "").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }.filter { it.id.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persistDrafts(list: List<LevelData>) {
        val arr = JSONArray()
        list.forEach { lvl ->
            arr.put(
                JSONObject().apply {
                    put("id", lvl.id)
                    put("title", lvl.title)
                    put("description", lvl.description)
                    put("difficulty", lvl.difficulty.name)
                    put("width", lvl.width)
                    put("gridRows", JSONArray(lvl.gridRows))
                    put("verified", lvl.verified)
                    put("publishedLevelId", lvl.publishedLevelId ?: "")
                }
            )
        }
        prefs.edit().putString(KEY_DRAFTS_JSON, arr.toString()).apply()
    }

    private companion object {
        const val PREFS_NAME = "neon_runner_local"
        const val KEY_USERNAME = "runner_username"
        const val KEY_PROGRESS_JSON = "campaign_progress_json"
        const val KEY_DRAFTS_JSON = "editor_drafts_json"
    }
}
