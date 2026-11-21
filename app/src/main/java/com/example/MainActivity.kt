package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.attemptAutoSignIn
import com.example.auth.onGoogleSignInClicked
import com.example.auth.signOut
import com.example.data.CampaignLevels
import com.example.data.LocalGameStorage
import com.example.data.NeonRunnerRepository
import com.example.model.LevelData
import com.example.ui.screens.GamePlayScreen
import com.example.ui.screens.HomeHubScreen
import com.example.ui.screens.LevelEditorScreen
import com.example.ui.screens.UsernameSetupScreen
import com.example.ui.theme.NeonRunnerTheme
import com.example.viewmodel.NeonRunnerViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

sealed interface ActiveRoute {
    data object Hub : ActiveRoute
    data class Playing(
        val level: LevelData,
        val isPlaytest: Boolean = false
    ) : ActiveRoute
    data class Editor(
        val level: LevelData,
        val verified: Boolean
    ) : ActiveRoute
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NeonRunnerTheme {
                NeonRunnerApp()
            }
        }
    }
}

@Composable
fun NeonRunnerApp(auth: FirebaseAuth = Firebase.auth) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val localStorage = remember(context) { LocalGameStorage(context) }

    val username by localStorage.username.collectAsStateWithLifecycle()
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {},
            onUnauthenticated = {},
            scope = scope
        )
    }

    val activeName = username
    if (activeName.isNullOrBlank()) {
        UsernameSetupScreen(
            onConfirmUsername = { chosen ->
                localStorage.setUsername(chosen)
            }
        )
    } else {
        val cloudUid = currentUser?.uid
        key(cloudUid ?: "local", activeName) {
            NeonRunnerFlow(
                localStorage = localStorage,
                runnerUsername = activeName,
                cloudUserId = cloudUid
            )
        }
    }
}

@Composable
private fun NeonRunnerFlow(
    localStorage: LocalGameStorage,
    runnerUsername: String,
    cloudUserId: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: NeonRunnerViewModel = viewModel(
        key = cloudUserId ?: "local_$runnerUsername",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                NeonRunnerViewModel(
                    localStorage = localStorage,
                    repository = NeonRunnerRepository(db),
                    currentUserId = cloudUserId,
                    runnerUsername = runnerUsername
                )
            }
        }
    )

    var route by remember { mutableStateOf<ActiveRoute>(ActiveRoute.Hub) }
    var playtestPassed by remember { mutableStateOf(false) }

    fun triggerOptionalCloudSignIn(onSuccess: () -> Unit, onError: (String) -> Unit) {
        onGoogleSignInClicked(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onSuccess,
            onAuthError = onError,
            scope = scope,
            onAuthCancelled = {}
        )
    }

    when (val active = route) {
        ActiveRoute.Hub -> {
            HomeHubScreen(
                viewModel = viewModel,
                userDisplayName = runnerUsername,
                isCloudConnected = viewModel.isCloudConnected,
                onPlayCampaignLevel = { lvl ->
                    playtestPassed = false
                    route = ActiveRoute.Playing(level = lvl, isPlaytest = false)
                },
                onPlayCommunityLevel = { lvl ->
                    playtestPassed = false
                    viewModel.recordCommunityPlay(lvl.id)
                    route = ActiveRoute.Playing(level = lvl, isPlaytest = false)
                },
                onCreateNewMap = {
                    playtestPassed = false
                    route = ActiveRoute.Editor(
                        level = LevelData(title = "Nuevo Sector"),
                        verified = false
                    )
                },
                onEditMap = { lvl, verified ->
                    playtestPassed = false
                    route = ActiveRoute.Editor(level = lvl, verified = verified)
                },
                onConnectCloud = ::triggerOptionalCloudSignIn,
                onDisconnectCloud = {
                    signOut(credentialManager = credentialManager, scope = scope)
                }
            )
        }

        is ActiveRoute.Editor -> {
            LevelEditorScreen(
                initialLevel = active.level,
                initialVerified = active.verified,
                isCloudConnected = viewModel.isCloudConnected,
                onExit = { route = ActiveRoute.Hub },
                onPlaytest = { draftLevel ->
                    playtestPassed = false
                    route = ActiveRoute.Playing(level = draftLevel, isPlaytest = true)
                },
                onSaveDraft = { levelToSave, isVerified ->
                    viewModel.saveDraft(
                        level = levelToSave,
                        verified = isVerified
                    ) { saved ->
                        route = ActiveRoute.Editor(level = saved, verified = isVerified)
                    }
                },
                onPublishOnline = { levelToPublish ->
                    val isPublishedEdit = levelToPublish.published && levelToPublish.id.isNotBlank()
                    val existingCommId = if (isPublishedEdit) levelToPublish.id else levelToPublish.publishedLevelId
                    val localDraftId = if (!isPublishedEdit) levelToPublish.id.takeIf { it.isNotBlank() } else null
                    viewModel.publishLevel(
                        level = levelToPublish,
                        existingCommunityId = existingCommId,
                        localDraftId = localDraftId
                    ) {
                        route = ActiveRoute.Hub
                    }
                },
                onConnectCloud = ::triggerOptionalCloudSignIn
            )
        }

        is ActiveRoute.Playing -> {
            val lvl = active.level
            val hasNextCampaign = lvl.isCampaign && lvl.campaignIndex in 0 until (CampaignLevels.levels.size - 1)
            GamePlayScreen(
                level = lvl,
                isPlaytest = active.isPlaytest,
                hasNextCampaignLevel = hasNextCampaign,
                onExit = {
                    route = if (active.isPlaytest) {
                        val isNowVerified = lvl.verified || playtestPassed
                        ActiveRoute.Editor(
                            level = lvl.copy(verified = isNowVerified),
                            verified = isNowVerified
                        )
                    } else {
                        ActiveRoute.Hub
                    }
                },
                onVictory = { stars, timeMs, score ->
                    if (active.isPlaytest) {
                        playtestPassed = true
                    } else if (lvl.isCampaign) {
                        viewModel.recordCampaignCompletion(
                            levelId = lvl.id,
                            levelIndex = lvl.campaignIndex,
                            stars = stars,
                            timeMs = timeMs,
                            score = score
                        )
                    }
                },
                onNextLevel = {
                    if (hasNextCampaign) {
                        val nextLvl = CampaignLevels.levels[lvl.campaignIndex + 1]
                        route = ActiveRoute.Playing(level = nextLvl, isPlaytest = false)
                    }
                }
            )
        }
    }
}
