package com.zxy13.predcoin

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zxy13.predcoin.data.Record
import com.zxy13.predcoin.data.Store
import com.zxy13.predcoin.ui.AppColors
import com.zxy13.predcoin.ui.components.IOSCard
import com.zxy13.predcoin.ui.components.IOSSegmented
import com.zxy13.predcoin.ui.components.IOSSlider
import com.zxy13.predcoin.ui.components.MiniChart
import com.zxy13.predcoin.ui.components.SwipeToDeleteRow
import com.zxy13.predcoin.ui.components.Tnum
import com.zxy13.predcoin.ui.theme.PredictCoinTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

class DetailActivity : ComponentActivity() {

    private val vm: DetailViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            PredictCoinTheme {
                DetailScreen(
                    vm = vm,
                    onBack = { finish() },
                    onOpenAll = { openHistory() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 全部记录页可能删改过记录，回来时重新读取
        vm.reload()
    }

    @Suppress("DEPRECATION")
    private fun openHistory() {
        startActivity(Intent(this, HistoryActivity::class.java))
        overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
    }

    @Suppress("DEPRECATION")
    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.fade_in, R.anim.slide_out_right)
    }
}

/* ───────────────────────── 状态 ───────────────────────── */

data class DetailUiState(
    val balance: Double = 1100.0,
    val p: Double = 0.45,
    val q: Double = 0.52,
    val optA: String = "EDG",
    val optB: String = "BLG",
    val solo: Boolean = false,
    val conf: Double = 0.6,
    val maxFrac: Double = 0.05,
    val expCap: Double = 0.20,
    val expUsed: Double = 0.0,
    val pendingCount: Int = 0,
    val drift: Double = 0.02,
    val kellyFrac: Double = 0.5,
    val loseStreak: Int = 0,
    val result: CalcResult = CalcEngine.calculate(1100.0, 0.45, 0.52),
    val stakeLow: Int = 0,
    val stakeHigh: Int = 0,
    val records: List<Record> = emptyList()
)

class DetailViewModel : ViewModel() {

    private val _state = MutableStateFlow(compute())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private fun compute(): DetailUiState {
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
        val low = if (range.first > 0) range.first else result.stake
        val high = if (range.first > 0) range.last else result.stake

        return DetailUiState(
            balance = Store.balance,
            p = Store.p,
            q = Store.q,
            optA = Store.optA,
            optB = Store.optB,
            solo = Store.solo,
            conf = Store.conf,
            maxFrac = Store.maxFrac,
            expCap = Store.expCap,
            expUsed = Store.expUsed,
            pendingCount = Store.pendingCount,
            drift = Store.drift,
            kellyFrac = Store.kellyFrac,
            loseStreak = streak,
            result = result,
            stakeLow = low,
            stakeHigh = high,
            records = Store.records.toList()
        )
    }

    private fun loseStreak(): Int {
        var n = 0
        for (r in Store.records) {
            if (r.result == "lose") n++ else break
        }
        return n
    }

    private fun refresh() {
        _state.value = compute()
        Store.save()
    }

    /** 从「全部记录」返回后重新读取 Store（可能删过记录、改过状态、动过余额） */
    fun reload() {
        _state.value = compute()
    }

    fun setConf(v: Double) {
        Store.conf = v.coerceIn(0.0, 1.0)
        refresh()
    }

    fun setMaxFrac(v: Double) {
        Store.maxFrac = v.coerceIn(0.005, 0.5)
        refresh()
    }

    fun setExpCap(v: Double) {
        Store.expCap = v.coerceIn(0.01, 1.0)
        refresh()
    }

    fun setDrift(v: Double) {
        Store.drift = v.coerceIn(-0.2, 0.2)
        refresh()
    }

    fun setKellyFrac(v: Double) {
        Store.kellyFrac = v
        refresh()
    }

    /** 待定 → 猜中 → 猜错 → 待定 */
    fun cycleRecord(id: String) {
        Store.cycleRecordResult(id)
        refresh()
    }

