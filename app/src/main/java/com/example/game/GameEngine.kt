package com.example.game

import com.example.model.LEVEL_GRID_HEIGHT
import com.example.model.LevelData
import com.example.model.TileType
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

enum class GameStatus {
    PLAYING,
    PAUSED,
    VICTORY,
    GAME_OVER
}

enum class EnemyKind {
    PATROL,
    FLYER,
    CHARGER
}

data class EnemyEntity(
    val id: Int,
    val kind: EnemyKind,
    val startX: Float,
    val startY: Float,
    var x: Float,
    var y: Float,
    val width: Float = 0.78f,
    val height: Float = 0.78f,
    var vx: Float = 2.5f,
    var vy: Float = 0f,
    var alive: Boolean = true,
    var phase: Float = 0f
)

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val maxLife: Float,
    val colorHex: Long,
    val size: Float
)

data class GameHudSnapshot(
    val status: GameStatus = GameStatus.PLAYING,
    val hearts: Int = 3,
    val maxHearts: Int = 3,
    val score: Long = 0L,
    val coinsCollected: Int = 0,
    val totalCoins: Int = 0,
    val enemiesDefeated: Int = 0,
    val elapsedMs: Long = 0L,
    val earnedStars: Int = 0,
    val checkpointReached: Boolean = false
)

class GameEngine(val level: LevelData) {

    companion object {
        const val GRAVITY = 28.5f
        const val MAX_FALL_SPEED = 18.0f
        const val MOVE_SPEED = 7.4f
        const val GROUND_ACCEL = 54.0f
        const val AIR_ACCEL = 38.0f
        const val JUMP_VY = -11.5f
        const val DOUBLE_JUMP_VY = -10.3f
        const val BOUNCE_VY = -16.6f
        const val STOMP_BOUNCE_VY = -10.8f
        const val PLAYER_W = 0.70f
        const val PLAYER_H = 0.86f
    }

    val width: Int = level.width
    val height: Int = LEVEL_GRID_HEIGHT

    private val grid: Array<CharArray> = Array(height) { r ->
        val row = level.gridRows.getOrNull(r) ?: ".".repeat(width)
        row.padEnd(width, if (r == height - 1) '#' else '.').take(width).toCharArray()
    }

    var status: GameStatus = GameStatus.PLAYING
    var playerX: Float = 1.15f
    var playerY: Float = (height - 3).toFloat()
    var playerVx: Float = 0f
    var playerVy: Float = 0f
    var facingRight: Boolean = true
    var onGround: Boolean = false
    var jumpsLeft: Int = 2
    var coyoteTimer: Float = 0f
    var jumpBufferTimer: Float = 0f
    var invulnTimer: Float = 0f
    var shakeTimer: Float = 0f
    var animTime: Float = 0f

    private var spawnX: Float = 1.15f
    private var spawnY: Float = (height - 3).toFloat()
    var checkpointX: Float = 1.15f
    var checkpointY: Float = (height - 3).toFloat()
    var checkpointReached: Boolean = false

    var hearts: Int = 3
    val maxHearts: Int = 3
    var score: Long = 0L
    var coinsCollected: Int = 0
    var totalCoins: Int = 0
    var enemiesDefeated: Int = 0
    var elapsedSec: Float = 0f
    var earnedStars: Int = 0

    var cameraX: Float = 0f
    var cameraY: Float = 0f

    val enemies = mutableListOf<EnemyEntity>()
    val particles = mutableListOf<Particle>()

    private var inputLeft = false
    private var inputRight = false

    init {
        reset()
    }

