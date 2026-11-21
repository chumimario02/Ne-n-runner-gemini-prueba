package com.example.data

import com.example.model.Difficulty
import com.example.model.LEVEL_GRID_HEIGHT
import com.example.model.LevelData

object CampaignLevels {

    private fun normalize(rows: List<String>): Pair<Int, List<String>> {
        val width = rows.maxOfOrNull { it.length } ?: 36
        val fixed = List(LEVEL_GRID_HEIGHT) { idx ->
            val raw = rows.getOrNull(idx) ?: ""
            val padChar = if (idx == LEVEL_GRID_HEIGHT - 1) '#' else '.'
            raw.padEnd(width, padChar).take(width)
        }
        return width to fixed
    }

    private fun buildLevel(
        id: String,
        index: Int,
        title: String,
        description: String,
        difficulty: Difficulty,
        rawRows: List<String>
    ): LevelData {
        val (width, fixedRows) = normalize(rawRows)
        return LevelData(
            id = id,
            title = title,
            description = description,
            difficulty = difficulty,
            width = width,
            height = LEVEL_GRID_HEIGHT,
            gridRows = fixedRows,
            authorId = "system",
            authorName = "Sistema Central",
            verified = true,
            published = true,
            isCampaign = true,
            campaignIndex = index
        )
    }

    val levels: List<LevelData> by lazy {
        listOf(
            // SECTOR 01: Tutorial / Easy — introduces movement, coins, basic gaps, and 2 patrol drones
            buildLevel(
                id = "sector_01",
                index = 0,
                title = "Sector 01 · Despertar Neón",
                description = "Calibra tus propulsores. Salta sobre drones patrulla o aplástalos desde arriba.",
                difficulty = Difficulty.EASY,
                rawRows = listOf(
                    "....................................",
                    "....................................",
                    "....................................",
                    "....................................",
                    "....................................",
                    ".......................CCC..........",
                    "......................=====.........",
                    "...........CCC......................",
                    "..........#####.....................",
                    ".....C...................C..........",
                    "....===........E........===......G..",
                    ".P.........#########.............#..",
                    "######..################..##########",
                    "######..################..##########"
                )
            ),
            // SECTOR 02: Easy-Medium — introduces bounce pads (B), jump-through platforms (=), and spikes (^)
            buildLevel(
                id = "sector_02",
                index = 1,
                title = "Sector 02 · Circuitos Altos",
                description = "Usa plataformas elevadas e impulsores verdes para esquivar los pinchos.",
                difficulty = Difficulty.EASY,
                rawRows = listOf(
                    "..........................................",
                    "..........................................",
                    "..........................................",
                    "..........................................",
                    "........................CCCC..............",
                    ".......................======.............",
                    "..............CCC.................CCC.....",
                    ".............=====...............=====....",
                    ".......C..............B...................",
                    "......###.....E......###.....E.........G..",
                    ".............####...........####......##..",
                    ".P......B.......^^....C........^^.....##..",
                    "#####..####..#######..####..#######..#####",
                    "#####..####..#######..####..#######..#####"
                )
            ),
            // SECTOR 03: Medium — introduces Hover Flyers (F), Checkpoints (K), and vertical jumps
            buildLevel(
                id = "sector_03",
                index = 2,
                title = "Sector 03 · Drones en Órbita",
                description = "Los drones voladores vigilan el espacio aéreo. Activa el checkpoint central.",
                difficulty = Difficulty.MEDIUM,
                rawRows = listOf(
                    "................................................",
                    "................................................",
                    "................................................",
                    "........................C.......................",
                    "...............F.......===........F.............",
                    ".............CCC.................CCC............",
                    "............=====...............=====...........",
                    ".......C.............K.................B......G.",
                    "......===...........###.....E.........###....###",
                    ".............E.............####.................",
                    "............####........................C.......",
                    ".P....B..............^^.........^^.....===......",
                    "####..###..######..######....######.......######",
                    "####..###..######..######....######.......######"
                )
            ),
            // SECTOR 04: Hard — introduces Laser Pits (~) and fast Dash Chargers (S)
            buildLevel(
                id = "sector_04",
                index = 3,
                title = "Sector 04 · Pulso Láser",
                description = "Cuidado con los cazadores veloces y las fosas de plasma láser.",
                difficulty = Difficulty.HARD,
                rawRows = listOf(
                    "....................................................",
                    "....................................................",
                    "....................................................",
                    "...........................CCC......................",
                    ".............F............=====.......F.............",
                    "...........=====.....................===............",
                    "......C............K.............C............C.....",
                    ".....===..........###.....S.....===..........===..G.",
                    ".........................#####...................###",
                    ".............E........................B.............",
                    "...........#####.....................###............",
                    ".P....B............^^..........^^................... ",
                    "####..###..~~~~..######..~~..######..~~~~..#########",
                    "####..###..####..######..##..######..####..#########"
                ).map { it.trimEnd() }
            ),
            // SECTOR 05: Very Hard — multi-tier Cyber Fortress with mixed Patrols, Flyers, and Chargers
            buildLevel(
                id = "sector_05",
                index = 4,
                title = "Sector 05 · Fortaleza Cyber",
                description = "Asalta la torre de seguridad combinando doble salto y rebotes precisos.",
                difficulty = Difficulty.HARD,
                rawRows = listOf(
                    "........................................................",
                    "........................................................",
                    ".......................................CCC..............",
                    "..........................F...........=====..........G..",
                    "...................CCC...===................F.......###.",
                    "..................=====.............C......===..........",
                    "...........B.................K.....===..................",
                    "..........###......S........###.............S...........",
                    "..................####....................#####.........",
                    ".....C......E..................E........................",
                    "....===...#####.......B.......####....B.................",
                    ".P...................###.............###......^^........",
                    "###..~~~..######..~~..####..~~..####..~~..##############",
                    "###..###..######..##..####..##..####..##..##############"
                )
            ),
            // SECTOR 06: Extreme — Finale gauntlet
            buildLevel(
                id = "sector_06",
                index = 5,
                title = "Sector 06 · Núcleo Omega",
                description = "El desafío definitivo. Enjambres de drones, cazadores y núcleo de plasma.",
                difficulty = Difficulty.EXTREME,
                rawRows = listOf(
                    "............................................................",
                    "............................................................",
                    ".................................................CCC........",
                    ".........................F..........F...........=====....G..",
                    ".................CCC....===........===..................###.",
                    "................=====.....................B.................",
                    "..........B.................K............###......S.........",
                    ".........###......S........###.....S.............####.......",
                    ".................####.............####......................",
                    ".....F........................E.............F...............",
                    "....===.....E.........B......####....B.....===......C.......",
                    ".P.........####......###............###............===......",
                    "###..~~~~..#####..~~..###..~~~~~~~..###..~~~~~~~..##########",
                    "###..####..#####..##..###..#######..###..#######..##########"
                )
            )
        )
    }
}
