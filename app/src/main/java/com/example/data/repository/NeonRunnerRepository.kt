package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.CampaignProgress
import com.example.data.model.CommunityLevel
import com.example.data.model.DraftLevel
import com.example.data.model.LevelDifficulty
import com.example.data.model.LevelTheme
import com.example.data.model.UserLikedLevel
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class NeonRunnerRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Debes iniciar sesión con Google antes de acceder a la nube.")
    }

    suspend fun ensureUserProfile(displayName: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("No autenticado"))
        val path = "users/$uid"
        return try {
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            val cleanName = displayName.trim().ifBlank { "Runner" }.take(40)
            if (!snap.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to cleanName,
                    "totalStars" to 0L,
                    "unlockedCampaignIndex" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        if (userId.isBlank()) {
            emit(null)
            return@flow
        }
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    handleFlowError(error, OperationType.GET, path, userId)
                    throw error
                }
        )
    }

    fun observeCampaignProgress(userId: String): Flow<List<CampaignProgress>> = flow {
        if (userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$userId/campaign_progress"
        emitAll(
            db.collection("users").document(userId).collection("campaign_progress")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CampaignProgress::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    suspend fun saveCampaignResult(
        userId: String,
        levelId: String,
        levelIndex: Int,
        stars: Int,
        timeMs: Long,
        shards: Int,
        deaths: Int,
        totalCampaignLevels: Int,
    ): Result<Unit> {
        val progressPath = "users/$userId/campaign_progress/$levelId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Sesión cambiada; operación cancelada." }
            require(levelId.isNotBlank() && levelId.length <= 64) { "ID de nivel inválido" }

            val clampedStars = stars.coerceIn(0, 3).toLong()
            val clampedTime = timeMs.coerceIn(0L, 3_600_000L)
            val clampedShards = shards.coerceIn(0, 200).toLong()
            val clampedDeaths = deaths.coerceIn(0, 10_000).toLong()

            val progressRef = db.collection("users").document(uid)
                .collection("campaign_progress").document(levelId)
            val existing = progressRef.get().await()

            if (!existing.exists()) {
                val payload = mapOf(
                    "levelId" to levelId,
                    "stars" to clampedStars,
                    "bestTimeMs" to clampedTime,
                    "bestShards" to clampedShards,
                    "deaths" to clampedDeaths,
                    "completed" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                progressRef.set(payload).await()
            } else {
                val oldStars = existing.getLong("stars") ?: 0L
                val oldBestTime = existing.getLong("bestTimeMs") ?: Long.MAX_VALUE
                val oldBestShards = existing.getLong("bestShards") ?: 0L
                val oldDeaths = existing.getLong("deaths") ?: Long.MAX_VALUE

                progressRef.update(
                    mapOf(
                        "stars" to maxOf(oldStars, clampedStars),
                        "bestTimeMs" to minOf(oldBestTime, clampedTime),
                        "bestShards" to maxOf(oldBestShards, clampedShards),
                        "deaths" to minOf(oldDeaths, clampedDeaths),
                        "completed" to true,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }

            // Recompute total stars and unlock next campaign level
            val allProgress = db.collection("users").document(uid)
                .collection("campaign_progress").get().await()
            val totalStars = allProgress.documents.sumOf { (it.getLong("stars") ?: 0L) }.coerceIn(0L, 1000L)

            val userRef = db.collection("users").document(uid)
            val userSnap = userRef.get().await()
            val currentUnlocked = userSnap.getLong("unlockedCampaignIndex") ?: 0L
            val nextUnlock = (levelIndex + 1).coerceAtMost((totalCampaignLevels - 1).coerceAtLeast(0)).toLong()
            val updatedUnlock = maxOf(currentUnlocked, nextUnlock).coerceIn(0L, 50L)

            if (userSnap.exists()) {
                userRef.update(
                    mapOf(
                        "totalStars" to totalStars,
                        "unlockedCampaignIndex" to updatedUnlock,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            } else {
                userRef.set(
                    mapOf(
                        "userId" to uid,
                        "displayName" to (auth.currentUser?.displayName?.take(40)?.ifBlank { "Runner" } ?: "Runner"),
                        "totalStars" to totalStars,
                        "unlockedCampaignIndex" to updatedUnlock,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, progressPath)
            Result.failure(e)
        }
    }

    fun observeMyLevels(userId: String): Flow<List<DraftLevel>> = flow {
        if (userId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "users/$userId/my_levels"
        emitAll(
            db.collection("users").document(userId).collection("my_levels")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        DraftLevel::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    suspend fun saveDraftLevel(
        userId: String,
        draftId: String?,
        title: String,
        description: String,
        difficulty: LevelDifficulty,
        theme: LevelTheme,
        width: Int,
        height: Int,
        rows: List<String>,
        verified: Boolean,
        publishedLevelId: String?,
    ): Result<String> {
        var docPath = "users/$userId/my_levels"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Sesión cambiada; operación cancelada." }
            val cleanTitle = title.trim().ifBlank { "Nivel Neón" }.take(40)
            val cleanDesc = description.trim().take(160)
            val cleanWidth = width.coerceIn(24, 80)
            val cleanHeight = height.coerceIn(12, 16)
            val cleanRows = normalizeGrid(rows, cleanWidth, cleanHeight)

            val collectionRef = db.collection("users").document(uid).collection("my_levels")
            val docRef = if (draftId.isNullOrBlank()) collectionRef.document() else collectionRef.document(draftId)
            docPath = docRef.path

            if (draftId.isNullOrBlank()) {
                val payload = mutableMapOf<String, Any>(
                    "title" to cleanTitle,
                    "description" to cleanDesc,
                    "difficulty" to difficulty.wireValue,
                    "theme" to theme.wireValue,
                    "width" to cleanWidth.toLong(),
                    "height" to cleanHeight.toLong(),
                    "rows" to cleanRows,
                    "verified" to verified,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                if (!publishedLevelId.isNullOrBlank()) {
                    payload["publishedLevelId"] = publishedLevelId.take(128)
                }
                docRef.set(payload).await()
            } else {
                val updates = mutableMapOf<String, Any>(
                    "title" to cleanTitle,
                    "description" to cleanDesc,
                    "difficulty" to difficulty.wireValue,
                    "theme" to theme.wireValue,
                    "width" to cleanWidth.toLong(),
                    "height" to cleanHeight.toLong(),
                    "rows" to cleanRows,
                    "verified" to verified,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                if (!publishedLevelId.isNullOrBlank()) {
                    updates["publishedLevelId"] = publishedLevelId.take(128)
                }
                docRef.update(updates).await()
            }

            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteDraftLevel(userId: String, draftId: String): Result<Unit> {
        val docPath = "users/$userId/my_levels/$draftId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Sesión cambiada; operación cancelada." }
            require(draftId.isNotBlank()) { "ID de borrador vacío" }
            db.document(docPath).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    fun observeCommunityLevels(): Flow<List<CommunityLevel>> = flow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            emit(emptyList())
            return@flow
        }
        val path = "community_levels"
        emitAll(
            db.collection("community_levels")
                .whereEqualTo("status", "published")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CommunityLevel::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeUserLikedLevelIds(userId: String): Flow<Set<String>> = flow {
        if (userId.isBlank()) {
            emit(emptySet())
            return@flow
        }
        val path = "users/$userId/liked_levels"
        emitAll(
            db.collection("users").document(userId).collection("liked_levels")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        UserLikedLevel::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).map { it.levelId.ifBlank { it.id } }.toSet()
                }
                .catch { error ->
                    handleFlowError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    suspend fun publishLevelToCommunity(
        userId: String,
        authorName: String,
        draftId: String?,
        existingPublishedId: String?,
        title: String,
        description: String,
        difficulty: LevelDifficulty,
        theme: LevelTheme,
        width: Int,
        height: Int,
        rows: List<String>,
        verified: Boolean,
    ): Result<String> {
        var docPath = "community_levels"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Sesión cambiada; operación cancelada." }
            val cleanAuthor = authorName.trim().ifBlank { "Runner" }.take(40)
            val cleanTitle = title.trim().ifBlank { "Nivel Neón" }.take(40)
            val cleanDesc = description.trim().take(160)
            val cleanWidth = width.coerceIn(24, 80)
            val cleanHeight = height.coerceIn(12, 16)
            val cleanRows = normalizeGrid(rows, cleanWidth, cleanHeight)

            val commColl = db.collection("community_levels")
            val existingSnap = if (!existingPublishedId.isNullOrBlank()) {
                commColl.document(existingPublishedId).get().await()
            } else null

            val commRef = if (existingSnap != null && existingSnap.exists() && existingSnap.getString("authorId") == uid) {
                commColl.document(existingSnap.id)
            } else {
                commColl.document()
            }
            docPath = commRef.path

            if (existingSnap != null && existingSnap.exists() && existingSnap.getString("authorId") == uid) {
                commRef.update(
                    mapOf(
                        "authorName" to cleanAuthor,
                        "title" to cleanTitle,
                        "description" to cleanDesc,
                        "difficulty" to difficulty.wireValue,
                        "theme" to theme.wireValue,
                        "width" to cleanWidth.toLong(),
                        "height" to cleanHeight.toLong(),
                        "rows" to cleanRows,
                        "verified" to verified,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            } else {
                commRef.set(
                    mapOf(
                        "authorId" to uid,
                        "authorName" to cleanAuthor,
                        "title" to cleanTitle,
                        "description" to cleanDesc,
                        "difficulty" to difficulty.wireValue,
                        "theme" to theme.wireValue,
                        "width" to cleanWidth.toLong(),
                        "height" to cleanHeight.toLong(),
                        "rows" to cleanRows,
                        "verified" to verified,
                        "status" to "published",
                        "playsCount" to 0L,
                        "likesCount" to 0L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            }

            if (!draftId.isNullOrBlank()) {
                db.collection("users").document(uid)
                    .collection("my_levels").document(draftId)
                    .update(
                        mapOf(
                            "publishedLevelId" to commRef.id,
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    ).await()
            }

            Result.success(commRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCommunityLevel(userId: String, levelId: String): Result<Unit> {
        val docPath = "community_levels/$levelId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Sesión cambiada; operación cancelada." }
            require(levelId.isNotBlank()) { "ID de nivel vacío" }
            db.document(docPath).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun incrementCommunityPlay(levelId: String): Result<Unit> {
        val docPath = "community_levels/$levelId"
        return try {
            requireUserId()
            require(levelId.isNotBlank()) { "ID de nivel vacío" }
            db.collection("community_levels").document(levelId)
                .update(
                    mapOf(
                        "playsCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleCommunityLike(
        userId: String,
        levelId: String,
        currentlyLiked: Boolean,
    ): Result<Unit> {
        val docPath = "community_levels/$levelId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Sesión cambiada; operación cancelada." }
            require(levelId.isNotBlank()) { "ID de nivel vacío" }

            val levelRef = db.collection("community_levels").document(levelId)
            val likeRef = levelRef.collection("likes").document(uid)
            val userLikeRef = db.collection("users").document(uid)
                .collection("liked_levels").document(levelId)

            val batch = db.batch()
            if (currentlyLiked) {
                batch.delete(likeRef)
                batch.delete(userLikeRef)
                batch.update(
                    levelRef,
                    mapOf(
                        "likesCount" to FieldValue.increment(-1L),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            } else {
                batch.set(
                    likeRef,
                    mapOf(
                        "userId" to uid,
                        "createdAt" to FieldValue.serverTimestamp(),
                    )
                )
                batch.set(
                    userLikeRef,
                    mapOf(
                        "levelId" to levelId,
                        "createdAt" to FieldValue.serverTimestamp(),
                    )
                )
                batch.update(
                    levelRef,
                    mapOf(
                        "likesCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    private fun handleFlowError(
        error: Throwable,
        operationType: OperationType,
        path: String,
        expectedUid: String,
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }

    private fun normalizeGrid(rows: List<String>, width: Int, height: Int): List<String> {
        val paddedRows = MutableList(height) { idx ->
            val src = rows.getOrNull(idx) ?: ".".repeat(width)
            if (src.length < width) src.padEnd(width, '.') else src.take(width)
        }
        return paddedRows
    }
}
