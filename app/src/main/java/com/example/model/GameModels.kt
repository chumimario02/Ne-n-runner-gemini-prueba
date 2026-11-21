package com.example.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCrimson
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonLime
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonPurple

const val LEVEL_GRID_HEIGHT = 14
const val MIN_LEVEL_WIDTH = 24
const val MAX_LEVEL_WIDTH = 64

enum class TileType(
    val code: Char,
    val labelEs: String,
    val shortLabelEs: String,
    val color: Color
) {
    EMPTY('.', "Borrador", "Borrar", Color(0xFF283266)),
    SOLID('#', "Bloque Neón", "Bloque", NeonCyan),
    PLATFORM('=', "Plataforma", "Plat.", Color(0xFF4CC9F0)),
    BOUNCE('B', "Impulsor", "Salto+", NeonLime),
    SPIKE('^', "Pinchos", "Pinchos", NeonMagenta),
    LASER('~', "Láser Mortal", "Láser", NeonCrimson),
    COIN('C', "Fragmento", "Moneda", NeonAmber),
    PATROL('E', "Dron Patrulla", "Patrulla", Color(0xFFFF6B35)),
    FLYER('F', "Dron Volador", "Volador", NeonPurple),
    CHARGER('S', "Cazador Veloz", "Cazador", NeonCrimson),
    CHECKPOINT('K', "Checkpoint", "Punto", Color(0xFF00E5A3)),
    SPAWN('P', "Inicio Jugador", "Inicio", Color(0xFFFFFFFF)),
    GOAL('G', "Portal Meta", "Meta", NeonLime);

    companion object {
        fun fromChar(c: Char): TileType = entries.firstOrNull { it.code == c } ?: EMPTY
    }
}

enum class Difficulty(
    val labelEs: String,
    val accentColor: Color
) {
    EASY("Fácil", NeonLime),
    MEDIUM("Medio", NeonCyan),
    HARD("Difícil", NeonAmber),
    EXTREME("Extremo", NeonMagenta);

    companion object {
        fun fromRaw(raw: String?): Difficulty =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: MEDIUM
    }
}

data class LevelData(
    val id: String = "",
    val title: String = "Nuevo Sector",
    val description: String = "",
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val width: Int = 36,
    val height: Int = LEVEL_GRID_HEIGHT,
    val gridRows: List<String> = defaultStarterGrid(36),
    val authorId: String = "",
    val authorName: String = "Runner",
    val verified: Boolean = false,
    val published: Boolean = false,
    val playsCount: Long = 0L,
    val likesCount: Long = 0L,
    val isCampaign: Boolean = false,
    val campaignIndex: Int = -1,
    val publishedLevelId: String? = null
)

data class LevelValidationResult(
    val isValid: Boolean,
    val errorEs: String? = null
)

fun validateLevelGrid(gridRows: List<String>, width: Int): LevelValidationResult {
    if (gridRows.size != LEVEL_GRID_HEIGHT) {
        return LevelValidationResult(false, "El mapa debe tener $LEVEL_GRID_HEIGHT filas.")
    }
    var spawnCount = 0
    var goalCount = 0
    for (row in gridRows) {
        if (row.length != width) {
            return LevelValidationResult(false, "Todas las filas deben tener $width columnas.")
        }
        for (ch in row) {
            if (ch == TileType.SPAWN.code) spawnCount++
            if (ch == TileType.GOAL.code) goalCount++
        }
    }
    if (spawnCount != 1) {
        return LevelValidationResult(false, "Coloca exactamente 1 punto de Inicio (P).")
    }
    if (goalCount < 1) {
        return LevelValidationResult(false, "Coloca al menos 1 Portal Meta (G).")
    }
    return LevelValidationResult(true)
}

fun defaultStarterGrid(width: Int = 36): List<String> {
    val safeWidth = width.coerceIn(MIN_LEVEL_WIDTH, MAX_LEVEL_WIDTH)
    val rows = MutableList(LEVEL_GRID_HEIGHT) { ".".repeat(safeWidth) }
    val floor = "#".repeat(safeWidth)
    rows[LEVEL_GRID_HEIGHT - 1] = floor
    rows[LEVEL_GRID_HEIGHT - 2] = buildString {
        append('.')
        append('P')
        for (i in 2 until safeWidth - 3) {
            append(if (i == safeWidth / 2) 'C' else '.')
        }
        append('G')
        append("..")
    }
    rows[LEVEL_GRID_HEIGHT - 5] = buildString {
        for (i in 0 until safeWidth) {
            append(if (i in (safeWidth / 2 - 2)..(safeWidth / 2 + 2)) '=' else '.')
        }
    }
    return rows
}

fun resizeGridRows(current: List<String>, newWidth: Int): List<String> {
    val targetWidth = newWidth.coerceIn(MIN_LEVEL_WIDTH, MAX_LEVEL_WIDTH)
    return List(LEVEL_GRID_HEIGHT) { rowIndex ->
        val row = current.getOrNull(rowIndex) ?: ".".repeat(targetWidth)
        val fillChar = if (rowIndex == LEVEL_GRID_HEIGHT - 1) '#' else '.'
        when {
            row.length == targetWidth -> row
            row.length > targetWidth -> row.substring(0, targetWidth)
            else -> row + fillChar.toString().repeat(targetWidth - row.length)
        }
    }
}
