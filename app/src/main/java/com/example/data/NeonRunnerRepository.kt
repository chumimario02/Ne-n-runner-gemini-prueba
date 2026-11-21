package com.example.data

import android.content.Context
import com.example.R
import com.example.model.LEVEL_GRID_HEIGHT
import com.example.model.LevelData
import com.example.model.MAX_LEVEL_WIDTH
import com.example.model.MIN_LEVEL_WIDTH
import com.example.model.resizeGridRows
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
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Debes iniciar sesión con Google antes de acceder a Firestore.")
    }

    fun observeCampaignProgress(userId: String): Flow<List<CampaignProgressDoc>> = flow {
        require(userId.isNotBlank()) { "userId no puede estar vacío" }
        val path = "users/$userId/progress"
        emitAll(
            db.collection("users").document(userId).collection("progress")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CampaignProgressDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveCampaignProgress(
        userId: String,
        levelId: String,
        levelIndex: Int,
        stars: Int,
        timeMs: Long,
        score: Long
    ): Result<Unit> {
        val path = "users/$userId/progress/$levelId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "El usuario autenticado cambió; operación cancelada." }
            require(levelId.isNotBlank() && levelId.length <= 128) { "ID de nivel inválido" }
            val safeStars = stars.coerceIn(0, 3).toLong()
            val safeTime = timeMs.coerceIn(0L, 86_400_000L)
            val safeScore = score.coerceIn(0L, 10_000_000L)
            val safeIndex = levelIndex.coerceIn(0, 50).toLong()

            val docRef = db.collection("users").document(uid)
                .collection("progress").document(levelId)
            val existing = docRef.get().await()

            if (existing.exists()) {
                val prevStars = existing.getLong("stars") ?: 0L
                val prevBestTime = existing.getLong("bestTimeMs") ?: Long.MAX_VALUE
                val prevBestScore = existing.getLong("bestScore") ?: 0L
                val bestTime = if (prevBestTime <= 0L) safeTime else minOf(prevBestTime, safeTime)

                docRef.update(
                    mapOf(
                        "completed" to true,
                        "stars" to maxOf(prevStars, safeStars),
                        "bestTimeMs" to bestTime,
                        "bestScore" to maxOf(prevBestScore, safeScore),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "levelIndex" to safeIndex,
                        "completed" to true,
                        "stars" to safeStars,
                        "bestTimeMs" to safeTime,
                        "bestScore" to safeScore,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeMyDrafts(userId: String): Flow<List<CustomDraftDoc>> = flow {
        require(userId.isNotBlank()) { "userId no puede estar vacío" }
        val path = "users/$userId/drafts"
        emitAll(
            db.collection("users").document(userId).collection("drafts")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CustomDraftDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveDraft(
        userId: String,
        draftId: String?,
        level: LevelData,
        verified: Boolean,
        publishedLevelId: String? = null
    ): Result<String> {
        var docPath = "users/$userId/drafts"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "El usuario autenticado cambió; operación cancelada." }
            val cleanTitle = level.title.trim().take(40).ifBlank { "Sector Personalizado" }
            val cleanDesc = level.description.trim().take(160)
            val safeWidth = level.width.coerceIn(MIN_LEVEL_WIDTH, MAX_LEVEL_WIDTH)
            val safeRows = resizeGridRows(level.gridRows, safeWidth)
            require(safeRows.size == LEVEL_GRID_HEIGHT && safeRows[0].length == safeWidth) {
                "Dimensiones de mapa inválidas."
            }

            val colRef = db.collection("users").document(uid).collection("drafts")
            val docRef = if (!draftId.isNullOrBlank()) colRef.document(draftId) else colRef.document()
            docPath = docRef.path

            val linkedId = publishedLevelId?.takeIf { it.isNotBlank() }?.take(128)
            if (!draftId.isNullOrBlank()) {
                val updateMap = mutableMapOf<String, Any?>(
                    "title" to cleanTitle,
                    "description" to cleanDesc,
                    "difficulty" to level.difficulty.name,
                    "width" to safeWidth.toLong(),
                    "height" to LEVEL_GRID_HEIGHT.toLong(),
                    "gridRows" to safeRows,
                    "verified" to verified,
                    "publishedLevelId" to linkedId,
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.update(updateMap).await()
            } else {
                val createMap = mutableMapOf<String, Any?>(
                    "userId" to uid,
                    "title" to cleanTitle,
                    "description" to cleanDesc,
                    "difficulty" to level.difficulty.name,
                    "width" to safeWidth.toLong(),
                    "height" to LEVEL_GRID_HEIGHT.toLong(),
                    "gridRows" to safeRows,
                    "verified" to verified,
                    "publishedLevelId" to linkedId,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(createMap).await()
            }
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteDraft(userId: String, draftId: String): Result<Unit> {
        val docPath = "users/$userId/drafts/$draftId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "El usuario autenticado cambió; operación cancelada." }
            require(draftId.isNotBlank()) { "ID de borrador inválido" }
            db.collection("users").document(uid).collection("drafts").document(draftId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    fun observeCommunityLevels(): Flow<List<CommunityLevelDoc>> = flow {
        val uid = requireUserId()
        val path = "community_levels"
        emitAll(
            db.collection(path)
                .whereEqualTo("published", true)
                .limit(60)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CommunityLevelDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeMyPublishedLevels(userId: String): Flow<List<CommunityLevelDoc>> = flow {
        require(userId.isNotBlank()) { "userId no puede estar vacío" }
        val path = "community_levels"
        emitAll(
            db.collection(path)
                .whereEqualTo("authorId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CommunityLevelDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun publishLevel(
        userId: String,
        authorName: String,
        level: LevelData,
        existingCommunityId: String?,
        draftId: String?
    ): Result<String> {
        var docPath = "community_levels"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "El usuario autenticado cambió; operación cancelada." }
            val cleanTitle = level.title.trim().take(40)
            require(cleanTitle.length >= 2) { "El título debe tener al menos 2 caracteres." }
            val cleanDesc = level.description.trim().take(160)
            val safeAuthor = authorName.trim().take(40).ifBlank { "Runner" }
            val safeWidth = level.width.coerceIn(MIN_LEVEL_WIDTH, MAX_LEVEL_WIDTH)
            val safeRows = resizeGridRows(level.gridRows, safeWidth)

            val colRef = db.collection("community_levels")
            val docRef = if (!existingCommunityId.isNullOrBlank()) {
                colRef.document(existingCommunityId)
            } else {
                colRef.document()
            }
            docPath = docRef.path

            val existingSnap = if (!existingCommunityId.isNullOrBlank()) docRef.get().await() else null
            if (existingSnap != null && existingSnap.exists()) {
                docRef.update(
                    mapOf(
                        "authorName" to safeAuthor,
                        "title" to cleanTitle,
                        "description" to cleanDesc,
                        "difficulty" to level.difficulty.name,
                        "width" to safeWidth.toLong(),
                        "height" to LEVEL_GRID_HEIGHT.toLong(),
                        "gridRows" to safeRows,
                        "verified" to level.verified,
                        "published" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "authorId" to uid,
                        "authorName" to safeAuthor,
                        "title" to cleanTitle,
                        "description" to cleanDesc,
                        "difficulty" to level.difficulty.name,
                        "width" to safeWidth.toLong(),
                        "height" to LEVEL_GRID_HEIGHT.toLong(),
                        "gridRows" to safeRows,
                        "verified" to level.verified,
                        "published" to true,
                        "playsCount" to 0L,
                        "likesCount" to 0L,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            if (!draftId.isNullOrBlank()) {
                val draftRef = db.collection("users").document(uid)
                    .collection("drafts").document(draftId)
                draftRef.update(
                    mapOf(
                        "publishedLevelId" to docRef.id,
                        "verified" to level.verified,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            Result.success(docRef.id)
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
            check(uid == userId) { "El usuario autenticado cambió; operación cancelada." }
            require(levelId.isNotBlank()) { "ID inválido" }
            db.collection("community_levels").document(levelId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun incrementPlayCount(levelId: String): Result<Unit> {
        val docPath = "community_levels/$levelId"
        return try {
            requireUserId()
            require(levelId.isNotBlank()) { "ID inválido" }
            db.collection("community_levels").document(levelId).update(
                mapOf(
                    "playsCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
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

    suspend fun hasUserLiked(levelId: String, userId: String): Boolean {
        val path = "community_levels/$levelId/likes/$userId"
        return try {
            val uid = requireUserId()
            if (uid != userId || levelId.isBlank()) return false
            val snap = db.collection("community_levels").document(levelId)
                .collection("likes").document(uid).get().await()
            snap.exists()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            false
        }
    }

    suspend fun toggleLike(
        levelId: String,
        userId: String,
        currentlyLiked: Boolean
    ): Result<Boolean> {
        val path = "community_levels/$levelId/likes/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "El usuario autenticado cambió; operación cancelada." }
            require(levelId.isNotBlank()) { "ID de nivel inválido" }

            val levelRef = db.collection("community_levels").document(levelId)
            val likeRef = levelRef.collection("likes").document(uid)
            val batch = db.batch()
            if (currentlyLiked) {
                batch.delete(likeRef)
                batch.update(
                    levelRef,
                    mapOf(
                        "likesCount" to FieldValue.increment(-1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                batch.set(
                    likeRef,
                    mapOf(
                        "userId" to uid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.update(
                    levelRef,
                    mapOf(
                        "likesCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(!currentlyLiked)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }
}