    fun reset() {
        status = GameStatus.PLAYING
        enemies.clear()
        particles.clear()
        hearts = maxHearts
        score = 0L
        coinsCollected = 0
        totalCoins = 0
        enemiesDefeated = 0
        elapsedSec = 0f
        earnedStars = 0
        invulnTimer = 0f
        shakeTimer = 0f
        coyoteTimer = 0f
        jumpBufferTimer = 0f
        checkpointReached = false
        playerVx = 0f
        playerVy = 0f
        onGround = false
        jumpsLeft = 2

        var enemyId = 0
        for (r in 0 until height) {
            val sourceRow = level.gridRows.getOrNull(r) ?: ".".repeat(width)
            for (c in 0 until width) {
                val ch = sourceRow.getOrNull(c) ?: '.'
                grid[r][c] = ch
                when (ch) {
                    TileType.SPAWN.code -> {
                        spawnX = c + (1f - PLAYER_W) / 2f
                        spawnY = r + (1f - PLAYER_H)
                        grid[r][c] = '.'
                    }
                    TileType.COIN.code -> totalCoins++
                    TileType.PATROL.code -> {
                        enemies.add(
                            EnemyEntity(
                                id = enemyId++,
                                kind = EnemyKind.PATROL,
                                startX = c + 0.11f,
                                startY = r + 0.22f,
                                x = c + 0.11f,
                                y = r + 0.22f,
                                vx = 2.5f
                            )
                        )
                        grid[r][c] = '.'
                    }
                    TileType.FLYER.code -> {
                        enemies.add(
                            EnemyEntity(
                                id = enemyId++,
                                kind = EnemyKind.FLYER,
                                startX = c + 0.11f,
                                startY = r + 0.11f,
                                x = c + 0.11f,
                                y = r + 0.11f,
                                vx = 2.2f,
                                phase = (c * 0.7f)
                            )
                        )
                        grid[r][c] = '.'
                    }
                    TileType.CHARGER.code -> {
                        enemies.add(
                            EnemyEntity(
                                id = enemyId++,
                                kind = EnemyKind.CHARGER,
                                startX = c + 0.11f,
                                startY = r + 0.22f,
                                x = c + 0.11f,
                                y = r + 0.22f,
                                vx = 3.4f
                            )
                        )
                        grid[r][c] = '.'
                    }
                }
            }
        }

        playerX = spawnX
        playerY = spawnY
        checkpointX = spawnX
        checkpointY = spawnY
        cameraX = (playerX - 6f).coerceAtLeast(0f)
    }

    fun setDirectionalInput(left: Boolean, right: Boolean) {
        inputLeft = left
        inputRight = right
    }

    fun triggerJump() {
        if (status != GameStatus.PLAYING) return
        jumpBufferTimer = 0.13f
        tryConsumeJump()
    }

    private fun tryConsumeJump() {
        if (jumpBufferTimer <= 0f) return
        if (onGround || coyoteTimer > 0f) {
            playerVy = JUMP_VY
            onGround = false
            coyoteTimer = 0f
            jumpBufferTimer = 0f
            jumpsLeft = 1
            spawnBurst(playerX + PLAYER_W / 2f, playerY + PLAYER_H, 0xFF00F0FF, 8, -2.5f)
        } else if (jumpsLeft > 0) {
            playerVy = DOUBLE_JUMP_VY
            jumpsLeft = 0
            jumpBufferTimer = 0f
            spawnBurst(playerX + PLAYER_W / 2f, playerY + PLAYER_H, 0xFF39FF14, 10, -2.0f)
        }
    }

    fun tileAt(col: Int, row: Int): Char {
        if (col < 0 || col >= width) return '#'
        if (row < 0) return '.'
        if (row >= height) return '.'
        return grid[row][col]
    }

