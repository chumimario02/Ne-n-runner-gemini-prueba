package com.example.data.model

enum class TileType(
    val code: Char,
    val labelEs: String,
    val shortTag: String,
    val isEnemy: Boolean = false,
) {
    EMPTY('.', "Vacío", "·"),
    SOLID('#', "Bloque Neón", "BLQ"),
    SPAWN('S', "Inicio", "INI"),
    GOAL('G', "Portal Meta", "META"),
    SPIKE('^', "Pinchos Láser", "PNC"),
    BOUNCE('B', "Impulsor", "IMP"),
    MOVING_H('M', "Móvil Horiz.", "M-H"),
    MOVING_V('V', "Móvil Vert.", "M-V"),
    COIN('C', "Fragmento", "FRG"),
    CHECKPOINT('K', "Checkpoint", "CHK"),
    PATROL_BOT('P', "Bot Patrulla", "PAT", isEnemy = true),
    FLYING_DRONE('F', "Dron Volador", "DRN", isEnemy = true),
    TURRET('T', "Torreta Láser", "TOR", isEnemy = true),
    CHASER_BOT('H', "Cazador Neón", "CAZ", isEnemy = true);

    companion object {
        fun fromCode(ch: Char): TileType = entries.firstOrNull { it.code == ch } ?: EMPTY
    }
}
