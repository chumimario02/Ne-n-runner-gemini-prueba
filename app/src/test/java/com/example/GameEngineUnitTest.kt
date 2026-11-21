package com.example

import com.example.data.CampaignLevels
import com.example.game.GameEngine
import com.example.game.GameStatus
import com.example.model.LEVEL_GRID_HEIGHT
import com.example.model.LevelData
import com.example.model.validateLevelGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineUnitTest {

    @Test
    fun allCampaignLevelsAreValidAndProgressive() {
        assertEquals(6, CampaignLevels.levels.size)
        CampaignLevels.levels.forEachIndexed { idx, level ->
            assertEquals(idx, level.campaignIndex)
            assertEquals(LEVEL_GRID_HEIGHT, level.gridRows.size)
            val result = validateLevelGrid(level.gridRows, level.width)
            assertTrue("Campaign level ${level.id} invalid: ${result.errorEs}", result.isValid)
        }
    }

    @Test
    fun playerCollectsCoinAndReachesGoal() {
        val rows = MutableList(LEVEL_GRID_HEIGHT) { ".".repeat(24) }
        rows[LEVEL_GRID_HEIGHT - 1] = "#".repeat(24)
        rows[LEVEL_GRID_HEIGHT - 2] = ".P.C.G" + ".".repeat(18)

        val level = LevelData(
            id = "test_lvl",
            title = "Test",
            width = 24,
            gridRows = rows
        )
        val engine = GameEngine(level)
        engine.setDirectionalInput(left = false, right = true)

        repeat(90) {
            engine.update(0.016f)
        }

        assertEquals(1, engine.coinsCollected)
        assertEquals(GameStatus.VICTORY, engine.status)
        assertTrue(engine.earnedStars >= 2)
    }

    @Test
    fun playerStompsPatrolEnemyAndBounces() {
        val rows = MutableList(LEVEL_GRID_HEIGHT) { ".".repeat(24) }
        rows[LEVEL_GRID_HEIGHT - 1] = "#".repeat(24)
        rows[LEVEL_GRID_HEIGHT - 2] = "....E.............G....."
        rows[LEVEL_GRID_HEIGHT - 4] = "....P..................."

        val level = LevelData(
            id = "stomp_lvl",
            title = "Stomp",
            width = 24,
            gridRows = rows
        )
        val engine = GameEngine(level)

        repeat(45) {
            engine.update(0.016f)
        }

        assertEquals(1, engine.enemiesDefeated)
        assertEquals(3, engine.hearts)
    }
}
