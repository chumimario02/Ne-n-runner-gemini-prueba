package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.game.GameCanvas
import com.example.game.GameEngine
import com.example.game.GameHudSnapshot
import com.example.game.GameStatus
import com.example.model.LevelData
import com.example.ui.toggleScreenOrientation
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

@Composable
fun GamePlayScreen(
    level: LevelData,
    isPlaytest: Boolean,
    hasNextCampaignLevel: Boolean,
    onExit: () -> Unit,
    onVictory: (stars: Int, timeMs: Long, score: Long) -> Unit,
    onNextLevel: () -> Unit
) {
    BackHandler(onBack = onExit)

    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    val engine = remember(level) { GameEngine(level) }
    var hud by remember(level) { mutableStateOf(engine.snapshot()) }
    var frameTick by remember { mutableLongStateOf(0L) }
    var victoryReported by remember(level) { mutableStateOf(false) }

    var leftPressed by remember { mutableStateOf(false) }
    var rightPressed by remember { mutableStateOf(false) }
    var keyLeftPressed by remember { mutableStateOf(false) }
    var keyRightPressed by remember { mutableStateOf(false) }
    var jumpPressed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(leftPressed, rightPressed, keyLeftPressed, keyRightPressed) {
        engine.setDirectionalInput(
            left = leftPressed || keyLeftPressed,
            right = rightPressed || keyRightPressed
        )
    }

    LaunchedEffect(level, hud.status) {
        if (hud.status == GameStatus.VICTORY && !victoryReported) {
            victoryReported = true
            onVictory(hud.earnedStars, hud.elapsedMs, hud.score)
        }
    }

    LaunchedEffect(engine) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                if (lastNanos == 0L) lastNanos = nanos
                val dt = ((nanos - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.033f)
                lastNanos = nanos
                hud = engine.update(dt)
                frameTick++
            }
        }
    }

    fun restartLevel() {
        victoryReported = false
        engine.reset()
        hud = engine.snapshot()
    }

    fun togglePause() {
        engine.status = if (engine.status == GameStatus.PAUSED) {
            GameStatus.PLAYING
        } else if (engine.status == GameStatus.PLAYING) {
            GameStatus.PAUSED
        } else engine.status
        hud = engine.snapshot()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonVoid)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                val isDown = keyEvent.type == KeyEventType.KeyDown
                val isUp = keyEvent.type == KeyEventType.KeyUp
                when (keyEvent.key) {
                    Key.DirectionLeft, Key.A -> {
                        if (isDown) keyLeftPressed = true else if (isUp) keyLeftPressed = false
                        true
                    }
                    Key.DirectionRight, Key.D -> {
                        if (isDown) keyRightPressed = true else if (isUp) keyRightPressed = false
                        true
                    }
                    Key.Spacebar, Key.DirectionUp, Key.W -> {
                        if (isDown && keyEvent.nativeKeyEvent.repeatCount == 0) {
                            jumpPressed = true
                            engine.triggerJump()
                        } else if (isUp) {
                            jumpPressed = false
                        }
                        true
                    }
                    Key.Escape, Key.P -> {
                        if (isDown && keyEvent.nativeKeyEvent.repeatCount == 0) {
                            togglePause()
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        val isWide = maxWidth > maxHeight
        val isCompactHeight = maxHeight < 460.dp

        if (isWide) {
            // Landscape / Tablet: Full-screen canvas with floating corner controls
            Box(modifier = Modifier.fillMaxSize()) {
                GameCanvas(engine = engine, frameTick = frameTick, modifier = Modifier.fillMaxSize())

                GameHudBar(
                    hud = hud,
                    isLandscape = true,
                    onPauseToggle = ::togglePause,
                    onRestart = ::restartLevel,
                    onToggleOrientation = { toggleScreenOrientation(context, true) },
                    onExit = onExit,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .widthIn(max = 760.dp)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )

                // Floating arcade controls in corners
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    DirectionalPad(
                        leftPressed = leftPressed || keyLeftPressed,
                        rightPressed = rightPressed || keyRightPressed,
                        onDirectionChanged = { l, r ->
                            leftPressed = l
                            rightPressed = r
                        },
                        compact = true
                    )
                    JumpPadButton(
                        pressed = jumpPressed,
                        onPressDown = {
                            jumpPressed = true
                            engine.triggerJump()
                        },
                        onRelease = { jumpPressed = false },
                        compact = true
                    )
                }
            }
        } else {
            // Portrait: Top 68% Canvas + HUD, Bottom 32% dedicated ergonomic Arcade Controller Deck
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.68f)
                ) {
                    GameCanvas(
                        engine = engine,
                        frameTick = frameTick,
                        modifier = Modifier.fillMaxSize()
                    )

                    GameHudBar(
                        hud = hud,
                        isLandscape = false,
                        onPauseToggle = ::togglePause,
                        onRestart = ::restartLevel,
                        onToggleOrientation = { toggleScreenOrientation(context, false) },
                        onExit = onExit,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(8.dp)
                    )
                }

                // Dedicated Arcade Control Deck
                Surface(
                    color = NeonSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.32f)
                        .border(1.dp, NeonCardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = level.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "DOBLE SALTO ACTIVO",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonCyan
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DirectionalPad(
                                leftPressed = leftPressed,
                                rightPressed = rightPressed,
                                onDirectionChanged = { l, r ->
                                    leftPressed = l
                                    rightPressed = r
                                },
                                compact = false
                            )
                            JumpPadButton(
                                pressed = jumpPressed,
                                onPressDown = {
                                    jumpPressed = true
                                    engine.triggerJump()
                                },
                                onRelease = { jumpPressed = false },
                                compact = false
                            )
                        }
                    }
                }
            }
        }

        // Overlay for Paused, Victory, or Game Over
        if (hud.status != GameStatus.PLAYING) {
            GameOverlayModal(
                status = hud.status,
                hud = hud,
                isPlaytest = isPlaytest,
                hasNextCampaignLevel = hasNextCampaignLevel,
                isCompactHeight = isCompactHeight,
                onResume = {
                    engine.status = GameStatus.PLAYING
                    hud = engine.snapshot()
                },
                onRestart = ::restartLevel,
                onNextLevel = onNextLevel,
                onExit = onExit
            )
        }
    }
}