    /** 删除一条记录 */
    fun deleteRecord(id: String) {
        Store.removeRecord(id)
        refresh()
    }
}

/* ───────────────────────── 格式化 ───────────────────────── */

private fun fmtPct(v: Double): String = String.format(Locale.US, "%.1f%%", v * 100)

private fun fmtPct2(v: Double): String = String.format(Locale.US, "%.2f%%", v * 100)

private fun fmtPp(v: Double): String = String.format(Locale.US, "%.1f", v)

private fun fmtSigned(v: Double, digits: Int = 1): String {
    val sign = if (v >= 0) "+" else "-"
    return sign + String.format(Locale.US, "%.${digits}f", abs(v))
}

private fun signedPp(v: Double): String = fmtSigned(v, 1) + "pp"

private fun kellyLabel(frac: Double): String = when {
    frac <= 0.3 -> "¼"
    frac <= 0.6 -> "½"
    else -> "全"
}

/** 详情页历史卡片默认展示的场数 */
internal const val RECORD_PREVIEW_COUNT = 3

/* ───────────────────────── 风险评分 ───────────────────────── */

private data class RiskInfo(val score: Int, val label: String, val reasons: List<String>)

private fun riskInfo(state: DetailUiState): RiskInfo {
    val r = state.result
    val w = state.balance.coerceAtLeast(1.0)
    val frac = r.stake / w
    var score = 0
    val reasons = mutableListOf<String>()

    // 仓位占比
    val posScore = when {
        frac < 0.01 -> 0
        frac < 0.03 -> 1
        frac < 0.05 -> 2
        frac < 0.10 -> 3
        else -> 4
    }
    score += posScore
    when (posScore) {
        2 -> reasons += "仓位占资金 ${fmtPct(frac)}，连错两把就很伤"
        3, 4 -> reasons += "仓位占资金 ${fmtPct(frac)}，单场偏重"
    }

    // 优势大小
    val edgePp = (state.q - state.p) * 100
    when {
        edgePp < 2.0 -> {
            score += 2
            reasons += "价差只有 ${fmtPp(edgePp)}pp，优势很薄，抄错一个数就没了"
        }

        edgePp < 5.0 -> {
            score += 1
            reasons += "价差 ${fmtPp(edgePp)}pp，属于小优势"
        }

        edgePp > 20.0 -> {
            score += 2
            reasons += "价差 ${fmtPp(edgePp)}pp 大得不像真的，先确认盘口和选项没抄错"
        }

        else -> {
            reasons += "价差 ${fmtPp(edgePp)}pp 属于中等区间，盘口抄错 1pp 就会吃掉大截期望"
        }
    }

    // 可信度
    if (state.conf < 0.4) {
        score += 2
        reasons += "对盘口可信度只有 ${fmtPct(state.conf)}，参考价值有限"
    } else if (state.conf < 0.6) {
        score += 1
        reasons += "对盘口可信度 ${fmtPct(state.conf)}，已打折使用"
    }

    // 极端盘口
    if (state.p < 0.12 || state.p > 0.88) {
        score += 2
        reasons += "池比例 ${fmtPct(state.p)} 处在极端区，冷门和大热门都容易大幅摆动"
    }

    // 连败
    if (state.loseStreak >= 3) {
        score += 1
        reasons += "最近 ${state.loseStreak} 场连续猜错，仓位已自动降档"
    }

    val capped = min(score, 10)
    val label = when {
        capped <= 2 -> "风险很低"
        capped <= 3 -> "风险较低"
        capped <= 5 -> "风险中等"
        capped <= 7 -> "风险偏高"
        else -> "风险很高"
    }

    val all = if (reasons.isEmpty()) {
        mutableListOf("各项参数都在常规区间，按建议金额投即可")
    } else {
        reasons
    }
    if (all.size < 2) all += "没有触发其他风险项"
    return RiskInfo(capped, label, all.take(4))
}

private fun riskColor(score: Int): Color = when {
    score <= 3 -> AppColors.accent
    score <= 5 -> Color(0xFFFF9500)
    else -> AppColors.red
}