    fun update(rawDt: Float): GameHudSnapshot {
        val dt = rawDt.coerceIn(0.001f, 0.030f)
        animTime += dt

        if (status != GameStatus.PLAYING) {
            updateParticles(dt)
            return snapshot()
        }

        elapsedSec += dt
        if (invulnTimer > 0f) invulnTimer = (invulnTimer - dt).coerceAtLeast(0f)
        if (shakeTimer > 0f) shakeTimer = (shakeTimer - dt).coerceAtLeast(0f)
        if (coyoteTimer > 0f) coyoteTimer = (coyoteTimer - dt).coerceAtLeast(0f)
        if (jumpBufferTimer > 0f) {
            jumpBufferTimer = (jumpBufferTimer - dt).coerceAtLeast(0f)
            tryConsumeJump()
        }

        val moveDir = when {
            inputLeft && !inputRight -> -1f
            inputRight && !inputLeft -> 1f
            else -> 0f
        }
        if (moveDir != 0f) {
            facingRight = moveDir > 0f
        }

        val targetVx = moveDir * MOVE_SPEED
        val accel = if (onGround) GROUND_ACCEL else AIR_ACCEL
        playerVx = moveToward(playerVx, targetVx, accel * dt)

        playerVy = (playerVy + GRAVITY * dt).coerceAtMost(MAX_FALL_SPEED)

        val prevBottom = playerY + PLAYER_H

        // Move X and resolve solid horizontal collisions
        playerX += playerVx * dt
        resolveHorizontalCollisions()

        // Move Y and resolve vertical collisions
        val wasOnGround = onGround
        onGround = false
        playerY += playerVy * dt
        resolveVerticalCollisions(prevBottom)

        if (onGround) {
            coyoteTimer = 0.11f
            jumpsLeft = 2
        } else if (wasOnGround && playerVy >= 0f) {
            coyoteTimer = 0.11f
        }

        // Running trail particles
        if (onGround && abs(playerVx) > 2.5f && Random.nextFloat() < 0.35f) {
            particles.add(
                Particle(
                    x = playerX + PLAYER_W / 2f,
                    y = playerY + PLAYER_H - 0.05f,
                    vx = -playerVx * 0.25f + (Random.nextFloat() - 0.5f) * 1.2f,
                    vy = -Random.nextFloat() * 1.5f,
                    life = 0.25f,
                    maxLife = 0.25f,
                    colorHex = 0xFF00F0FF,
                    size = 0.12f
                )
            )
        }

        // Tile triggers: Coins, Bounce, Checkpoint, Goal, Spikes, Laser
        checkTileInteractions()

        // Void fall check
        if (playerY > height + 1.2f) {
            handlePitOrLaserHit()
        }

        // Update enemies & check player-enemy collision
        updateEnemies(dt, prevBottom)

        // Update particles
        updateParticles(dt)

        return snapshot()
    }

    private fun resolveHorizontalCollisions() {
        if (playerX < 0f) {
            playerX = 0f
            playerVx = 0f
        } else if (playerX + PLAYER_W > width) {
            playerX = width - PLAYER_W
            playerVx = 0f
        }

        val topRow = floor(playerY + 0.06f).toInt()
        val bottomRow = floor(playerY + PLAYER_H - 0.06f).toInt()

        if (playerVx > 0f) {
            val rightCol = floor(playerX + PLAYER_W).toInt()
            for (r in topRow..bottomRow) {
                if (isSolidTile(tileAt(rightCol, r))) {
                    playerX = rightCol - PLAYER_W - 0.001f
                    playerVx = 0f
                    break
                }
            }
        } else if (playerVx < 0f) {
            val leftCol = floor(playerX).toInt()
            for (r in topRow..bottomRow) {
                if (isSolidTile(tileAt(leftCol, r))) {
                    playerX = leftCol + 1f + 0.001f
                    playerVx = 0f
                    break
                }
            }
        }
    }