@Composable
private fun GameHudBar(
    hud: GameHudSnapshot,
    isLandscape: Boolean,
    onPauseToggle: () -> Unit,
    onRestart: () -> Unit,
    onToggleOrientation: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = NeonVoid.copy(alpha = if (isLandscape) 0.70f else 0.78f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, NeonCyan.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = if (isLandscape) 2.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onExit,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("game_exit_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Salir del nivel",
                        tint = TextPrimary
                    )
                }

                // Hearts
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (i in 1..hud.maxHearts) {
                        Icon(
                            imageVector = if (i <= hud.hearts) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Vida $i",
                            tint = if (i <= hud.hearts) NeonMagenta else TextSecondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Center Stats
            Row(
                horizontalArrangement = Arrangement.spacedBy(if (isLandscape) 18.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "PUNTOS",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = hud.score.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = NeonCyan
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "MONEDAS",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = "${hud.coinsCollected}/${hud.totalCoins}",
                        style = MaterialTheme.typography.labelLarge,
                        color = NeonAmber
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "TIEMPO",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = formatElapsed(hud.elapsedMs),
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onToggleOrientation,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("game_rotate_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ScreenRotation,
                        contentDescription = if (isLandscape) "Cambiar a vertical" else "Jugar en horizontal",
                        tint = NeonLime
                    )
                }
                IconButton(
                    onClick = onRestart,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("game_restart_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Reiniciar nivel",
                        tint = NeonCyan
                    )
                }
                IconButton(
                    onClick = onPauseToggle,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("game_pause_button")
                ) {
                    Icon(
                        imageVector = if (hud.status == GameStatus.PAUSED) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        contentDescription = "Pausar juego",
                        tint = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun DirectionalPad(
    leftPressed: Boolean,
    rightPressed: Boolean,
    onDirectionChanged: (left: Boolean, right: Boolean) -> Unit,
    compact: Boolean
) {
    val btnW = if (compact) 68.dp else 82.dp
    val btnH = if (compact) 60.dp else 78.dp
    var padSize by remember { mutableStateOf(IntSize.Zero) }

    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .onSizeChanged { padSize = it }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val anyPressed = event.changes.any { it.pressed }
                        if (!anyPressed || event.type == PointerEventType.Release) {
                            onDirectionChanged(false, false)
                        } else {
                            val halfW = padSize.width / 2f
                            var left = false
                            var right = false
                            for (change in event.changes) {
                                if (change.pressed) {
                                    if (change.position.x < halfW) left = true else right = true
                                }
                            }
                            onDirectionChanged(left, right)
                        }
                    }
                }
            }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = btnW, height = btnH)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    if (leftPressed) NeonCyan.copy(alpha = 0.28f) else NeonSurfaceVariant.copy(alpha = 0.85f)
                )
                .border(
                    width = if (leftPressed) 2.dp else 1.dp,
                    color = if (leftPressed) NeonCyan else NeonCardBorder,
                    shape = RoundedCornerShape(18.dp)
                )
                .testTag("control_left")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = "Mover a la izquierda",
                tint = if (leftPressed) NeonCyan else TextPrimary,
                modifier = Modifier.size(38.dp)
            )
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = btnW, height = btnH)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    if (rightPressed) NeonCyan.copy(alpha = 0.28f) else NeonSurfaceVariant.copy(alpha = 0.85f)
                )
                .border(
                    width = if (rightPressed) 2.dp else 1.dp,
                    color = if (rightPressed) NeonCyan else NeonCardBorder,
                    shape = RoundedCornerShape(18.dp)
                )
                .testTag("control_right")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "Mover a la derecha",
                tint = if (rightPressed) NeonCyan else TextPrimary,
                modifier = Modifier.size(38.dp)
            )
        }
    }
}

