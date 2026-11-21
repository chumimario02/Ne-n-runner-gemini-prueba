package com.example.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import com.example.model.TileType
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCrimson
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonLime
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonVoid
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun GameCanvas(
    engine: GameEngine,
    frameTick: Long,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        // Touch frameTick so Compose redraws every animation frame
        @Suppress("UNUSED_EXPRESSION")
        frameTick

        val tileSize = size.height / engine.height.toFloat()
        val viewWidthTiles = size.width / tileSize

        // Smooth camera follow clamped to level bounds
        val targetCamX = (engine.playerX + GameEngine.PLAYER_W / 2f - viewWidthTiles * 0.42f)
            .coerceIn(0f, (engine.width - viewWidthTiles).coerceAtLeast(0f))
        engine.cameraX += (targetCamX - engine.cameraX) * 0.16f

        val shakeX = if (engine.shakeTimer > 0f) (Random.nextFloat() - 0.5f) * tileSize * 0.25f else 0f
        val shakeY = if (engine.shakeTimer > 0f) (Random.nextFloat() - 0.5f) * tileSize * 0.25f else 0f

        drawCyberBackground(engine.cameraX, engine.animTime, tileSize)

        translate(left = -engine.cameraX * tileSize + shakeX, top = shakeY) {
            val startCol = floor(engine.cameraX).toInt().coerceAtLeast(0)
            val endCol = (startCol + viewWidthTiles.toInt() + 2).coerceAtMost(engine.width - 1)

            // Draw level tiles
            for (r in 0 until engine.height) {
                for (c in startCol..endCol) {
                    val ch = engine.tileAt(c, r)
                    if (ch == '.' || ch == TileType.SPAWN.code) continue
                    val x = c * tileSize
                    val y = r * tileSize
                    drawTile(ch, x, y, tileSize, engine.animTime, engine.checkpointReached)
                }
            }

            // Draw enemies
            for (enemy in engine.enemies) {
                if (!enemy.alive) continue
                drawEnemy(enemy, tileSize, engine.animTime)
            }

            // Draw particles
            for (p in engine.particles) {
                val alpha = (p.life / p.maxLife).coerceIn(0f, 1f)
                drawCircle(
                    color = Color(p.colorHex).copy(alpha = alpha),
                    radius = p.size * tileSize,
                    center = Offset(p.x * tileSize, p.y * tileSize)
                )
            }

            // Draw player
            drawPlayer(engine, tileSize)
        }
    }
}

private fun DrawScope.drawCyberBackground(cameraX: Float, animTime: Float, tileSize: Float) {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(NeonVoid, Color(0xFF0B0E24), Color(0xFF140B29))
        )
    )

    // Distant synthwave sun/halo
    val sunX = size.width * 0.72f - cameraX * tileSize * 0.04f
    val sunY = size.height * 0.28f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(NeonMagenta.copy(alpha = 0.22f), Color.Transparent),
            center = Offset(sunX, sunY),
            radius = size.height * 0.35f
        ),
        radius = size.height * 0.35f,
        center = Offset(sunX, sunY)
    )

    // Parallax neon skyline silhouettes
    val parallaxOffset = -(cameraX * tileSize * 0.15f) % (size.width * 0.5f)
    val buildingW = size.width / 10f
    for (i in -2..14) {
        val bx = parallaxOffset + i * buildingW
        val bh = size.height * (0.25f + ((i * 37) % 5) * 0.08f)
        val by = size.height - bh
        drawRect(
            color = Color(0xFF0E1330),
            topLeft = Offset(bx, by),
            size = Size(buildingW * 0.86f, bh)
        )
        drawLine(
            color = NeonCyan.copy(alpha = 0.14f),
            start = Offset(bx, by),
            end = Offset(bx + buildingW * 0.86f, by),
            strokeWidth = 2f
        )
    }

    // Perspective laser grid lines
    val gridSpacing = tileSize * 2f
    val shift = -(cameraX * tileSize * 0.4f) % gridSpacing
    var gx = shift
    while (gx < size.width) {
        drawLine(
            color = NeonCyan.copy(alpha = 0.05f),
            start = Offset(gx, 0f),
            end = Offset(gx, size.height),
            strokeWidth = 1f
        )
        gx += gridSpacing
    }
    var gy = 0f
    while (gy < size.height) {
        drawLine(
            color = NeonMagenta.copy(alpha = 0.05f),
            start = Offset(0f, gy),
            end = Offset(size.width, gy),
            strokeWidth = 1f
        )
        gy += gridSpacing
    }

    // Subtle animated scanline pulse
    val pulseAlpha = 0.03f + 0.02f * sin(animTime * 2f)
    drawRect(color = NeonCyan.copy(alpha = pulseAlpha))
}

