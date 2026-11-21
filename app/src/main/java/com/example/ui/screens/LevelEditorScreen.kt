package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.ui.toggleScreenOrientation
import com.example.model.Difficulty
import com.example.model.LEVEL_GRID_HEIGHT
import com.example.model.LevelData
import com.example.model.TileType
import com.example.model.defaultStarterGrid
import com.example.model.resizeGridRows
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
import kotlin.math.floor

@Composable
fun LevelEditorScreen(
    initialLevel: LevelData,
    initialVerified: Boolean,
    isCloudConnected: Boolean,
    onExit: () -> Unit,
    onPlaytest: (LevelData) -> Unit,
    onSaveDraft: (LevelData, Boolean) -> Unit,
    onPublishOnline: (LevelData) -> Unit,
    onConnectCloud: (onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit
) {
    BackHandler(onBack = onExit)

    val context = LocalContext.current

    var title by remember(initialLevel.id) { mutableStateOf(initialLevel.title) }
    var description by remember(initialLevel.id) { mutableStateOf(initialLevel.description) }
    var difficulty by remember(initialLevel.id) { mutableStateOf(initialLevel.difficulty) }
    var width by remember(initialLevel.id) { mutableIntStateOf(initialLevel.width.coerceIn(24, 64)) }
    var verified by remember(initialLevel.id, initialVerified) { mutableStateOf(initialVerified) }

    var rowsState by remember(initialLevel.id) {
        mutableStateOf(resizeGridRows(initialLevel.gridRows, initialLevel.width))
    }
    val undoStack = remember { mutableStateListOf<List<String>>() }

    var selectedTool by remember { mutableStateOf(TileType.SOLID) }
    var scrollColOffset by remember { mutableFloatStateOf(0f) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showCloudPublishPrompt by remember { mutableStateOf(false) }
    var cloudAuthError by remember { mutableStateOf<String?>(null) }
    var validationBanner by remember { mutableStateOf<String?>(null) }

    fun currentLevelData(): LevelData = initialLevel.copy(
        title = title.trim().ifBlank { "Sector Personalizado" },
        description = description.trim(),
        difficulty = difficulty,
        width = width,
        height = LEVEL_GRID_HEIGHT,
        gridRows = rowsState,
        verified = verified
    )

    fun pushUndoSnapshot() {
        if (undoStack.size >= 25) undoStack.removeAt(0)
        undoStack.add(rowsState)
    }

    fun paintCell(col: Int, row: Int, tool: TileType, recordUndo: Boolean) {
        if (row !in 0 until LEVEL_GRID_HEIGHT || col !in 0 until width) return
        val currentChar = rowsState[row].getOrNull(col) ?: '.'
        if (currentChar == tool.code) return
        if (recordUndo) pushUndoSnapshot()

        val updated = rowsState.toMutableList()
        if (tool == TileType.SPAWN) {
            for (r in updated.indices) {
                if (updated[r].contains(TileType.SPAWN.code)) {
                    updated[r] = updated[r].replace(TileType.SPAWN.code, '.')
                }
            }
        }
        val chars = updated[row].toCharArray()
        chars[col] = tool.code
        updated[row] = String(chars)
        rowsState = updated
        verified = false
        validationBanner = null
    }

    fun handlePlaytest() {
        val check = validateLevelGrid(rowsState, width)
        if (check.isValid) {
            onPlaytest(currentLevelData())
        } else {
            validationBanner = check.errorEs
        }
    }

    fun handlePublish() {
        val check = validateLevelGrid(rowsState, width)
        if (!check.isValid) {
            validationBanner = check.errorEs
        } else if (title.trim().length < 2) {
            validationBanner = "El nombre del mapa debe tener al menos 2 letras."
            showSettingsDialog = true
        } else if (isCloudConnected) {
            onPublishOnline(currentLevelData())
        } else {
            cloudAuthError = null
            showCloudPublishPrompt = true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonVoid)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val isLandscape = maxWidth > maxHeight

        Column(modifier = Modifier.fillMaxSize()) {
            // Top Action Toolbar
            Surface(
                color = NeonSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NeonCardBorder)
            ) {
                if (isLandscape) {
                    // Single compact row in landscape so the grid gets maximum vertical space
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = onExit,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("editor_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Salir del editor",
                                    tint = TextPrimary
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { showSettingsDialog = true }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = title.ifBlank { "Sin título" },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (verified) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Rounded.Verified,
                                            contentDescription = "Verificado",
                                            tint = NeonLime,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${difficulty.labelEs} · ${width}×$LEVEL_GRID_HEIGHT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeonCyan,
                                    maxLines = 1
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (undoStack.isNotEmpty()) {
                                        rowsState = undoStack.removeAt(undoStack.lastIndex)
                                        verified = false
                                    }
                                },
                                enabled = undoStack.isNotEmpty(),
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("editor_undo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Undo,
                                    contentDescription = "Deshacer",
                                    tint = if (undoStack.isNotEmpty()) TextPrimary else TextSecondary.copy(alpha = 0.35f)
                                )
                            }
                            IconButton(
                                onClick = { showSettingsDialog = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("editor_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = "Configurar mapa",
                                    tint = NeonCyan
                                )
                            }
                            IconButton(
                                onClick = { toggleScreenOrientation(context, true) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("editor_rotate_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ScreenRotation,
                                    contentDescription = "Cambiar a vertical",
                                    tint = NeonLime
                                )
                            }
                            OutlinedButton(
                                onClick = ::handlePlaytest,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("editor_playtest_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = NeonLime,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Probar", color = NeonLime, maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { onSaveDraft(currentLevelData(), verified) },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("editor_save_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Save,
                                    contentDescription = null,
                                    tint = NeonCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Guardar", color = NeonCyan, maxLines = 1)
                            }
                            Button(
                                onClick = ::handlePublish,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonMagenta,
                                    contentColor = TextPrimary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("editor_publish_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Publicar", maxLines = 1)
                            }
                        }
                    }
                } else {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onExit,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("editor_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Salir del editor",
                                tint = TextPrimary
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { showSettingsDialog = true }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = title.ifBlank { "Sin título" },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (verified) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Rounded.Verified,
                                        contentDescription = "Verificado",
                                        tint = NeonLime,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = "${difficulty.labelEs} · ${width}×$LEVEL_GRID_HEIGHT · Toca para editar",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonCyan
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (undoStack.isNotEmpty()) {
                                    rowsState = undoStack.removeAt(undoStack.lastIndex)
                                    verified = false
                                }
                            },
                            enabled = undoStack.isNotEmpty(),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("editor_undo_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Undo,
                                contentDescription = "Deshacer",
                                tint = if (undoStack.isNotEmpty()) TextPrimary else TextSecondary.copy(alpha = 0.35f)
                            )
                        }
                        IconButton(
                            onClick = { toggleScreenOrientation(context, false) },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("editor_rotate_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ScreenRotation,
                                contentDescription = "Cambiar a horizontal",
                                tint = NeonLime
                            )
                        }
                        IconButton(
                            onClick = { showSettingsDialog = true },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("editor_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = "Configurar mapa",
                                tint = NeonCyan
                            )
                        }
                    }
                }

                // Secondary quick action row: Playtest, Save Draft, Publish Online
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = ::handlePlaytest,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("editor_playtest_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = NeonLime,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Probar", color = NeonLime, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = {
                            onSaveDraft(currentLevelData(), verified)
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("editor_save_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Save,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Guardar", color = NeonCyan, maxLines = 1)
                    }

                    Button(
                        onClick = ::handlePublish,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonMagenta,
                            contentColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1.1f)
                            .height(48.dp)
                            .testTag("editor_publish_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Publicar", maxLines = 1)
                    }
                }
            }
                }
            }

            if (validationBanner != null) {
                Surface(
                    color = NeonCrimson.copy(alpha = 0.2f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, NeonCrimson.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = validationBanner ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeonCrimson,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    // Minimap Camera Scroller Bar
                    Surface(
                        color = NeonSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = if (isLandscape) 3.dp else 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, NeonCardBorder, RoundedCornerShape(12.dp))
                    ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 4.dp)
            ) {
                IconButton(
                    onClick = { scrollColOffset = (scrollColOffset - 5f).coerceAtLeast(0f) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = "Desplazar mapa izquierda",
                        tint = NeonCyan
                    )
                }

                var minimapSize by remember { mutableStateOf(IntSize.Zero) }
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(NeonVoid)
                        .onSizeChanged { minimapSize = it }
                        .pointerInput(width) {
                            detectTapGestures { offset ->
                                if (minimapSize.width > 0) {
                                    val ratio = (offset.x / minimapSize.width).coerceIn(0f, 1f)
                                    scrollColOffset = (ratio * width - 8f).coerceIn(0f, (width - 14f).coerceAtLeast(0f))
                                }
                            }
                        }
                        .pointerInput(width) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                if (minimapSize.width > 0) {
                                    val ratio = (change.position.x / minimapSize.width).coerceIn(0f, 1f)
                                    scrollColOffset = (ratio * width - 8f).coerceIn(0f, (width - 14f).coerceAtLeast(0f))
                                }
                            }
                        }
                ) {
                    val cellW = size.width / width.toFloat()
                    val cellH = size.height / LEVEL_GRID_HEIGHT.toFloat()
                    for (r in 0 until LEVEL_GRID_HEIGHT) {
                        val rowStr = rowsState[r]
                        for (c in 0 until width) {
                            val ch = rowStr[c]
                            if (ch != '.') {
                                drawRect(
                                    color = TileType.fromChar(ch).color,
                                    topLeft = Offset(c * cellW, r * cellH),
                                    size = Size(cellW.coerceAtLeast(1.5f), cellH.coerceAtLeast(1.5f))
                                )
                            }
                        }
                    }
                    // Viewport box
                    val viewCols = 16f.coerceAtMost(width.toFloat())
                    val vx = scrollColOffset.coerceIn(0f, (width - viewCols).coerceAtLeast(0f)) * cellW
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(vx, 0f),
                        size = Size(viewCols * cellW, size.height),
                        style = Stroke(width = 2f)
                    )
                }

                IconButton(
                    onClick = {
                        scrollColOffset = (scrollColOffset + 5f).coerceAtMost((width - 14f).coerceAtLeast(0f))
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Desplazar mapa derecha",
                        tint = NeonCyan
                    )
                }
            }
        }

                // Main Interactive Editor Grid Canvas
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = if (isLandscape) 4.dp else 0.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, NeonCyan.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                        .background(NeonVoid)
                ) {
                    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
                    var strokeRecorded by remember { mutableStateOf(false) }

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { canvasSize = it }
                            .pointerInput(selectedTool, width, scrollColOffset) {
                                detectTapGestures { offset ->
                                    if (canvasSize.height <= 0) return@detectTapGestures
                                    val tilePx = canvasSize.height / LEVEL_GRID_HEIGHT.toFloat()
                                    val visibleCols = canvasSize.width / tilePx
                                    val maxScroll = (width - visibleCols).coerceAtLeast(0f)
                                    val camCol = scrollColOffset.coerceIn(0f, maxScroll)
                                    val col = floor(camCol + offset.x / tilePx).toInt()
                                    val row = floor(offset.y / tilePx).toInt()
                                    paintCell(col, row, selectedTool, recordUndo = true)
                                }
                            }
                            .pointerInput(selectedTool, width, scrollColOffset) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        strokeRecorded = false
                                        if (canvasSize.height > 0) {
                                            val tilePx = canvasSize.height / LEVEL_GRID_HEIGHT.toFloat()
                                            val visibleCols = canvasSize.width / tilePx
                                            val maxScroll = (width - visibleCols).coerceAtLeast(0f)
                                            val camCol = scrollColOffset.coerceIn(0f, maxScroll)
                                            val col = floor(camCol + offset.x / tilePx).toInt()
                                            val row = floor(offset.y / tilePx).toInt()
                                            paintCell(col, row, selectedTool, recordUndo = true)
                                            strokeRecorded = true
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        if (canvasSize.height > 0) {
                                            val tilePx = canvasSize.height / LEVEL_GRID_HEIGHT.toFloat()
                                            val visibleCols = canvasSize.width / tilePx
                                            val maxScroll = (width - visibleCols).coerceAtLeast(0f)
                                            val camCol = scrollColOffset.coerceIn(0f, maxScroll)
                                            val col = floor(camCol + change.position.x / tilePx).toInt()
                                            val row = floor(change.position.y / tilePx).toInt()
                                            paintCell(col, row, selectedTool, recordUndo = !strokeRecorded)
                                            strokeRecorded = true
                                        }
                                    }
                                )
                            }
                    ) {
                        val tilePx = size.height / LEVEL_GRID_HEIGHT.toFloat()
                        val visibleCols = size.width / tilePx
                        val maxScroll = (width - visibleCols).coerceAtLeast(0f)
                        val camCol = scrollColOffset.coerceIn(0f, maxScroll)

                        val startCol = floor(camCol).toInt().coerceAtLeast(0)
                        val endCol = (startCol + visibleCols.toInt() + 2).coerceAtMost(width - 1)

                        // Grid lines
                        for (r in 0..LEVEL_GRID_HEIGHT) {
                            val y = r * tilePx
                            drawLine(
                                color = NeonCardBorder.copy(alpha = 0.45f),
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1f
                            )
                        }
                        for (c in startCol..endCol + 1) {
                            val x = (c - camCol) * tilePx
                            drawLine(
                                color = NeonCardBorder.copy(alpha = 0.45f),
                                start = Offset(x, 0f),
                                end = Offset(x, size.height),
                                strokeWidth = 1f
                            )
                        }

                        // Draw placed tiles
                        for (r in 0 until LEVEL_GRID_HEIGHT) {
                            val rowStr = rowsState[r]
                            for (c in startCol..endCol) {
                                val ch = rowStr[c]
                                if (ch == '.') continue
                                val x = (c - camCol) * tilePx
                                val y = r * tilePx
                                drawEditorTile(ch, x, y, tilePx)
                            }
                        }
                    }
                }
                }

                if (isLandscape) {
                    // Right-hand Vertical Tile Palette in landscape for maximum canvas height
                    Surface(
                        color = NeonSurface,
                        modifier = Modifier
                            .width(88.dp)
                            .fillMaxHeight()
                            .border(1.dp, NeonCardBorder)
                    ) {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            items(TileType.entries) { tool ->
                                EditorToolItem(
                                    tool = tool,
                                    isSelected = selectedTool == tool,
                                    onSelect = { selectedTool = tool },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            if (!isLandscape) {
                // Bottom Tile Palette in portrait
                Surface(
                    color = NeonSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .border(1.dp, NeonCardBorder)
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(TileType.entries) { tool ->
                            EditorToolItem(
                                tool = tool,
                                isSelected = selectedTool == tool,
                                onSelect = { selectedTool = tool }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            containerColor = NeonSurface,
            title = {
                Text("Configurar Mapa", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { if (it.length <= 40) title = it },
                        label = { Text("Nombre del mapa (2-40)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { if (it.length <= 160) description = it },
                        label = { Text("Descripción breve") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Dificultad", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Difficulty.entries.forEach { diff ->
                            FilterChip(
                                selected = difficulty == diff,
                                onClick = { difficulty = diff },
                                label = { Text(diff.labelEs, maxLines = 1) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = diff.accentColor.copy(alpha = 0.25f),
                                    selectedLabelColor = diff.accentColor
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Text("Longitud del circuito", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(32, 44, 56).forEach { w ->
                            FilterChip(
                                selected = width == w,
                                onClick = {
                                    if (width != w) {
                                        pushUndoSnapshot()
                                        width = w
                                        rowsState = resizeGridRows(rowsState, w)
                                        verified = false
                                    }
                                },
                                label = { Text("$w col") }
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            pushUndoSnapshot()
                            rowsState = defaultStarterGrid(width)
                            verified = false
                        }
                    ) {
                        Text("Restaurar plantilla inicial", color = NeonMagenta)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showSettingsDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = NeonVoid)
                ) {
                    Text("Listo")
                }
            }
        )
    }

    if (showCloudPublishPrompt) {
        AlertDialog(
            onDismissRequest = { showCloudPublishPrompt = false },
            containerColor = NeonSurface,
            title = { Text("Publicar en línea", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Tu mapa ya se puede guardar y jugar localmente sin cuenta. Para subirlo a la comunidad en línea de Firebase, conecta una cuenta de Google:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    if (cloudAuthError != null) {
                        Text(
                            text = cloudAuthError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = NeonCrimson
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onConnectCloud(
                            {
                                showCloudPublishPrompt = false
                                onPublishOnline(currentLevelData())
                            },
                            { err -> cloudAuthError = err }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = NeonVoid)
                ) {
                    Text("Conectar y Publicar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloudPublishPrompt = false }) {
                    Text("Solo guardar local", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun EditorToolItem(
    tool: TileType,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isSelected) tool.color.copy(alpha = 0.22f) else NeonSurfaceVariant
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) tool.color else NeonCardBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("tool_${tool.name.lowercase()}")
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (tool == TileType.EMPTY) NeonVoid else tool.color.copy(alpha = 0.85f)
                )
                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (tool == TileType.EMPTY) "×" else tool.code.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (tool == TileType.EMPTY) TextSecondary else NeonVoid,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = tool.shortLabelEs,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) TextPrimary else TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEditorTile(
    ch: Char,
    x: Float,
    y: Float,
    s: Float
) {
    val tile = TileType.fromChar(ch)
    when (ch) {
        TileType.SOLID.code -> {
            drawRoundRect(
                color = Color(0xFF141E45),
                topLeft = Offset(x + 1f, y + 1f),
                size = Size(s - 2f, s - 2f),
                cornerRadius = CornerRadius(s * 0.1f)
            )
            drawRoundRect(
                color = NeonCyan,
                topLeft = Offset(x + 1f, y + 1f),
                size = Size(s - 2f, s - 2f),
                cornerRadius = CornerRadius(s * 0.1f),
                style = Stroke(width = 2f)
            )
        }
        TileType.PLATFORM.code -> {
            drawRect(color = tile.color, topLeft = Offset(x + 1f, y + 1f), size = Size(s - 2f, s * 0.35f))
        }
        TileType.SPIKE.code -> {
            val path = Path().apply {
                moveTo(x + s * 0.1f, y + s)
                lineTo(x + s * 0.5f, y + s * 0.18f)
                lineTo(x + s * 0.9f, y + s)
                close()
            }
            drawPath(path, color = NeonMagenta)
        }
        TileType.COIN.code -> {
            drawCircle(color = NeonAmber, radius = s * 0.28f, center = Offset(x + s / 2f, y + s / 2f))
        }
        TileType.SPAWN.code -> {
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(x + s * 0.18f, y + s * 0.12f),
                size = Size(s * 0.64f, s * 0.78f),
                cornerRadius = CornerRadius(s * 0.15f)
            )
            drawCircle(color = NeonCyan, radius = s * 0.14f, center = Offset(x + s * 0.5f, y + s * 0.38f))
        }
        TileType.GOAL.code -> {
            drawCircle(
                color = NeonLime,
                radius = s * 0.38f,
                center = Offset(x + s / 2f, y + s / 2f),
                style = Stroke(width = 3f)
            )
        }
        else -> {
            drawRoundRect(
                color = tile.color.copy(alpha = 0.85f),
                topLeft = Offset(x + s * 0.1f, y + s * 0.1f),
                size = Size(s * 0.8f, s * 0.8f),
                cornerRadius = CornerRadius(s * 0.18f)
            )
        }
    }
}