@Composable
private fun JumpPadButton(
    pressed: Boolean,
    onPressDown: () -> Unit,
    onRelease: () -> Unit,
    compact: Boolean
) {
    val size = if (compact) 74.dp else 94.dp
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (pressed) NeonMagenta.copy(alpha = 0.35f) else NeonMagenta.copy(alpha = 0.16f)
            )
            .border(
                width = if (pressed) 2.5.dp else 1.8.dp,
                color = NeonMagenta,
                shape = CircleShape
            )
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    var wasDown = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val isDown = event.changes.any { it.pressed }
                        if (isDown && !wasDown) {
                            wasDown = true
                            onPressDown()
                        } else if (!isDown && wasDown) {
                            wasDown = false
                            onRelease()
                        }
                    }
                }
            }
            .testTag("control_jump")
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.ArrowUpward,
                contentDescription = "Saltar",
                tint = NeonMagenta,
                modifier = Modifier.size(if (compact) 30.dp else 36.dp)
            )
            Text(
                text = "SALTAR",
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun GameOverlayModal(
    status: GameStatus,
    hud: GameHudSnapshot,
    isPlaytest: Boolean,
    hasNextCampaignLevel: Boolean,
    isCompactHeight: Boolean,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onNextLevel: () -> Unit,
    onExit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NeonVoid.copy(alpha = 0.78f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = NeonSurface),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .padding(if (isCompactHeight) 12.dp else 24.dp)
                .widthIn(max = if (isCompactHeight) 520.dp else 420.dp)
                .fillMaxWidth()
                .border(
                    width = 1.5.dp,
                    color = when (status) {
                        GameStatus.VICTORY -> NeonLime
                        GameStatus.GAME_OVER -> NeonCrimson
                        else -> NeonCyan
                    },
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(if (isCompactHeight) 16.dp else 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = when (status) {
                        GameStatus.VICTORY -> "¡SECTOR COMPLETADO!"
                        GameStatus.GAME_OVER -> "SISTEMA CAÍDO"
                        else -> "PAUSA"
                    },
                    style = if (isCompactHeight) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                    color = when (status) {
                        GameStatus.VICTORY -> NeonLime
                        GameStatus.GAME_OVER -> NeonCrimson
                        else -> NeonCyan
                    }
                )

                if (status == GameStatus.VICTORY) {
                    Spacer(modifier = Modifier.height(if (isCompactHeight) 8.dp else 14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (s in 1..3) {
                            Icon(
                                imageVector = if (s <= hud.earnedStars) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                contentDescription = "Estrella $s",
                                tint = if (s <= hud.earnedStars) NeonAmber else TextSecondary,
                                modifier = Modifier.size(if (isCompactHeight) 30.dp else 38.dp)
                            )
                        }
                    }

                    if (isPlaytest) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = NeonLime.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.border(1.dp, NeonLime.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Verified,
                                    contentDescription = null,
                                    tint = NeonLime,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Nivel verificado para publicar",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = NeonLime
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(if (isCompactHeight) 10.dp else 16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatBadge("Puntos", hud.score.toString(), NeonCyan)
                        StatBadge("Tiempo", formatElapsed(hud.elapsedMs), TextPrimary)
                        StatBadge("Monedas", "${hud.coinsCollected}/${hud.totalCoins}", NeonAmber)
                    }
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (status == GameStatus.GAME_OVER) {
                            "Te quedaste sin núcleos de energía. ¡Inténtalo de nuevo!"
                        } else {
                            "Juego en pausa"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(if (isCompactHeight) 14.dp else 22.dp))

                if (status == GameStatus.PAUSED) {
                    Button(
                        onClick = onResume,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = NeonVoid),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("overlay_resume_button")
                    ) {
                        Text("Continuar", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (status == GameStatus.VICTORY && hasNextCampaignLevel) {
                    Button(
                        onClick = onNextLevel,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = NeonVoid),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("overlay_next_button")
                    ) {
                        Text("Siguiente Sector", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (isCompactHeight) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onRestart,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (status == GameStatus.GAME_OVER) NeonCyan else NeonSurfaceVariant,
                                contentColor = if (status == GameStatus.GAME_OVER) NeonVoid else TextPrimary
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("overlay_restart_button")
                        ) {
                            Text("Reintentar", style = MaterialTheme.typography.titleMedium)
                        }

                        OutlinedButton(
                            onClick = onExit,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("overlay_exit_button")
                        ) {
                            Text(
                                text = if (isPlaytest) "Volver al Editor" else "Salir al Menú",
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = onRestart,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (status == GameStatus.GAME_OVER) NeonCyan else NeonSurfaceVariant,
                            contentColor = if (status == GameStatus.GAME_OVER) NeonVoid else TextPrimary
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("overlay_restart_button")
                    ) {
                        Text("Reintentar", style = MaterialTheme.typography.titleMedium)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = onExit,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("overlay_exit_button")
                    ) {
                        Text(
                            text = if (isPlaytest) "Volver al Editor" else "Salir al Menú",
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBadge(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Text(text = value, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

fun formatElapsed(ms: Long): String {
    val totalTenths = (ms / 100L).coerceAtLeast(0L)
    val tenths = totalTenths % 10L
    val totalSec = totalTenths / 10L
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return "%02d:%02d.%d".format(mins, secs, tenths)
}