    private fun resolveVerticalCollisions(prevBottom: Float) {
        val leftCol = floor(playerX + 0.05f).toInt()
        val rightCol = floor(playerX + PLAYER_W - 0.05f).toInt()

        if (playerVy >= 0f) {
            val bottomRow = floor(playerY + PLAYER_H).toInt()
            for (c in leftCol..rightCol) {
                val t = tileAt(c, bottomRow)
                val isOneWay = t == TileType.PLATFORM.code && prevBottom <= bottomRow.toFloat() + 0.18f
                if (isSolidTile(t) || isOneWay) {
                    playerY = bottomRow - PLAYER_H - 0.001f
                    playerVy = 0f
                    onGround = true
                    break
                } else if (t == TileType.BOUNCE.code && prevBottom <= bottomRow.toFloat() + 0.35f) {
                    playerY = bottomRow - PLAYER_H - 0.001f
                    playerVy = BOUNCE_VY
                    onGround = false
                    jumpsLeft = 1
                    spawnBurst(c + 0.5f, bottomRow.toFloat(), 0xFF39FF14, 12, -4f)
                    break
                }
            }
        } else {
            val topRow = floor(playerY).toInt()
            for (c in leftCol..rightCol) {
                if (isSolidTile(tileAt(c, topRow))) {
                    playerY = topRow + 1f + 0.001f
                    playerVy = 0f
                    break
                }
            }
        }
    }

    private fun checkTileInteractions() {
        val minCol = floor(playerX).toInt().coerceIn(0, width - 1)
        val maxCol = floor(playerX + PLAYER_W).toInt().coerceIn(0, width - 1)
        val minRow = floor(playerY).toInt().coerceIn(0, height - 1)
        val maxRow = floor(playerY + PLAYER_H).toInt().coerceIn(0, height - 1)

        for (r in minRow..maxRow) {
            for (c in minCol..maxCol) {
                val ch = grid[r][c]
                when (ch) {
                    TileType.COIN.code -> {
                        if (overlapsBox(playerX, playerY, PLAYER_W, PLAYER_H, c + 0.2f, r + 0.2f, 0.6f, 0.6f)) {
                            grid[r][c] = '.'
                            coinsCollected++
                            score += 100L
                            spawnBurst(c + 0.5f, r + 0.5f, 0xFFFFB800, 10, -2.5f)
                        }
                    }
                    TileType.CHECKPOINT.code -> {
                        if (!checkpointReached || checkpointX != c + 0.15f) {
                            checkpointX = c + (1f - PLAYER_W) / 2f
                            checkpointY = r + (1f - PLAYER_H)
                            if (!checkpointReached) {
                                checkpointReached = true
                                score += 50L
                                spawnBurst(c + 0.5f, r + 0.5f, 0xFF00E5A3, 14, -3f)
                            }
                        }
                    }
                    TileType.GOAL.code -> {
                        if (overlapsBox(playerX, playerY, PLAYER_W, PLAYER_H, c + 0.15f, r + 0.1f, 0.7f, 0.9f)) {
                            completeVictory()
                            return
                        }
                    }
                    TileType.BOUNCE.code -> {
                        if (overlapsBox(playerX, playerY, PLAYER_W, PLAYER_H, c + 0.1f, r + 0.55f, 0.8f, 0.45f) && playerVy >= -2f) {
                            playerVy = BOUNCE_VY
                            onGround = false
                            jumpsLeft = 1
                            spawnBurst(c + 0.5f, r + 0.6f, 0xFF39FF14, 12, -4f)
                        }
                    }
                    TileType.SPIKE.code -> {
                        // Forgiving inner hitbox for spikes
                        if (overlapsBox(playerX, playerY, PLAYER_W, PLAYER_H, c + 0.18f, r + 0.36f, 0.64f, 0.64f)) {
                            damagePlayer(knockbackX = if (playerX + PLAYER_W / 2f < c + 0.5f) -5f else 5f)
                        }
                    }
                    TileType.LASER.code -> {
                        if (overlapsBox(playerX, playerY, PLAYER_W, PLAYER_H, c + 0.08f, r + 0.2f, 0.84f, 0.8f)) {
                            handlePitOrLaserHit()
                            return
                        }
                    }
                }
            }
        }
    }