private fun DrawScope.drawTile(
    ch: Char,
    x: Float,
    y: Float,
    s: Float,
    animTime: Float,
    checkpointReached: Boolean
) {
    when (ch) {
        TileType.SOLID.code -> {
            drawRoundRect(
                color = Color(0xFF12193B),
                topLeft = Offset(x, y),
                size = Size(s, s),
                cornerRadius = CornerRadius(s * 0.10f)
            )
            drawRoundRect(
                color = NeonCyan.copy(alpha = 0.85f),
                topLeft = Offset(x + 1f, y + 1f),
                size = Size(s - 2f, s - 2f),
                cornerRadius = CornerRadius(s * 0.10f),
                style = Stroke(width = (s * 0.08f).coerceAtLeast(1.5f))
            )
            drawRect(
                color = NeonCyan.copy(alpha = 0.18f),
                topLeft = Offset(x + s * 0.22f, y + s * 0.22f),
                size = Size(s * 0.56f, s * 0.56f)
            )
        }
        TileType.PLATFORM.code -> {
            drawRoundRect(
                color = Color(0xFF4CC9F0),
                topLeft = Offset(x, y),
                size = Size(s, s * 0.28f),
                cornerRadius = CornerRadius(s * 0.08f)
            )
            drawRect(
                color = NeonCyan.copy(alpha = 0.35f),
                topLeft = Offset(x + s * 0.1f, y + s * 0.28f),
                size = Size(s * 0.8f, s * 0.08f)
            )
        }
        TileType.BOUNCE.code -> {
            val springOffset = abs(sin(animTime * 5f)) * s * 0.10f
            drawRoundRect(
                color = Color(0xFF19283D),
                topLeft = Offset(x + s * 0.1f, y + s * 0.65f),
                size = Size(s * 0.8f, s * 0.35f),
                cornerRadius = CornerRadius(s * 0.08f)
            )
            drawRoundRect(
                color = NeonLime,
                topLeft = Offset(x + s * 0.05f, y + s * 0.45f - springOffset),
                size = Size(s * 0.9f, s * 0.22f),
                cornerRadius = CornerRadius(s * 0.1f)
            )
        }
        TileType.SPIKE.code -> {
            val path = Path().apply {
                moveTo(x + s * 0.06f, y + s)
                lineTo(x + s * 0.30f, y + s * 0.20f)
                lineTo(x + s * 0.50f, y + s)
                lineTo(x + s * 0.70f, y + s * 0.20f)
                lineTo(x + s * 0.94f, y + s)
                close()
            }
            drawPath(path = path, color = NeonMagenta)
            drawPath(
                path = path,
                color = Color.White.copy(alpha = 0.45f),
                style = Stroke(width = (s * 0.05f).coerceAtLeast(1f))
            )
        }
        TileType.LASER.code -> {
            val pulse = 0.65f + 0.35f * sin(animTime * 9f)
            drawRect(
                color = NeonCrimson.copy(alpha = 0.32f * pulse),
                topLeft = Offset(x, y),
                size = Size(s, s)
            )
            drawRect(
                color = NeonCrimson,
                topLeft = Offset(x, y + s * 0.35f),
                size = Size(s, s * 0.3f)
            )
            drawRect(
                color = Color.White.copy(alpha = 0.7f * pulse),
                topLeft = Offset(x, y + s * 0.44f),
                size = Size(s, s * 0.12f)
            )
        }
        TileType.COIN.code -> {
            val bob = sin(animTime * 4f + x * 0.1f) * s * 0.10f
            val cx = x + s / 2f
            val cy = y + s / 2f + bob
            val r = s * 0.28f
            drawCircle(color = NeonAmber.copy(alpha = 0.25f), radius = r * 1.45f, center = Offset(cx, cy))
            val diamond = Path().apply {
                moveTo(cx, cy - r)
                lineTo(cx + r * 0.75f, cy)
                lineTo(cx, cy + r)
                lineTo(cx - r * 0.75f, cy)
                close()
            }
            drawPath(diamond, color = NeonAmber)
            drawPath(diamond, color = Color.White.copy(alpha = 0.6f), style = Stroke(width = 1.5f))
        }
        TileType.CHECKPOINT.code -> {
            val beamColor = if (checkpointReached) NeonLime else Color(0xFF00E5A3).copy(alpha = 0.55f)
            drawRect(
                color = Color(0xFF28355E),
                topLeft = Offset(x + s * 0.44f, y + s * 0.15f),
                size = Size(s * 0.12f, s * 0.85f)
            )
            val flag = Path().apply {
                moveTo(x + s * 0.52f, y + s * 0.18f)
                lineTo(x + s * 0.92f, y + s * 0.34f)
                lineTo(x + s * 0.52f, y + s * 0.50f)
                close()
            }
            drawPath(flag, color = beamColor)
        }
        TileType.GOAL.code -> {
            val cx = x + s / 2f
            val cy = y + s / 2f
            val spin = animTime * 3.5f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(NeonLime.copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = s * 0.75f
                ),
                radius = s * 0.75f,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = NeonLime,
                radius = s * 0.42f,
                center = Offset(cx, cy),
                style = Stroke(width = (s * 0.10f).coerceAtLeast(2f))
            )
            drawCircle(
                color = NeonCyan,
                radius = s * 0.24f,
                center = Offset(cx + cos(spin) * s * 0.08f, cy + sin(spin) * s * 0.08f)
            )
        }
    }
}

