package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.CampaignLevels
import com.example.data.CampaignProgressDoc
import com.example.data.CommunityLevelDoc
import com.example.model.Difficulty
import com.example.model.LEVEL_GRID_HEIGHT
import com.example.model.LevelData
import com.example.model.TileType
import com.example.model.validateLevelGrid
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCardBorder
import com.example.ui.theme.NeonCrimson
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonLime
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonSurface
import com.example.ui.theme.NeonSurfaceVariant
import com.example.ui.theme.NeonVoid
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.NeonRunnerViewModel
import com.example.viewmodel.UiState

enum class HubTab(val labelEs: String, val icon: ImageVector) {
    CAMPAIGN("Campaña", Icons.Rounded.SportsEsports),
    COMMUNITY("Comunidad", Icons.Rounded.Public),
    WORKSHOP("Taller", Icons.Rounded.Build)
}

enum class CommunitySort(val labelEs: String) {
    RECENT("Recientes"),
    LIKED("Más gustados"),
    PLAYED("Populares")
}

@Composable
fun HomeHubScreen(
    viewModel: NeonRunnerViewModel,
    userDisplayName: String,
    isCloudConnected: Boolean,
    onPlayCampaignLevel: (LevelData) -> Unit,
    onPlayCommunityLevel: (LevelData) -> Unit,
    onCreateNewMap: () -> Unit,
    onEditMap: (LevelData, Boolean) -> Unit,
    onConnectCloud: (onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    onDisconnectCloud: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(HubTab.CAMPAIGN) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showDisconnectConfirm by remember { mutableStateOf(false) }
    var cloudError by remember { mutableStateOf<String?>(null) }

    val progressState by viewModel.campaignProgress.collectAsStateWithLifecycle()
    val communityState by viewModel.communityLevels.collectAsStateWithLifecycle()
    val draftsState by viewModel.myDrafts.collectAsStateWithLifecycle()
    val myPublishedState by viewModel.myPublishedLevels.collectAsStateWithLifecycle()
    val likedIds by viewModel.likedIds.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(actionMessage) {
        val msg = actionMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearActionMessage()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonVoid)
    ) {
        val isWide = maxWidth >= 700.dp

        Scaffold(
            containerColor = NeonVoid,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                Surface(
                    color = NeonSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, NeonCardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showEditNameDialog = true }
                                .padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = "NEON RUNNER",
                                style = MaterialTheme.typography.titleLarge,
                                color = NeonCyan
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Runner: $userDisplayName",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = "Cambiar nombre",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = if (isCloudConnected) NeonLime.copy(alpha = 0.14f) else NeonSurfaceVariant,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        1.dp,
                                        if (isCloudConnected) NeonLime.copy(alpha = 0.5f) else NeonCardBorder,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        if (isCloudConnected) {
                                            showDisconnectConfirm = true
                                        } else {
                                            cloudError = null
                                            onConnectCloud({}, { err -> cloudError = err })
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isCloudConnected) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff,
                                        contentDescription = null,
                                        tint = if (isCloudConnected) NeonLime else TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isCloudConnected) "En línea" else "Local",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isCloudConnected) NeonLime else TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                if (!isWide) {
                    NavigationBar(
                        containerColor = NeonSurface,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        HubTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.labelEs
                                    )
                                },
                                label = { Text(tab.labelEs) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = NeonCyan,
                                    selectedTextColor = NeonCyan,
                                    indicatorColor = NeonCyan.copy(alpha = 0.18f),
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("nav_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWide) {
                    NavigationRail(
                        containerColor = NeonSurface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HubTab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(imageVector = tab.icon, contentDescription = tab.labelEs)
                                },
                                label = { Text(tab.labelEs) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = NeonCyan,
                                    selectedTextColor = NeonCyan,
                                    indicatorColor = NeonCyan.copy(alpha = 0.18f),
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("rail_${tab.name.lowercase()}")
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(modifier = Modifier.widthIn(max = 820.dp).fillMaxSize()) {
                        when (selectedTab) {
                            HubTab.CAMPAIGN -> CampaignTabContent(
                                progressState = progressState,
                                onPlayLevel = onPlayCampaignLevel
                            )
                            HubTab.COMMUNITY -> CommunityTabContent(
                                isCloudConnected = isCloudConnected,
                                cloudError = cloudError,
                                communityState = communityState,
                                likedIds = likedIds,
                                onConnectCloud = {
                                    cloudError = null
                                    onConnectCloud({}, { err -> cloudError = err })
                                },
                                onCheckLiked = viewModel::checkIfLiked,
                                onToggleLike = viewModel::toggleLike,
                                onPlayLevel = onPlayCommunityLevel,
                                onGoToWorkshop = { selectedTab = HubTab.WORKSHOP }
                            )
                            HubTab.WORKSHOP -> WorkshopTabContent(
                                isCloudConnected = isCloudConnected,
                                draftsState = draftsState,
                                publishedState = myPublishedState,
                                onCreateNewMap = onCreateNewMap,
                                onEditMap = onEditMap,
                                onPlayMap = onPlayCommunityLevel,
                                onPublishDraft = { draft ->
                                    val check = validateLevelGrid(draft.gridRows, draft.width)
                                    if (!check.isValid) {
                                        viewModel.showActionMessage(check.errorEs ?: "Mapa incompleto")
                                    } else if (!isCloudConnected) {
                                        onConnectCloud(
                                            {
                                                viewModel.publishLevel(
                                                    level = draft,
                                                    existingCommunityId = draft.publishedLevelId,
                                                    localDraftId = draft.id,
                                                    onPublished = {}
                                                )
                                            },
                                            { err -> viewModel.showActionMessage(err) }
                                        )
                                    } else {
                                        viewModel.publishLevel(
                                            level = draft,
                                            existingCommunityId = draft.publishedLevelId,
                                            localDraftId = draft.id,
                                            onPublished = {}
                                        )
                                    }
                                },
                                onDeleteDraft = viewModel::deleteDraft,
                                onDeletePublished = viewModel::deletePublishedLevel
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditNameDialog) {
        var newName by remember(userDisplayName) { mutableStateOf(userDisplayName) }
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            containerColor = NeonSurface,
            title = { Text("Cambiar nombre", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { if (it.length <= 24) newName = it },
                    label = { Text("Nombre de jugador") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateUsername(newName)
                        showEditNameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = NeonVoid)
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }

    if (showDisconnectConfirm) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirm = false },
            containerColor = NeonSurface,
            title = { Text("Desconectar nube", color = TextPrimary) },
            text = {
                Text(
                    "Puedes seguir jugando la campaña y usando el editor localmente con tu nombre sin conexión.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDisconnectConfirm = false
                        onDisconnectCloud()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta, contentColor = TextPrimary)
                ) {
                    Text("Pasar a Modo Local")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectConfirm = false }) {
                    Text("Cancelar", color = NeonCyan)
                }
            }
        )
    }
}

@Composable
private fun CampaignTabContent(
    progressState: UiState<List<CampaignProgressDoc>>,
    onPlayLevel: (LevelData) -> Unit
) {
    val progressMap = remember(progressState) {
        when (progressState) {
            is UiState.Success -> progressState.data.associateBy { it.id }
            else -> emptyMap()
        }
    }

    val totalStars = progressMap.values.sumOf { it.stars.toInt() }
    val completedCount = progressMap.values.count { it.completed }
    val totalScore = progressMap.values.sumOf { it.bestScore }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(155.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.2.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_neon_hero_banner),
                    contentDescription = "Sector de campaña Neon Runner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(NeonVoid.copy(alpha = 0.92f), NeonVoid.copy(alpha = 0.45f))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "MODO CAMPAÑA",
                            style = MaterialTheme.typography.labelMedium,
                            color = NeonCyan
                        )
                        Text(
                            text = "Sectores Progresivos",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        CampaignStat("Estrellas", "$totalStars/${CampaignLevels.levels.size * 3}", NeonAmber)
                        CampaignStat("Sectores", "$completedCount/${CampaignLevels.levels.size}", NeonLime)
                        CampaignStat("Récord", totalScore.toString(), NeonCyan)
                    }
                }
            }
        }

        items(CampaignLevels.levels, key = { it.id }) { level ->
            val doc = progressMap[level.id]
            val prevCompleted = if (level.campaignIndex == 0) {
                true
            } else {
                val prevId = CampaignLevels.levels[level.campaignIndex - 1].id
                progressMap[prevId]?.completed == true
            }
            val stars = (doc?.stars ?: 0L).toInt()

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (prevCompleted) NeonSurface else NeonSurface.copy(alpha = 0.65f)
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = if (prevCompleted) level.difficulty.accentColor.copy(alpha = 0.45f) else NeonCardBorder,
                        shape = RoundedCornerShape(18.dp)
                    )
                    .clickable(enabled = prevCompleted) { onPlayLevel(level) }
                    .testTag("campaign_card_${level.id}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DifficultyBadge(level.difficulty)
                            if (doc?.completed == true) {
                                Text(
                                    text = formatElapsed(doc.bestTimeMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeonCyan
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = level.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (prevCompleted) TextPrimary else TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (prevCompleted) {
                                level.description
                            } else {
                                "Completa el sector anterior para desbloquear."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (s in 1..3) {
                                Icon(
                                    imageVector = if (s <= stars) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                    contentDescription = null,
                                    tint = if (s <= stars) NeonAmber else TextSecondary.copy(alpha = 0.45f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    if (prevCompleted) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(level.difficulty.accentColor.copy(alpha = 0.2f))
                                .border(1.5.dp, level.difficulty.accentColor, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Jugar ${level.title}",
                                tint = level.difficulty.accentColor,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(NeonSurfaceVariant)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = "Nivel bloqueado",
                                tint = TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityTabContent(
    isCloudConnected: Boolean,
    cloudError: String?,
    communityState: UiState<List<CommunityLevelDoc>>,
    likedIds: Map<String, Boolean>,
    onConnectCloud: () -> Unit,
    onCheckLiked: (String) -> Unit,
    onToggleLike: (String) -> Unit,
    onPlayLevel: (LevelData) -> Unit,
    onGoToWorkshop: () -> Unit
) {
    if (!isCloudConnected) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = NeonSurface),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, NeonCyan.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Public,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Comunidad en Línea",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tu campaña y tu editor funcionan sin cuenta. Si quieres descargar y publicar mapas con otros jugadores en la nube de Firebase, conecta tu cuenta de Google aquí.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    if (cloudError != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        ErrorBanner(cloudError)
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = onConnectCloud,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = NeonVoid),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("connect_cloud_button")
                    ) {
                        Text(
                            text = "Conectar con Google",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onGoToWorkshop,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Ir al Taller Local", color = NeonMagenta)
                    }
                }
            }
        }
        return
    }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var filterDiff by rememberSaveable { mutableStateOf<Difficulty?>(null) }
    var sortBy by rememberSaveable { mutableStateOf(CommunitySort.RECENT) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar mapa o creador...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = NeonCyan) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = filterDiff == null,
                        onClick = { filterDiff = null },
                        label = { Text("Todos") }
                    )
                }
                items(Difficulty.entries) { diff ->
                    FilterChip(
                        selected = filterDiff == diff,
                        onClick = { filterDiff = if (filterDiff == diff) null else diff },
                        label = { Text(diff.labelEs) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = diff.accentColor.copy(alpha = 0.22f),
                            selectedLabelColor = diff.accentColor
                        )
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CommunitySort.entries.forEach { sort ->
                    FilterChip(
                        selected = sortBy == sort,
                        onClick = { sortBy = sort },
                        label = { Text(sort.labelEs) }
                    )
                }
            }
        }

        when (communityState) {
            is UiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = NeonCyan)
                    }
                }
            }
            is UiState.Error -> item { ErrorBanner(communityState.message) }
            is UiState.Success -> {
                val filtered = communityState.data
                    .filter { doc ->
                        val matchesDiff = filterDiff == null || Difficulty.fromRaw(doc.difficulty) == filterDiff
                        val q = searchQuery.trim()
                        val matchesQuery = q.isEmpty() ||
                            doc.title.contains(q, ignoreCase = true) ||
                            doc.authorName.contains(q, ignoreCase = true)
                        matchesDiff && matchesQuery
                    }
                    .let { list ->
                        when (sortBy) {
                            CommunitySort.RECENT -> list.sortedByDescending { it.createdAt?.seconds ?: 0L }
                            CommunitySort.LIKED -> list.sortedByDescending { it.likesCount }
                            CommunitySort.PLAYED -> list.sortedByDescending { it.playsCount }
                        }
                    }

                if (filtered.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Rounded.Public,
                            title = "Aún no hay mapas que coincidan",
                            subtitle = "¡Sé el primero en diseñar y publicar un circuito desde el Taller!",
                            ctaLabel = "Ir al Taller",
                            onCtaClick = onGoToWorkshop
                        )
                    }
                } else {
                    items(filtered, key = { it.id }) { doc ->
                        LaunchedEffect(doc.id) {
                            onCheckLiked(doc.id)
                        }
                        val level = remember(doc) { doc.toLevelData() }
                        val isLiked = likedIds[doc.id] == true

                        CommunityMapCard(
                            level = level,
                            isLiked = isLiked,
                            onToggleLike = { onToggleLike(doc.id) },
                            onPlay = { onPlayLevel(level) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkshopTabContent(
    isCloudConnected: Boolean,
    draftsState: UiState<List<LevelData>>,
    publishedState: UiState<List<CommunityLevelDoc>>,
    onCreateNewMap: () -> Unit,
    onEditMap: (LevelData, Boolean) -> Unit,
    onPlayMap: (LevelData) -> Unit,
    onPublishDraft: (LevelData) -> Unit,
    onDeleteDraft: (String) -> Unit,
    onDeletePublished: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = NeonSurfaceVariant),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, NeonMagenta.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "TALLER DE CIRCUITOS",
                            style = MaterialTheme.typography.labelMedium,
                            color = NeonMagenta
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Diseña tu propio nivel",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Coloca plataformas, enemigos, impulsores y guárdalo o compártelo.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = onCreateNewMap,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta, contentColor = TextPrimary),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("create_new_map_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Nuevo")
                    }
                }
            }
        }

        item {
            Text(
                text = "MIS MAPAS GUARDADOS",
                style = MaterialTheme.typography.labelMedium,
                color = NeonCyan
            )
        }

        when (draftsState) {
            is UiState.Loading -> {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = NeonCyan)
                    }
                }
            }
            is UiState.Error -> item { ErrorBanner(draftsState.message) }
            is UiState.Success -> {
                if (draftsState.data.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Rounded.Build,
                            title = "Sin mapas guardados",
                            subtitle = "Crea un mapa nuevo, pruébalo y guárdalo aquí para jugarlo cuando quieras.",
                            ctaLabel = "Crear Mapa",
                            onCtaClick = onCreateNewMap
                        )
                    }
                } else {
                    items(draftsState.data, key = { it.id }) { draft ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = NeonSurface),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, NeonCardBorder, RoundedCornerShape(16.dp))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = draft.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (draft.verified) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                imageVector = Icons.Rounded.Verified,
                                                contentDescription = "Verificado",
                                                tint = NeonLime,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    DifficultyBadge(draft.difficulty)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                MiniLevelPreviewBar(draft)
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val check = validateLevelGrid(draft.gridRows, draft.width)
                                            if (check.isValid) onPlayMap(draft)
                                        },
                                        modifier = Modifier.weight(1f).height(48.dp)
                                    ) {
                                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("Jugar", maxLines = 1)
                                    }
                                    OutlinedButton(
                                        onClick = { onEditMap(draft, draft.verified) },
                                        modifier = Modifier.weight(1f).height(48.dp)
                                    ) {
                                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("Editar", maxLines = 1)
                                    }
                                    Button(
                                        onClick = { onPublishDraft(draft) },
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = NeonVoid),
                                        modifier = Modifier.weight(1f).height(48.dp)
                                    ) {
                                        Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(if (draft.publishedLevelId != null) "Sync" else "Subir", maxLines = 1)
                                    }
                                    IconButton(
                                        onClick = { onDeleteDraft(draft.id) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Eliminar mapa", tint = NeonCrimson)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isCloudConnected) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "MIS MAPAS EN LÍNEA",
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonLime
                )
            }

            when (publishedState) {
                is UiState.Loading -> {}
                is UiState.Error -> item { ErrorBanner(publishedState.message) }
                is UiState.Success -> {
                    if (publishedState.data.isEmpty()) {
                        item {
                            Text(
                                text = "Todavía no has publicado ningún circuito en línea.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    } else {
                        items(publishedState.data, key = { it.id }) { doc ->
                            val lvl = remember(doc) { doc.toLevelData() }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = NeonSurface),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, NeonLime.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = lvl.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = TextPrimary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "${doc.playsCount} jugadas · ${doc.likesCount} ♥",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NeonAmber
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { onPlayMap(lvl) },
                                            modifier = Modifier.weight(1f).height(48.dp)
                                        ) {
                                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Jugar")
                                        }
                                        OutlinedButton(
                                            onClick = { onEditMap(lvl, doc.verified) },
                                            modifier = Modifier.weight(1f).height(48.dp)
                                        ) {
                                            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Editar")
                                        }
                                        IconButton(
                                            onClick = { onDeletePublished(doc.id) },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Retirar mapa", tint = NeonCrimson)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityMapCard(
    level: LevelData,
    isLiked: Boolean,
    onToggleLike: () -> Unit,
    onPlay: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = NeonSurface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NeonCardBorder, RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = level.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (level.verified) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.Verified,
                            contentDescription = "Verificado por su creador",
                            tint = NeonLime,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                DifficultyBadge(level.difficulty)
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Por ${level.authorName}" + if (level.description.isNotBlank()) " · ${level.description}" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))
            MiniLevelPreviewBar(level)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onToggleLike)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("like_button_${level.id}")
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Me gusta",
                            tint = if (isLiked) NeonMagenta else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = level.likesCount.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isLiked) NeonMagenta else TextSecondary
                        )
                    }

                    Text(
                        text = "${level.playsCount} jugadas",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }

                Button(
                    onClick = onPlay,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = NeonVoid),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("play_community_${level.id}")
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Jugar")
                }
            }
        }
    }
}