/* ───────────────────────── 页面 ───────────────────────── */

@Composable
private fun DetailScreen(vm: DetailViewModel, onBack: () -> Unit, onOpenAll: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize(), color = AppColors.bg) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            TopBar(title = "详细数据", onBack = onBack)
            Spacer(Modifier.height(16.dp))
            ComparisonCard(state)
            Spacer(Modifier.height(16.dp))
            EdgeCard(state)
            Spacer(Modifier.height(16.dp))
            LadderCard(state)
            Spacer(Modifier.height(16.dp))
            RiskCard(state)
            Spacer(Modifier.height(16.dp))
            SensitivityCard(state)
            Spacer(Modifier.height(16.dp))
            ChartCard(state)
            Spacer(Modifier.height(16.dp))
            HistoryCard(
                state = state,
                onCycle = { vm.cycleRecord(it) },
                onDelete = { vm.deleteRecord(it) },
                onOpenAll = onOpenAll
            )
            Spacer(Modifier.height(16.dp))
            AdvancedCard(state, vm)
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
fun TopBar(title: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "‹ 返回",
                fontSize = 15.sp,
                color = AppColors.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onBack() }
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            )
            Text(
                text = title,
                fontSize = 13.sp,
                color = AppColors.textSecondary
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(AppColors.separator)
        )
    }
}

@Composable
private fun ComparisonCard(state: DetailUiState) {
    IOSCard {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.trackOff)
            )
            Box(
                Modifier
                    .fillMaxWidth(state.p.toFloat())
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.accent.copy(alpha = 0.85f))
            )
            val dot = 14.dp
            val dotX = (maxWidth * state.q.toFloat() - dot / 2)
                .coerceIn(0.dp, (maxWidth - dot).coerceAtLeast(0.dp))
            Box(
                Modifier
                    .offset(x = dotX)
                    .size(dot)
                    .align(Alignment.CenterStart)
                    .shadow(2.dp, CircleShape)
                    .background(Color.White, CircleShape)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "B 站 ${fmtPct(state.p)}",
                fontSize = 13.sp,
                color = AppColors.textSecondary,
                style = Tnum
            )
            Text(
                text = "盘口 ${fmtPct(state.q)}",
                fontSize = 13.sp,
                color = AppColors.textSecondary,
                style = Tnum
            )
        }
    }
}

@Composable
private fun EdgeCard(state: DetailUiState) {
    val r = state.result
    IOSCard {
        EdgeRow("原始优势（盘口 - B站）", (state.q - state.p) * 100)
        Spacer(Modifier.height(10.dp))
        EdgeRow("封口漂移后（模型使用）", (state.q - r.pEff) * 100)
        Spacer(Modifier.height(10.dp))
        EdgeRow("可信度收缩后（最终采用）", r.edge * 100)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "盈亏平衡胜率 ${fmtPct(r.breakeven)}",
            fontSize = 13.sp,
            color = AppColors.textSecondary,
            style = Tnum
        )
    }
}

@Composable
private fun EdgeRow(label: String, pp: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 15.sp, color = AppColors.textSecondary)
        Text(
            text = signedPp(pp),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (pp > 0) AppColors.red else AppColors.textSecondary,
            style = Tnum
        )
    }
}

