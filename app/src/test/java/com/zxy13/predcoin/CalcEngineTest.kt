package com.zxy13.predcoin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalcEngineTest {

    /** 有正期望：1100 余额、p=45%、q=52%、可信度 0.6、漂移 +2pp → 应投 38 币 */
    @Test
    fun positiveEdge_recommends38() {
        val r = CalcEngine.calculate(
            balance = 1100.0,
            pRaw = 0.45,
            q = 0.52,
            conf = 0.6,
            drift = 0.02,
            maxFrac = 0.05,
            kellyFrac = 0.5,
            loseStreak = 0
        )
        assertEquals(38, r.stake)
    }

    /** 区间：必须把点估计夹在中间，且上下界有区分 */
    @Test
    fun stakeRange_bracketsPointEstimate() {
        val range = CalcEngine.stakeRange(
            balance = 1100.0,
            pRaw = 0.45,
            q = 0.52,
            conf = 0.6,
            drift = 0.02,
            maxFrac = 0.05,
            kellyFrac = 0.5,
            loseStreak = 0
        )
        assertTrue(range.first < 38)
        assertTrue(range.last > 38)
    }

    /** 无正期望：q=44% 低于 p=45% → 必须不建议下注，投入为 0 */
    @Test
    fun negativeEdge_recommendsNothing() {
        val r = CalcEngine.calculate(
            balance = 1100.0,
            pRaw = 0.45,
            q = 0.44,
            conf = 0.6,
            drift = 0.02,
            maxFrac = 0.05,
            kellyFrac = 0.5,
            loseStreak = 0
        )
        assertEquals(0, r.stake)
        assertFalse(r.recommended)
    }
}