private fun DrawScope.drawEnemy(enemy: EnemyEntity, s: Float, animTime: Float) {
    val x = enemy.x * s
    val y = enemy.y * s
    val w = enemy.width * s
    val h = enemy.height * s

    when (enemy.kind) {
        EnemyKind.PATROL -> {
            val bodyColor = Color(0xFFFF6B35)
            drawRoundRect(
                color = bodyColor.copy(alpha = 0.25f),
                topLeft = Offset(x - s * 0.05f, y - s * 0.05f),
                size = Size(w + s * 0.1f, h + s * 0.1f),
                cornerRadius = CornerRadius(s * 0.2f)
            )
            drawRoundRect(
                color = Color(0xFF1D1024),
                topLeft = Offset(x, y + h * 0.12f),
                size = Size(w, h * 0.88f),
                cornerRadius = CornerRadius(s * 0.18f)
            )
            drawRoundRect(
                color = bodyColor,
                topLeft = Offset(x, y + h * 0.12f),
                size = Size(w, h * 0.88f),
                cornerRadius = CornerRadius(s * 0.18f),
                style = Stroke(width = (s * 0.08f).coerceAtLeast(1.5f))
            )
            val eyeX = if (enemy.vx >= 0f) x + w * 0.62f else x + w * 0.22f
            drawCircle(color = NeonAmber, radius = w * 0.14f, center = Offset(eyeX, y + h * 0.50f))
        }
        EnemyKind.FLYER -> {
            val cx = x + w / 2f
            val cy = y + h / 2f
            drawCircle(color = NeonPurple.copy(alpha = 0.28f), radius = w * 0.72f, center = Offset(cx, cy))
            val wingSpread = w * (0.45f + 0.15f * sin(animTime * 16f))
            drawLine(
                color = NeonCyan,
                start = Offset(cx - wingSpread, cy - h * 0.28f),
                end = Offset(cx + wingSpread, cy - h * 0.28f),
                strokeWidth = (s * 0.09f).coerceAtLeast(2f)
            )
            drawCircle(color = Color(0xFF1E1035), radius = w * 0.38f, center = Offset(cx, cy))
            drawCircle(
                color = NeonPurple,
                radius = w * 0.38f,
                center = Offset(cx, cy),
                style = Stroke(width = (s * 0.08f).coerceAtLeast(1.5f))
            )
            drawCircle(color = NeonMagenta, radius = w * 0.15f, center = Offset(cx, cy))
        }
        EnemyKind.CHARGER -> {
            val bodyColor = NeonCrimson
            drawRoundRect(
                color = bodyColor.copy(alpha = 0.28f),
                topLeft = Offset(x - s * 0.07f, y - s * 0.05f),
                size = Size(w + s * 0.14f, h + s * 0.1f),
                cornerRadius = CornerRadius(s * 0.15f)
            )
            drawRoundRect(
                color = Color(0xFF280B18),
                topLeft = Offset(x, y + h * 0.08f),
                size = Size(w, h * 0.92f),
                cornerRadius = CornerRadius(s * 0.15f)
            )
            drawRoundRect(
                color = bodyColor,
                topLeft = Offset(x, y + h * 0.08f),
                size = Size(w, h * 0.92f),
                cornerRadius = CornerRadius(s * 0.15f),
                style = Stroke(width = (s * 0.09f).coerceAtLeast(2f))
            )
            // Aggressive glowing visor
            val visorX = if (enemy.vx >= 0f) x + w * 0.45f else x + w * 0.12f
            drawRect(
                color = NeonAmber,
                topLeft = Offset(visorX, y + h * 0.34f),
                size = Size(w * 0.42f, h * 0.16f)
            )
        }
    }
}