@Composable
private fun LadderCard(state: DetailUiState) {
    val r = state.result
    val w = state.balance.coerceAtLeast(1.0)
    val streakF = 0.86.pow(min(state.loseStreak, 8).toDouble())
    val afterKelly = r.fKelly * state.kellyFrac
    val afterStreak = afterKelly * streakF
    val constraint = if (r.bindBy == "凯利计算值") "未触及上限" else r.bindBy

    IOSCard {
        LadderRow("① 理论凯利", fmtPct2(r.fKelly), "${(w * r.fKelly).roundToInt()} 币")
        Spacer(Modifier.height(10.dp))
        LadderRow("② × ${kellyLabel(state.kellyFrac)} Kelly", fmtPct2(afterKelly), "${(w * afterKelly).roundToInt()} 币")
        Spacer(Modifier.height(10.dp))
        LadderRow("③ × 连败 ${String.format(Locale.US, "%.2f", streakF)}", fmtPct2(afterStreak), "${(w * afterStreak).roundToInt()} 币")
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "④ 约束后", fontSize = 15.sp, color = AppColors.text, modifier = Modifier.weight(1f))
            Text(
                text = "—",
                fontSize = 15.sp,
                color = AppColors.textSecondary,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(0.8f)
            )
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(
                    text = when {
                        state.stakeLow <= 0 -> "${r.stake} 币"
                        state.stakeLow == state.stakeHigh -> "${state.stakeLow} 币"
                        else -> "${state.stakeLow}~${state.stakeHigh} 币"
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.text,
                    style = Tnum
                )
                Text(
                    text = constraint,
                    fontSize = 11.sp,
                    color = AppColors.textSecondary
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "每一刀都有原因：防估计误差、防情绪、防单场爆仓",
            fontSize = 13.sp,
            color = AppColors.textSecondary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "已占用敞口 ${state.expUsed.roundToInt()} / ${(state.balance * state.expCap).roundToInt()} 币" +
                "（待定 ${state.pendingCount} 场）",
            fontSize = 11.sp,
            color = AppColors.textTertiary,
            style = Tnum
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "④ 的区间是封口漂移上下各 ±0.5pp 各跑一次得到的",
            fontSize = 11.sp,
            color = AppColors.textTertiary
        )
    }
}

