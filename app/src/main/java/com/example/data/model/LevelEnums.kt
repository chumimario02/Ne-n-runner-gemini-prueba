package com.example.data.model

enum class LevelDifficulty(
    val wireValue: String,
    val labelEs: String,
) {
    EASY("easy", "Fácil"),
    NORMAL("normal", "Normal"),
    HARD("hard", "Difícil"),
    EXTREME("extreme", "Extremo");

    companion object {
        fun fromWire(raw: String?): LevelDifficulty =
            entries.firstOrNull { it.wireValue.equals(raw, ignoreCase = true) } ?: NORMAL
    }
}

enum class LevelTheme(
    val wireValue: String,
    val labelEs: String,
) {
    CYAN("cyan", "Cian Eléctrico"),
    MAGENTA("magenta", "Pulso Magenta"),
    AMBER("amber", "Sol Sintético"),
    EMERALD("emerald", "Matrix Esmeralda");

    companion object {
        fun fromWire(raw: String?): LevelTheme =
            entries.firstOrNull { it.wireValue.equals(raw, ignoreCase = true) } ?: CYAN
    }
}