private fun DrawScope.drawPlayer(engine: GameEngine, s: Float) {
    // Blink during invulnerability frames
    if (engine.invulnTimer > 0f && ((engine.invulnTimer * 16f).toInt() % 2 == 0)) {
        return
    }

    val x = engine.playerX * s
    val y = engine.playerY * s
    val w = GameEngine.PLAYER_W * s
    val h = GameEngine.PLAYER_H * s

    // Neon glow aura
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(NeonCyan.copy(alpha = 0.35f), Color.Transparent),
            center = Offset(x + w / 2f, y + h / 2f),
            radius = s * 0.9f
        ),
        radius = s * 0.9f,
        center = Offset(x + w / 2f, y + h / 2f)
    )

    // Cyber helmet / torso body
    drawRoundRect(
        color = Color(0xFF0A1D36),
        topLeft = Offset(x, y),
        size = Size(w, h * 0.85f),
        cornerRadius = CornerRadius(s * 0.18f)
    )
    drawRoundRect(
        color = NeonCyan,
        topLeft = Offset(x, y),
        size = Size(w, h * 0.85f),
        cornerRadius = CornerRadius(s * 0.18f),
        style = Stroke(width = (s * 0.08f).coerceAtLeast(2f))
    )

    // Glowing visor facing movement direction
    val visorX = if (engine.facingRight) x + w * 0.42f else x + w * 0.10f
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(visorX, y + h * 0.18f),
        size = Size(w * 0.48f, h * 0.22f),
        cornerRadius = CornerRadius(s * 0.08f)
    )

    // Animated cyber legs
    val stride = if (engine.onGround && kotlin.math.abs(engine.playerVx) > 0.6f) {
        sin(engine.animTime * 18f) * w * 0.18f
    } else 0f
    drawRoundRect(
        color = NeonCyan,
        topLeft = Offset(x + w * 0.16f + stride, y + h * 0.82f),
        size = Size(w * 0.22f, h * 0.18f),
        cornerRadius = CornerRadius(s * 0.05f)
    )
    drawRoundRect(
        color = NeonMagenta,
        topLeft = Offset(x + w * 0.62f - stride, y + h * 0.82f),
        size = Size(w * 0.22f, h * 0.18f),
        cornerRadius = CornerRadius(s * 0.05f)
    )
}