@Composable
private fun LadderRow(name: String, percent: String, money: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = name, fontSize = 15.sp, color = AppColors.text, modifier = Modifier.weight(1f))
        Text(
            text = percent,
            fontSize = 15.sp,
            color = AppColors.textSecondary,
            textAlign = TextAlign.End,
            style = Tnum,
            modifier = Modifier.weight(0.8f)
        )
        Text(
            text = money,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.text,
            textAlign = TextAlign.End,
            style = Tnum,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RiskCard(state: DetailUiState) {
    val risk = riskInfo(state)
    IOSCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "风险等级", fontSize = 15.sp, color = AppColors.text)
            Text(
                text = "${risk.label}（${risk.score}/10）",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = riskColor(risk.score),
                style = Tnum
            )
        }
        Spacer(Modifier.height(10.dp))
        risk.reasons.forEach { reason ->
            Row(Modifier.fillMaxWidth()) {
                Text(text = "· ", fontSize = 13.sp, color = AppColors.textSecondary)
                Text(
                    text = reason,
                    fontSize = 13.sp,
                    color = AppColors.textSecondary,
                    style = Tnum
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun SensitivityCard(state: DetailUiState) {
    val stake = state.result.stake
    IOSCard {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("封口时池比例", fontSize = 12.sp, color = AppColors.textSecondary, modifier = Modifier.weight(1.1f))
            Text(
                "优势",
                fontSize = 12.sp,
                color = AppColors.textSecondary,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
            Text(
                "你的期望",
                fontSize = 12.sp,
                color = AppColors.textSecondary,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(6.dp))

        listOf(-0.06, -0.04, -0.02, 0.0, 0.02, 0.04, 0.06).forEach { delta ->
            val pool = (state.p + delta).coerceIn(0.005, 0.995)
            val isCurrent = delta == 0.0
            val edgePp = (state.q - pool) * 100
            val ev = if (stake > 0 && pool > 0) state.q * (stake / pool) - stake else 0.0

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isCurrent) AppColors.accent.copy(alpha = 0.08f) else Color.Transparent)
                    .padding(horizontal = 6.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = fmtPct(pool) + if (isCurrent) "  当前" else "",
                    fontSize = 13.sp,
                    color = if (isCurrent) AppColors.text else AppColors.textSecondary,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    style = Tnum,
                    modifier = Modifier.weight(1.1f)
                )
                Text(
                    text = signedPp(edgePp),
                    fontSize = 13.sp,
                    color = if (edgePp > 0) AppColors.red else AppColors.textSecondary,
                    textAlign = TextAlign.End,
                    style = Tnum,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${fmtSigned(ev)} 币",
                    fontSize = 13.sp,
                    color = if (ev > 0) AppColors.red else AppColors.textSecondary,
                    textAlign = TextAlign.End,
                    style = Tnum,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "优势撑不住 ±5pp 的变化，这笔就是在赌赔率不变",
            fontSize = 13.sp,
            color = AppColors.textSecondary
        )
    }
}

@Composable
private fun ChartCard(state: DetailUiState) {
    val r = state.result
    IOSCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "下注额 vs 收益",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.text
            )
            Text(
                text = "已按可信度折算",
                fontSize = 13.sp,
                color = AppColors.textSecondary
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendDot(AppColors.accent)
            Text("期望净收益", fontSize = 12.sp, color = AppColors.textSecondary)
            Spacer(Modifier.width(14.dp))
            LegendDot(Color(0xFFFF9500))
            Text("对数增长率", fontSize = 12.sp, color = AppColors.textSecondary)
        }
        Spacer(Modifier.height(8.dp))
        if (r.recommended) {
            MiniChart(
                balance = state.balance,
                pEff = r.pEff,
                qAdj = r.qAdj,
                stake = r.stake,
                stakeLow = state.stakeLow,
                stakeHigh = state.stakeHigh,
                maxFrac = state.maxFrac,
                fKelly = r.fKelly,
                height = 180.dp
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "当前没有正期望，任何一个下注额都会拉低资金曲线",
                    fontSize = 13.sp,
                    color = AppColors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(
        Modifier
            .size(8.dp)
            .background(color, CircleShape)
    )
    Spacer(Modifier.width(5.dp))
}

@Composable
private fun HistoryCard(
    state: DetailUiState,
    onCycle: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpenAll: () -> Unit
) {
    IOSCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "历史记录",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.text
            )
            Text(
                text = "${state.records.size} 场",
                fontSize = 13.sp,
                color = AppColors.textSecondary,
                style = Tnum
            )
        }
        Spacer(Modifier.height(10.dp))

        if (state.records.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "还没有记录", fontSize = 13.sp, color = AppColors.textSecondary)
            }
        } else {
            val preview = state.records.take(RECORD_PREVIEW_COUNT)
            preview.forEachIndexed { index, record ->
                if (index > 0) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(AppColors.separator)
                    )
                }
                SwipeToDeleteRow(onDelete = { onDelete(record.id) }) {
                    RecordRow(record, onCycle)
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(AppColors.separator)
            )

            if (state.records.size > RECORD_PREVIEW_COUNT) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenAll() }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "查看全部 ${state.records.size} 场",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.accent,
                        style = Tnum
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(text = "›", fontSize = 13.sp, color = AppColors.accent)
                }
            } else {
                Text(
                    text = "向左滑动某场可以删除",
                    fontSize = 11.sp,
                    color = AppColors.textTertiary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Text(
                text = "赚多少按记录时的池比例估算，实际以 B 站结算为准",
                fontSize = 11.sp,
                color = AppColors.textTertiary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun RecordRow(
    record: Record,
    onCycle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // 只给大概收益：封盘前赔率还会变，精确金额以 B 站结算为准
    val estWin = if (record.p > 0.0) record.stake / record.p - record.stake else 0.0
    val plText = when (record.result) {
        "win" -> "≈ " + fmtSigned(estWin, 0)
        "lose" -> fmtSigned(-record.stake.toDouble(), 0)
        else -> "—"
    }
    val plColor = when (record.result) {
        "win" -> AppColors.red
        "lose" -> AppColors.green
        else -> AppColors.textTertiary
    }
    val tagText = when (record.result) {
        "win" -> "猜中"
        "lose" -> "猜错"
        else -> "待定"
    }
    val tagColor = when (record.result) {
        "win" -> AppColors.red
        "lose" -> AppColors.green
        else -> AppColors.textSecondary
    }
    val tagBg = when (record.result) {
        "win" -> AppColors.red.copy(alpha = 0.12f)
        "lose" -> AppColors.green.copy(alpha = 0.12f)
        else -> AppColors.trackOff
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = record.match,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.text
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${record.opt} · 投 ${record.stake} · B站 ${(record.p * 100).roundToInt()}% / 盘口 ${(record.q * 100).roundToInt()}%",
                fontSize = 12.sp,
                color = AppColors.textSecondary,
                style = Tnum
            )
        }
        Text(
            text = plText,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = plColor,
            style = Tnum
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(tagBg)
                .clickable { onCycle(record.id) }
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Text(
                text = tagText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = tagColor
            )
        }
    }
}

@Composable
private fun AdvancedCard(state: DetailUiState, vm: DetailViewModel) {
    // 默认折叠：滚动时不会误碰滑块改掉参数
    var expanded by remember { mutableStateOf(false) }

    IOSCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "高级设置",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.text
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (expanded) "收起" else "展开",
                    fontSize = 13.sp,
                    color = AppColors.accent
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (expanded) "⌃" else "⌄",
                    fontSize = 13.sp,
                    color = AppColors.accent
                )
            }
        }

        if (!expanded) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "可信度 ${(state.conf * 100).roundToInt()}% · 单场 ${fmtPct(state.maxFrac)} · " +
                    "敞口 ${(state.expCap * 100).roundToInt()}% · 漂移 ${signedPp(state.drift * 100)} · " +
                    "${kellyLabel(state.kellyFrac)} Kelly",
                fontSize = 12.sp,
                color = AppColors.textSecondary,
                style = Tnum
            )
        } else {
            Spacer(Modifier.height(14.dp))
            SettingSlider(
                name = "对盘口可信度",
            valueText = String.format(Locale.US, "%.0f%%", state.conf * 100),
            value = state.conf.toFloat(),
            onChange = { vm.setConf(it.toDouble()) }
        )
        Spacer(Modifier.height(16.dp))
        SettingSlider(
            name = "单场最多投",
            valueText = fmtPct(state.maxFrac),
            value = ((state.maxFrac - 0.005) / 0.495).toFloat(),
            onChange = { vm.setMaxFrac(0.005 + it * 0.495) }
        )
        Spacer(Modifier.height(16.dp))
        SettingSlider(
            name = "总敞口上限",
            valueText = String.format(Locale.US, "%.0f%%", state.expCap * 100),
            value = ((state.expCap - 0.01) / 0.99).toFloat(),
            onChange = { vm.setExpCap(0.01 + it * 0.99) }
        )
        Spacer(Modifier.height(16.dp))
        SettingSlider(
            name = "封口前比例漂移",
            valueText = signedPp(state.drift * 100),
            value = ((state.drift + 0.2) / 0.4).toFloat(),
            onChange = { vm.setDrift(-0.2 + it * 0.4) }
        )
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "凯利分数", fontSize = 15.sp, color = AppColors.text)
            Text(
                text = kellyLabel(state.kellyFrac),
                fontSize = 15.sp,
                color = AppColors.textSecondary,
                style = Tnum
            )
        }
        Spacer(Modifier.height(8.dp))
        val options = listOf("¼", "½", "全")
        val selected = when {
            state.kellyFrac <= 0.3 -> 0
            state.kellyFrac <= 0.6 -> 1
            else -> 2
        }
        IOSSegmented(options = options, selected = selected, onSelect = { index ->
            vm.setKellyFrac(if (index == 0) 0.25 else if (index == 1) 0.5 else 1.0)
        })
        }
    }
}

@Composable
private fun SettingSlider(
    name: String,
    valueText: String,
    value: Float,
    onChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = name, fontSize = 15.sp, color = AppColors.text)
        Text(text = valueText, fontSize = 15.sp, color = AppColors.textSecondary, style = Tnum)
    }
    Spacer(Modifier.height(6.dp))
    IOSSlider(value = value.coerceIn(0f, 1f), onValueChange = onChange)
}