    private fun updateEnemies(dt: Float, prevPlayerBottom: Float) {
        for (enemy in enemies) {
            if (!enemy.alive) continue

            when (enemy.kind) {
                EnemyKind.PATROL -> {
                    val nextX = enemy.x + enemy.vx * dt
                    val checkCol = if (enemy.vx > 0f) floor(nextX + enemy.width).toInt() else floor(nextX).toInt()
                    val row = floor(enemy.y + 0.3f).toInt()
                    val belowRow = floor(enemy.y + enemy.height + 0.15f).toInt()

                    val hitsWall = checkCol < 0 || checkCol >= width || isSolidTile(tileAt(checkCol, row))
                    val floorAhead = tileAt(checkCol, belowRow)
                    val hasLedge = floorAhead == '.' || floorAhead == TileType.LASER.code || floorAhead == TileType.SPIKE.code

                    if (hitsWall || hasLedge) {
                        enemy.vx = -enemy.vx
                    } else {
                        enemy.x = nextX
                    }
                }
                EnemyKind.FLYER -> {
                    val nextX = enemy.x + enemy.vx * dt
                    val checkCol = if (enemy.vx > 0f) floor(nextX + enemy.width).toInt() else floor(nextX).toInt()
                    val row = floor(enemy.y + 0.35f).toInt()
                    val tooFar = abs(nextX - enemy.startX) > 3.4f
                    if (checkCol < 0 || checkCol >= width || isSolidTile(tileAt(checkCol, row)) || tooFar) {
                        enemy.vx = -enemy.vx
                    } else {
                        enemy.x = nextX
                    }
                    enemy.y = enemy.startY + sin(animTime * 3.0f + enemy.phase) * 0.45f
                }
                EnemyKind.CHARGER -> {
                    val dx = (playerX + PLAYER_W / 2f) - (enemy.x + enemy.width / 2f)
                    val dy = abs(playerY - enemy.y)
                    val chasing = dy < 1.5f && abs(dx) < 5.8f
                    val speed = if (chasing) 5.8f else 3.2f
                    if (chasing && abs(dx) > 0.25f) {
                        enemy.vx = if (dx > 0f) speed else -speed
                    } else {
                        enemy.vx = if (enemy.vx >= 0f) speed else -speed
                    }

                    val nextX = enemy.x + enemy.vx * dt
                    val checkCol = if (enemy.vx > 0f) floor(nextX + enemy.width).toInt() else floor(nextX).toInt()
                    val row = floor(enemy.y + 0.3f).toInt()
                    val belowRow = floor(enemy.y + enemy.height + 0.15f).toInt()
                    val hitsWall = checkCol < 0 || checkCol >= width || isSolidTile(tileAt(checkCol, row))
                    val floorAhead = tileAt(checkCol, belowRow)
                    val hasLedge = floorAhead == '.' || floorAhead == TileType.LASER.code || floorAhead == TileType.SPIKE.code

                    if (hitsWall || hasLedge) {
                        enemy.vx = -enemy.vx
                    } else {
                        enemy.x = nextX
                    }
                }
            }

            // Player-enemy collision
            if (overlapsBox(playerX, playerY, PLAYER_W, PLAYER_H, enemy.x, enemy.y, enemy.width, enemy.height)) {
                val isStomp = playerVy > 0f && prevPlayerBottom <= enemy.y + enemy.height * 0.64f
                if (isStomp) {
                    enemy.alive = false
                    playerVy = STOMP_BOUNCE_VY
                    jumpsLeft = 1
                    enemiesDefeated++
                    score += 150L
                    shakeTimer = 0.14f
                    val color = when (enemy.kind) {
                        EnemyKind.PATROL -> 0xFFFF6B35
                        EnemyKind.FLYER -> 0xFF9D4EDD
                        EnemyKind.CHARGER -> 0xFFFF3355
                    }
                    spawnBurst(enemy.x + enemy.width / 2f, enemy.y + enemy.height / 2f, color, 16, -3.5f)
                } else {
                    val kb = if (playerX + PLAYER_W / 2f < enemy.x + enemy.width / 2f) -5.5f else 5.5f
                    damagePlayer(knockbackX = kb)
                }
            }
        }
    }

