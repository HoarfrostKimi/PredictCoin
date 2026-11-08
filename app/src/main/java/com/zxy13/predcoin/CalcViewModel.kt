package com.zxy13.predcoin

import androidx.lifecycle.ViewModel
import com.zxy13.predcoin.data.Record
import com.zxy13.predcoin.data.Store
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UiState(
    val balance: Double = 1100.0,
    val p: Double = 0.45,
    val q: Double = 0.52,
    val optA: String = "EDG",
    val optB: String = "BLG",
    val solo: Boolean = false,
    val soloOpposite: Boolean = false,
    val conf: Double = 0.6,
    val maxFrac: Double = 0.05,
    val expCap: Double = 0.20,
    val expUsed: Double = 0.0,
    val drift: Double = 0.02,
    val kellyFrac: Double = 0.5,
    val result: CalcResult = CalcEngine.calculate(1100.0, 0.45, 0.52),
    val stakeLow: Int = 0,
    val stakeHigh: Int = 0
)

class CalcViewModel : ViewModel() {

    private val _state = MutableStateFlow(compute())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private fun compute(): UiState {
        val streak = loseStreak()
        val result = CalcEngine.calculate(
            balance = Store.balance,
            pRaw = Store.p,
            q = Store.q,
            conf = Store.conf,
            drift = Store.drift,
            maxFrac = Store.maxFrac,
            expCap = Store.expCap,
            expUsed = Store.expUsed,
            kellyFrac = Store.kellyFrac,
            loseStreak = streak
        )
        val range = CalcEngine.stakeRange(
            balance = Store.balance,
            pRaw = Store.p,
            q = Store.q,
            conf = Store.conf,
            drift = Store.drift,
            maxFrac = Store.maxFrac,
            expCap = Store.expCap,
            expUsed = Store.expUsed,
            kellyFrac = Store.kellyFrac,
            loseStreak = streak
        )
        // 下界为 0 说明优势很薄，区间退化成点估计，避免出现「0 ~ 20 币」
        val low = if (range.first > 0) range.first else result.stake
        val high = if (range.first > 0) range.last else result.stake

        return UiState(
            balance = Store.balance,
            p = Store.p,
            q = Store.q,
            optA = Store.optA,
            optB = Store.optB,
            solo = Store.solo,
            soloOpposite = Store.soloOpposite,
            conf = Store.conf,
            maxFrac = Store.maxFrac,
            expCap = Store.expCap,
            expUsed = Store.expUsed,
            drift = Store.drift,
            kellyFrac = Store.kellyFrac,
            result = result,
            stakeLow = low,
            stakeHigh = high
        )
    }

    /** 末尾连续猜错的场数，用于连败降档 */
    private fun loseStreak(): Int {
        var n = 0
        for (r in Store.records) {
            if (r.result == "lose") n++ else break
        }
        return n
    }

    private fun refresh(persist: Boolean = true) {
        _state.value = compute()
        if (persist) Store.save()
    }

    /** 从详情页返回后重新读取 Store（余额、参数可能被改过） */
    fun reload() {
        _state.value = compute()
    }

    fun setBalance(v: Double) {
        Store.balance = v.coerceAtLeast(0.0)
        refresh()
    }

    fun setP(v: Double) {
        Store.p = v.coerceIn(0.005, 0.995)
        refresh()
    }

    fun setQ(v: Double) {
        Store.q = v.coerceIn(0.005, 0.995)
        refresh()
    }

    fun setSolo(v: Boolean) {
        if (v == Store.solo) return
        // 离开冠军模式时，把「不夺冠」的补集数值翻回「夺冠」
        if (!v && Store.soloOpposite) {
            Store.p = (1.0 - Store.p).coerceIn(0.005, 0.995)
            Store.q = (1.0 - Store.q).coerceIn(0.005, 0.995)
            Store.soloOpposite = false
        }
        Store.solo = v
        refresh()
    }

    /** 冠军模式：在「夺冠 / 不夺冠」之间转换，p、q 同时取补 */
    fun setSoloOpposite(v: Boolean) {
        if (!Store.solo || v == Store.soloOpposite) return
        Store.soloOpposite = v
        Store.p = (1.0 - Store.p).coerceIn(0.005, 0.995)
        Store.q = (1.0 - Store.q).coerceIn(0.005, 0.995)
        refresh()
    }

    fun setOptA(v: String) {
        Store.optA = v.uppercase(Locale.ROOT).take(12)
        refresh()
    }

    fun setOptB(v: String) {
        Store.optB = v.uppercase(Locale.ROOT).take(12)
        refresh()
    }

    /** 换边：p、q 同时取补，队名互换 */
    fun swapSides() {
        Store.p = (1.0 - Store.p).coerceIn(0.005, 0.995)
        Store.q = (1.0 - Store.q).coerceIn(0.005, 0.995)
        val a = Store.optA
        Store.optA = Store.optB
        Store.optB = a
        refresh()
    }

    /** 记录本场，状态记为待定 */
    fun addRecord(): Record {
        val s = _state.value
        val record = Record(
            id = System.currentTimeMillis().toString(),
            date = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date()),
            match = when {
                s.solo && s.soloOpposite -> "${s.optA} 不夺冠"
                s.solo -> "${s.optA} 夺冠"
                else -> "${s.optA} vs ${s.optB}"
            },
            opt = if (s.solo && s.soloOpposite) "${s.optA} 不夺冠" else s.optA,
            q = s.q,
            p = s.p,
            stake = s.result.stake,
            result = "pending"
        )
        Store.records.add(0, record)
        refresh()
        return record
    }
}
