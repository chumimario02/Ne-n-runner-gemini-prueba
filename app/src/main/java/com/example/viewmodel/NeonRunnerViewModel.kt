package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CampaignProgressDoc
import com.example.data.CommunityLevelDoc
import com.example.data.LocalGameStorage
import com.example.data.NeonRunnerRepository
import com.example.model.LevelData
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class NeonRunnerViewModel(
    private val localStorage: LocalGameStorage,
    private val repository: NeonRunnerRepository,
    private val currentUserId: String?,
    private val runnerUsername: String
) : ViewModel() {

    val isCloudConnected: Boolean = !currentUserId.isNullOrBlank()

    val campaignProgress: StateFlow<UiState<List<CampaignProgressDoc>>> =
        if (currentUserId.isNullOrBlank()) {
            localStorage.progress
                .map<List<CampaignProgressDoc>, UiState<List<CampaignProgressDoc>>> { UiState.Success(it) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = UiState.Success(localStorage.progress.value)
                )
        } else {
            combine(
                localStorage.progress,
                repository.observeCampaignProgress(currentUserId)
            ) { localList, cloudList ->
                val merged = mutableMapOf<String, CampaignProgressDoc>()
                localList.forEach { merged[it.id] = it }
                cloudList.forEach { cloud ->
                    val prev = merged[cloud.id]
                    if (prev == null) {
                        merged[cloud.id] = cloud
                    } else {
                        val bestTime = when {
                            prev.bestTimeMs <= 0L -> cloud.bestTimeMs
                            cloud.bestTimeMs <= 0L -> prev.bestTimeMs
                            else -> minOf(prev.bestTimeMs, cloud.bestTimeMs)
                        }
                        merged[cloud.id] = cloud.copy(
                            completed = prev.completed || cloud.completed,
                            stars = maxOf(prev.stars, cloud.stars),
                            bestTimeMs = bestTime,
                            bestScore = maxOf(prev.bestScore, cloud.bestScore)
                        )
                    }
                }
                merged.values.toList()
            }
                .map<List<CampaignProgressDoc>, UiState<List<CampaignProgressDoc>>> { UiState.Success(it) }
                .catch { error ->
                    val firestoreError = unwrapFirestoreError(error)
                    Log.w(TAG, "Error observing campaign progress", firestoreError)
                    emit(UiState.Success(localStorage.progress.value))
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = UiState.Success(localStorage.progress.value)
                )
        }

    val myDrafts: StateFlow<UiState<List<LevelData>>> =
        localStorage.drafts
            .map<List<LevelData>, UiState<List<LevelData>>> { UiState.Success(it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Success(localStorage.drafts.value)
            )

    val communityLevels: StateFlow<UiState<List<CommunityLevelDoc>>> =
        if (currentUserId.isNullOrBlank()) {
            flowOf<UiState<List<CommunityLevelDoc>>>(UiState.Success(emptyList()))
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = UiState.Success(emptyList())
                )
        } else {
            repository.observeCommunityLevels()
                .map<List<CommunityLevelDoc>, UiState<List<CommunityLevelDoc>>> { list ->
                    UiState.Success(list.sortedByDescending { it.createdAt?.seconds ?: 0L })
                }
                .catch { error ->
                    val firestoreError = unwrapFirestoreError(error)
                    Log.w(TAG, "Error observing community levels", firestoreError)
                    emit(UiState.Error(firestoreError.message ?: "Error al cargar mapas en línea"))
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = UiState.Loading
                )
        }

    val myPublishedLevels: StateFlow<UiState<List<CommunityLevelDoc>>> =
        if (currentUserId.isNullOrBlank()) {
            flowOf<UiState<List<CommunityLevelDoc>>>(UiState.Success(emptyList()))
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = UiState.Success(emptyList())
                )
        } else {
            repository.observeMyPublishedLevels(currentUserId)
                .map<List<CommunityLevelDoc>, UiState<List<CommunityLevelDoc>>> { list ->
                    UiState.Success(list.sortedByDescending { it.updatedAt?.seconds ?: 0L })
                }
                .catch { error ->
                    val firestoreError = unwrapFirestoreError(error)
                    Log.w(TAG, "Error observing published levels", firestoreError)
                    emit(UiState.Error(firestoreError.message ?: "Error al cargar tus mapas publicados"))
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = UiState.Loading
                )
        }

    private val _likedIds = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val likedIds: StateFlow<Map<String, Boolean>> = _likedIds.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun showActionMessage(msg: String) {
        _actionMessage.value = msg
    }

    fun updateUsername(newName: String) {
        val trimmed = newName.trim()
        if (trimmed.length < 2) {
            _actionMessage.value = "El nombre debe tener al menos 2 caracteres."
            return
        }
        localStorage.setUsername(trimmed)
        _actionMessage.value = "Nombre actualizado a $trimmed."
    }

    fun recordCampaignCompletion(
        levelId: String,
        levelIndex: Int,
        stars: Int,
        timeMs: Long,
        score: Long
    ) {
        localStorage.saveCampaignProgress(
            levelId = levelId,
            levelIndex = levelIndex,
            stars = stars,
            timeMs = timeMs,
            score = score
        )
        val uid = currentUserId ?: return
        viewModelScope.launch {
            repository.saveCampaignProgress(
                userId = uid,
                levelId = levelId,
                levelIndex = levelIndex,
                stars = stars,
                timeMs = timeMs,
                score = score
            )
        }
    }

    fun recordCommunityPlay(levelId: String) {
        if (currentUserId.isNullOrBlank() || levelId.isBlank() || levelId.startsWith("local_")) return
        viewModelScope.launch {
            repository.incrementPlayCount(levelId)
        }
    }

    fun checkIfLiked(levelId: String) {
        val uid = currentUserId ?: return
        if (levelId.isBlank() || _likedIds.value.containsKey(levelId)) return
        viewModelScope.launch {
            val liked = repository.hasUserLiked(levelId, uid)
            _likedIds.value = _likedIds.value + (levelId to liked)
        }
    }

    fun toggleLike(levelId: String) {
        val uid = currentUserId ?: return
        if (levelId.isBlank()) return
        viewModelScope.launch {
            val current = _likedIds.value[levelId] ?: repository.hasUserLiked(levelId, uid)
            repository.toggleLike(levelId, uid, current)
                .onSuccess { newLiked ->
                    _likedIds.value = _likedIds.value + (levelId to newLiked)
                }
                .onFailure { err ->
                    _actionMessage.value = "No se pudo actualizar Me gusta: ${err.localizedMessage}"
                }
        }
    }

    fun saveDraft(
        level: LevelData,
        verified: Boolean,
        onSaved: (LevelData) -> Unit
    ) {
        val savedLocal = localStorage.saveDraft(level, verified)
        _actionMessage.value = "Mapa guardado en tu taller."
        onSaved(savedLocal)
    }

    fun publishLevel(
        level: LevelData,
        existingCommunityId: String?,
        localDraftId: String?,
        onPublished: (String) -> Unit
    ) {
        val uid = currentUserId
        if (uid.isNullOrBlank()) {
            _actionMessage.value = "Conecta la nube en Comunidad para publicar en línea."
            return
        }
        viewModelScope.launch {
            repository.publishLevel(
                userId = uid,
                authorName = runnerUsername,
                level = level,
                existingCommunityId = existingCommunityId,
                draftId = null
            ).onSuccess { commId ->
                if (!localDraftId.isNullOrBlank()) {
                    localStorage.linkDraftPublishedId(localDraftId, commId, level.verified)
                }
                _actionMessage.value = "¡Mapa publicado en línea por $runnerUsername!"
                onPublished(commId)
            }.onFailure { err ->
                _actionMessage.value = "Error al publicar: ${err.localizedMessage}"
            }
        }
    }

    fun deleteDraft(draftId: String) {
        localStorage.deleteDraft(draftId)
        _actionMessage.value = "Mapa eliminado de tu taller."
    }

    fun deletePublishedLevel(levelId: String) {
        val uid = currentUserId ?: return
        viewModelScope.launch {
            repository.deleteCommunityLevel(uid, levelId)
                .onSuccess { _actionMessage.value = "Mapa retirado de la comunidad." }
                .onFailure { err -> _actionMessage.value = "Error al retirar mapa: ${err.localizedMessage}" }
        }
    }

    private fun unwrapFirestoreError(error: Throwable): Throwable {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        return firestoreError ?: error
    }

    private companion object {
        const val TAG = "NeonRunnerVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