    private fun damagePlayer(knockbackX: Float) {
        if (invulnTimer > 0f || status != GameStatus.PLAYING) return
        hearts = (hearts - 1).coerceAtLeast(0)
        shakeTimer = 0.25f
        spawnBurst(playerX + PLAYER_W / 2f, playerY + PLAYER_H / 2f, 0xFFFF2E93, 14, -3f)
        if (hearts <= 0) {
            status = GameStatus.GAME_OVER
            playerVx = 0f
            playerVy = 0f
        } else {
            invulnTimer = 1.4f
            playerVy = -7.8f
            playerVx = knockbackX
        }
    }

    private fun handlePitOrLaserHit() {
        if (status != GameStatus.PLAYING) return
        hearts = (hearts - 1).coerceAtLeast(0)
        shakeTimer = 0.25f
        spawnBurst(playerX + PLAYER_W / 2f, playerY + PLAYER_H / 2f, 0xFFFF3355, 16, -4f)
        if (hearts <= 0) {
            status = GameStatus.GAME_OVER
        } else {
            playerX = checkpointX
            playerY = checkpointY
            playerVx = 0f
            playerVy = 0f
            invulnTimer = 1.4f
            jumpsLeft = 2
        }
    }

    private fun completeVictory() {
        status = GameStatus.VICTORY
        val heartBonus = hearts * 150L
        val timeBonus = (600L - (elapsedSec.toLong() * 8L)).coerceAtLeast(0L)
        score += 300L + heartBonus + timeBonus
        earnedStars = when {
            hearts == maxHearts && (totalCoins == 0 || coinsCollected * 10 >= totalCoins * 8) -> 3
            hearts >= 2 || (totalCoins > 0 && coinsCollected * 2 >= totalCoins) -> 2
            else -> 1
        }
        spawnBurst(playerX + PLAYER_W / 2f, playerY + PLAYER_H / 2f, 0xFF39FF14, 24, -4.5f)
    }

    private fun spawnBurst(x: Float, y: Float, colorHex: Long, count: Int, baseVy: Float) {
        repeat(count) {
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = (Random.nextFloat() - 0.5f) * 7.5f,
                    vy = baseVy + (Random.nextFloat() - 0.5f) * 5.0f,
                    life = 0.45f + Random.nextFloat() * 0.25f,
                    maxLife = 0.7f,
                    colorHex = colorHex,
                    size = 0.12f + Random.nextFloat() * 0.10f
                )
            )
        }
    }

    private fun updateParticles(dt: Float) {
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vy += 12f * dt
            p.life -= dt
            if (p.life <= 0f) it.remove()
        }
    }

    private fun isSolidTile(ch: Char): Boolean = ch == TileType.SOLID.code

    private fun overlapsBox(
        ax: Float, ay: Float, aw: Float, ah: Float,
        bx: Float, by: Float, bw: Float, bh: Float
    ): Boolean {
        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by
    }

    private fun moveToward(current: Float, target: Float, maxDelta: Float): Float {
        return when {
            abs(target - current) <= maxDelta -> target
            target > current -> current + maxDelta
            else -> current - maxDelta
        }
    }

    fun snapshot(): GameHudSnapshot = GameHudSnapshot(
        status = status,
        hearts = hearts,
        maxHearts = maxHearts,
        score = score,
        coinsCollected = coinsCollected,
        totalCoins = totalCoins,
        enemiesDefeated = enemiesDefeated,
        elapsedMs = (elapsedSec * 1000f).toLong(),
        earnedStars = earnedStars,
        checkpointReached = checkpointReached
    )
}