@Composable
private fun MiniLevelPreviewBar(level: LevelData) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(NeonVoid)
            .border(1.dp, NeonCardBorder.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
    ) {
        val w = level.width.coerceAtLeast(1)
        val cw = size.width / w.toFloat()
        val ch = size.height / LEVEL_GRID_HEIGHT.toFloat()
        for (r in 0 until LEVEL_GRID_HEIGHT) {
            val rowStr = level.gridRows.getOrNull(r) ?: continue
            for (c in 0 until minOf(w, rowStr.length)) {
                val code = rowStr[c]
                if (code != '.') {
                    drawRect(
                        color = TileType.fromChar(code).color,
                        topLeft = Offset(c * cw, r * ch),
                        size = Size(cw.coerceAtLeast(1.5f), ch.coerceAtLeast(1.5f))
                    )
                }
            }
        }
    }
}

@Composable
private fun DifficultyBadge(difficulty: Difficulty) {
    Surface(
        color = difficulty.accentColor.copy(alpha = 0.16f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.border(1.dp, difficulty.accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
    ) {
        Text(
            text = difficulty.labelEs.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = difficulty.accentColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun CampaignStat(label: String, value: String, color: Color) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(text = value, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        color = NeonCrimson.copy(alpha = 0.15f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NeonCrimson.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = NeonCrimson,
            modifier = Modifier.padding(14.dp)
        )
    }
}

@Composable
private fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    ctaLabel: String,
    onCtaClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = NeonSurface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NeonCardBorder, RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = NeonCyan,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedButton(onClick = onCtaClick, shape = RoundedCornerShape(12.dp)) {
                Text(ctaLabel, color = NeonCyan)
            }
        }
    }
}
