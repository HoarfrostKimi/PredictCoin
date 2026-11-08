package com.zxy13.predcoin

import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.max
import kotlin.math.min

data class CalcResult(
    val recommended: Boolean,
    val stake: Int,          // 建议投入（整数币）
    val pEff: Double,        // 计入漂移后的池比例
    val qAdj: Double,        // 计入可信度收缩后的概率
    val edge: Double,        // qAdj - pEff
    val fKelly: Double,      // 理论凯利比例
    val fFinal: Double,      // 各层折扣后的比例
    val payout: Double,      // 猜中返还（含本金）
    val netWin: Double,      // 猜中净赚
    val ev: Double,          // 期望净收益
    val breakeven: Double,   // 盈亏平衡胜率
    val bindBy: String       // 被哪条约束卡住
)

object CalcEngine {

    fun payoutOf(stake: Double, p: Double): Double =
        if (stake <= 0.0) 0.0 else stake / p

    /**
     * 建议投入的合理区间：把「封口前池比例漂移」上下各浮动 [driftTol] 跑一遍，
     * 得到仓位下界与上界。池子漂得比预期多，仓位就该小一点；漂得少，就可以多一点。
     */
    fun stakeRange(
        balance: Double,
        pRaw: Double,
        q: Double,
        conf: Double = 0.6,
        drift: Double = 0.02,
        maxFrac: Double = 0.05,
        expCap: Double = 0.20,
        expUsed: Double = 0.0,
        kellyFrac: Double = 0.5,
        loseStreak: Int = 0,
        driftTol: Double = 0.005
    ): IntRange {
        val low = calculate(
            balance = balance, pRaw = pRaw, q = q, conf = conf, drift = drift + driftTol,
            maxFrac = maxFrac, expCap = expCap, expUsed = expUsed,
            kellyFrac = kellyFrac, loseStreak = loseStreak
        ).stake
        val high = calculate(
            balance = balance, pRaw = pRaw, q = q, conf = conf, drift = drift - driftTol,
            maxFrac = maxFrac, expCap = expCap, expUsed = expUsed,
            kellyFrac = kellyFrac, loseStreak = loseStreak
        ).stake
        return minOf(low, high)..maxOf(low, high)
    }

    fun calculate(
        balance: Double,
        pRaw: Double,
        q: Double,
        conf: Double = 0.6,       // 对盘口可信度 0..1
        drift: Double = 0.02,     // 封盘前池比例漂移
        maxFrac: Double = 0.05,   // 单场上限
        expCap: Double = 0.20,    // 总敞口上限
        expUsed: Double = 0.0,    // 其他场次已占用
        kellyFrac: Double = 0.5,  // 凯利分数
        loseStreak: Int = 0       // 连续猜错次数
    ): CalcResult {

        val W = max(balance, 1.0)
        val pEff = (pRaw + drift).coerceIn(0.002, 0.998)

        // 1) 可信度收缩：可信度越低，优势被削得越狠
        val shrink = 0.35 + 0.65 * conf
        val qAdj = pEff + (q - pEff) * shrink
        val edge = qAdj - pEff

        // 2) 理论凯利（由 E[ln W] 求导得到）
        val fKelly = if (edge > 0.0) edge / (1.0 - pEff) else 0.0

        // 3) 连败降档 + 分数凯利
        val streakF = 0.86.pow(min(loseStreak, 8).toDouble())
        val fFinal = fKelly * kellyFrac * streakF

        // 4) 四道约束取最小
        val capStake = W * maxFrac
        val capExp = max(W * expCap - expUsed, 0.0)
        val capHard = W * 0.5
        val raw = W * fFinal

        var bindBy = "凯利计算值"
        var cap = capStake
        if (capExp < cap) { cap = capExp; bindBy = "总敞口上限" }
        if (capHard < cap) { cap = capHard; bindBy = "硬性上限" }

        val stakeRaw = min(raw, cap)
        val recommended = fKelly > 0.0 && stakeRaw >= 0.5
        val stake = if (recommended) stakeRaw.roundToInt().coerceAtLeast(1) else 0

        val payout = payoutOf(stake.toDouble(), pEff)
        val netWin = payout - stake
        val ev = if (stake > 0) q * payout - stake else 0.0
        val breakeven = if (payout > 0) stake / payout else pEff

        return CalcResult(
            recommended, stake, pEff, qAdj, edge,
            fKelly, fFinal, payout, netWin, ev, breakeven,
            if (raw <= cap) "凯利计算值" else bindBy
        )
    }
}
