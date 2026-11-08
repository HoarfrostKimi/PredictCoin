package com.zxy13.predcoin

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zxy13.predcoin.data.Store
import com.zxy13.predcoin.ui.AppColors
import com.zxy13.predcoin.ui.components.IOSButton
import com.zxy13.predcoin.ui.components.IOSCard
import com.zxy13.predcoin.ui.components.IOSSegmented
import com.zxy13.predcoin.ui.components.IOSSlider
import com.zxy13.predcoin.ui.components.Tnum
import com.zxy13.predcoin.ui.theme.PredictCoinTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private val vm: CalcViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            PredictCoinTheme {
                CalcScreen(vm, onOpenDetail = { openDetail() })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 详情页可能改过余额/参数，回来时重新从 Store 读取
        vm.reload()
    }

    @Suppress("DEPRECATION")
    private fun openDetail() {
        startActivity(Intent(this, DetailActivity::class.java))
        overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
    }
}

private enum class InputTarget { BALANCE, P, Q }

/* ───────────────────────── 格式化工具 ───────────────────────── */

private fun fmtPct(v: Double): String = String.format(Locale.US, "%.1f%%", v * 100)

private fun fmtPp(v: Double): String = String.format(Locale.US, "%.1f", v)

private fun fmtMoney(v: Double): String = String.format(Locale.US, "%,d", v.roundToInt())

private fun fmtSigned(v: Double, digits: Int = 1): String {
    val sign = if (v >= 0) "+" else "-"
    return sign + String.format(Locale.US, "%.${digits}f", abs(v))
}

/** 上下界相同时只显示一个值，避免出现「55 ~ 55」 */
private fun rangeText(low: String, high: String): String = if (low == high) low else "$low ~ $high"

private fun riskLabel(fracOfBalance: Double): String = when {
    fracOfBalance < 0.01 -> "风险很低"
    fracOfBalance < 0.05 -> "风险较低"
    fracOfBalance < 0.08 -> "风险中等"
    fracOfBalance < 0.15 -> "风险偏高"
    else -> "风险很高"
}

/* ───────────────────────── 主界面 ───────────────────────── */

@Composable
private fun CalcScreen(vm: CalcViewModel, onOpenDetail: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var dialog by remember { mutableStateOf<InputTarget?>(null) }
    var recordDone by remember { mutableStateOf(false) }

    val nameA = state.optA.ifBlank { "这一边" }
    val side = when {
        state.solo && state.soloOpposite -> "$nameA 不夺冠"
        state.solo -> "$nameA 夺冠"
        else -> nameA
    }
    val pTitle = if (state.solo) "B 站显示 $side" else "B 站显示 $nameA 胜率"
    val qTitle = if (state.solo) "盘口给 ${side}的胜率" else "盘口给 $nameA 的胜率"

    Surface(modifier = Modifier.fillMaxSize(), color = AppColors.bg) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            /* 内容区：常规屏幕一屏放完，极小屏幕仍可滚动兜底 */
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 10.dp)
            ) {
            /* 标题行 */
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "预测币",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.text,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = fmtMoney(state.balance),
                    fontSize = 16.sp,
                    color = AppColors.textSecondary,
                    style = Tnum,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { dialog = InputTarget.BALANCE }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            /* 分段控件 */
            IOSSegmented(
                options = listOf("两队对战", "冠军"),
                selected = if (state.solo) 1 else 0,
                onSelect = { vm.setSolo(it == 1) }
            )

            Spacer(Modifier.height(8.dp))

            /* 队伍卡片 */
            TeamCard(state, vm)

            Spacer(Modifier.height(8.dp))

            /* 概率卡片：B 站池比例 + 盘口胜率 */
            val pp = (state.q - state.p) * 100
            IOSCard(contentPadding = 12.dp) {
                ProbHeader(pTitle, fmtPct(state.p)) { dialog = InputTarget.P }
                Spacer(Modifier.height(2.dp))
                IOSSlider(
                    value = state.p.toFloat(),
                    onValueChange = { vm.setP(it.toDouble()) }
                )
                Spacer(Modifier.height(4.dp))
                ProbHeader(qTitle, fmtPct(state.q)) { dialog = InputTarget.Q }
                Spacer(Modifier.height(2.dp))
                IOSSlider(
                    value = state.q.toFloat(),
                    onValueChange = { vm.setQ(it.toDouble()) }
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val hasEdge = pp > 0.05
                    Text(
                        text = when {
                            hasEdge -> "盘口比 B 站高 ${fmtPp(pp)}pp · B 站低估了 $side"
                            pp < -0.05 -> "盘口比 B 站低 ${fmtPp(-pp)}pp · B 站高估了 $side"
                            else -> "两边基本一致 · 没有可下注的价差"
                        },
                        fontSize = 12.sp,
                        color = if (hasEdge) AppColors.red else AppColors.textSecondary,
                        style = Tnum,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (pp < -0.05) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "押对面 →",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.accent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    if (state.solo) {
                                        vm.setSoloOpposite(!state.soloOpposite)
                                    } else {
                                        vm.swapSides()
                                    }
                                }
                                .padding(horizontal = 2.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            /* 结果卡片 */
            ResultCard(state)

            Spacer(Modifier.height(6.dp))
            }

            /* 按钮行：固定在底部 */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 10.dp)
            ) {
                IOSButton(
                    text = "查看详情",
                    primary = false,
                    modifier = Modifier.weight(1f)
                ) {
                    onOpenDetail()
                }
                Spacer(Modifier.width(12.dp))
                IOSButton(
                    text = if (recordDone) "已记录 ✓" else "记录本场",
                    primary = true,
                    modifier = Modifier.weight(1f)
                ) {
                    if (recordDone) return@IOSButton
                    vm.addRecord()
                    Toast.makeText(context, "已记录本场", Toast.LENGTH_SHORT).show()
                    recordDone = true
                    scope.launch {
                        delay(1500)
                        recordDone = false
                    }
                }
            }

        }
    }

    /* 输入对话框 */
    when (dialog) {
        InputTarget.BALANCE -> IOSInputDialog(
            title = "修改余额",
            initial = state.balance.roundToInt().toString(),
            hint = "当前可用预测币总数",
            decimal = false,
            onDismiss = { dialog = null },
            onConfirm = { text ->
                text.toDoubleOrNull()?.let { vm.setBalance(it) }
                dialog = null
            }
        )

        InputTarget.P -> IOSInputDialog(
            title = "B 站显示的胜率",
            initial = String.format(Locale.US, "%.1f", state.p * 100),
            hint = "输入 0 ~ 100 之间的数字，例如 45.3",
            decimal = true,
            onDismiss = { dialog = null },
            onConfirm = { text ->
                text.toDoubleOrNull()?.let { vm.setP(it / 100.0) }
                dialog = null
            }
        )

        InputTarget.Q -> IOSInputDialog(
            title = "盘口给出的胜率",
            initial = String.format(Locale.US, "%.1f", state.q * 100),
            hint = "输入 0 ~ 100 之间的数字，例如 52",
            decimal = true,
            onDismiss = { dialog = null },
            onConfirm = { text ->
                text.toDoubleOrNull()?.let { vm.setQ(it / 100.0) }
                dialog = null
            }
        )

        null -> Unit
    }
}

/* ───────────────────────── 队伍卡片 ───────────────────────── */

@Composable
private fun TeamCard(state: UiState, vm: CalcViewModel) {
    var nameA by remember { mutableStateOf(state.optA) }
    var nameB by remember { mutableStateOf(state.optB) }

    LaunchedEffect(state.optA) { if (nameA != state.optA) nameA = state.optA }
    LaunchedEffect(state.optB) { if (nameB != state.optB) nameB = state.optB }

    IOSCard(contentPadding = 12.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TeamField(
                value = nameA,
                modifier = Modifier.weight(1f),
                onValueChange = { raw ->
                    val v = raw.uppercase(Locale.ROOT)
                    nameA = v
                    vm.setOptA(v)
                }
            )
            if (state.solo) {
                Spacer(Modifier.width(10.dp))
                IOSSegmented(
                    options = listOf("夺冠", "不夺冠"),
                    selected = if (state.soloOpposite) 1 else 0,
                    modifier = Modifier.width(108.dp),
                    height = 28.dp,
                    fontSize = 11.sp
                ) { vm.setSoloOpposite(it == 1) }
            } else {
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(AppColors.trackOff)
                        .clickable { vm.swapSides() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⇄",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.text
                    )
                }
                Spacer(Modifier.width(10.dp))
                TeamField(
                    value = nameB,
                    modifier = Modifier.weight(1f),
                    onValueChange = { raw ->
                        val v = raw.uppercase(Locale.ROOT)
                        nameB = v
                        vm.setOptB(v)
                    }
                )
            }
        }
    }
}

@Composable
private fun ProbHeader(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 14.sp, color = AppColors.textSecondary)
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.text,
            style = Tnum,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { onClick() }
                .padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun TeamField(
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            fontSize = 17.sp,
            color = AppColors.text,
            textAlign = TextAlign.Center
        ),
        cursorBrush = SolidColor(AppColors.accent),
        modifier = modifier,
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = "队名",
                        fontSize = 17.sp,
                        color = AppColors.textTertiary
                    )
                }
                innerTextField()
            }
        }
    )
}

/* ───────────────────────── 结果卡片 ───────────────────────── */

@Composable
private fun ResultCard(state: UiState) {
    val r = state.result
    val balance = state.balance.coerceAtLeast(1.0)
    val capStake = balance * state.maxFrac
    val low = state.stakeLow.coerceAtLeast(1)
    val high = state.stakeHigh.coerceAtLeast(low)
    val fracLow = low / balance
    val fracHigh = high / balance
    val netLow = low / r.pEff - low
    val netHigh = high / r.pEff - high
    val evLow = state.q * (low / r.pEff) - low
    val evHigh = state.q * (high / r.pEff) - high

    IOSCard(contentPadding = 14.dp) {
        Text(text = "建议投入", fontSize = 12.sp, color = AppColors.textSecondary)
        Spacer(Modifier.height(2.dp))

        if (r.recommended) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (low == high) "$low" else "$low ~ $high",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.text,
                    letterSpacing = (-1).sp,
                    style = Tnum
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "币",
                    fontSize = 15.sp,
                    color = AppColors.textSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Text(
                text = if (low == high) {
                    "占资金 ${fmtPct(fracLow)} · 单场上限 ${fmtMoney(capStake)} 币"
                } else {
                    "占资金 ${fmtPct(fracLow)}~${fmtPct(fracHigh)} · 单场上限 ${fmtMoney(capStake)} 币"
                },
                fontSize = 12.sp,
                color = AppColors.textSecondary,
                style = Tnum
            )

            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(AppColors.separator)
            )
            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("猜中净赚", fontSize = 11.sp, color = AppColors.textSecondary)
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = rangeText(fmtSigned(netLow, 0), fmtSigned(netHigh, 0)),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.red,
                        style = Tnum
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text("猜错亏掉", fontSize = 11.sp, color = AppColors.textSecondary)
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = rangeText(fmtSigned(-low.toDouble(), 0), fmtSigned(-high.toDouble(), 0)),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.green,
                        style = Tnum
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = "期望 ${rangeText(fmtSigned(evLow), fmtSigned(evHigh))} 币 · ${riskLabel(fracHigh)}",
                fontSize = 12.sp,
                color = AppColors.textSecondary,
                style = Tnum
            )
        } else {
            Text(
                text = "不下注",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.textSecondary,
                letterSpacing = (-0.5).sp,
                style = Tnum
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = noBetReason(state),
                fontSize = 12.sp,
                color = AppColors.textSecondary,
                style = Tnum
            )
        }
    }
}

private fun noBetReason(state: UiState): String {
    val raw = state.optA.ifBlank { "这一边" }
    val name = when {
        state.solo && state.soloOpposite -> "$raw 不夺冠"
        state.solo -> "$raw 夺冠"
        else -> raw
    }
    val r = state.result
    val expLimit = state.balance * state.expCap
    return when {
        state.q <= state.p ->
            "盘口只给 $name ${fmtPct(state.q)}，B 站却给到 ${fmtPct(state.p)} —— 这边被高估了"

        expLimit - state.expUsed < 1.0 ->
            "总敞口已用满（${fmtMoney(state.expUsed)} / ${fmtMoney(expLimit)} 币），先结算或删掉待定的场次"

        r.edge <= 0.0 ->
            "计入可信度折扣后优势归零 —— 不值得投"

        else ->
            "价差只有 ${fmtPp((state.q - state.p) * 100)}pp，折算后不足半个币 —— 不值得投"
    }
}

/* ───────────────────────── 输入对话框 ───────────────────────── */

@Composable
private fun IOSInputDialog(
    title: String,
    initial: String,
    hint: String,
    decimal: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(AppColors.card)
                .padding(20.dp)
        ) {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.text,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppColors.bg)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { input ->
                        text = input.filter { c ->
                            c.isDigit() || (decimal && c == '.')
                        }
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 20.sp,
                        color = AppColors.text,
                        fontFeatureSettings = "tnum"
                    ),
                    cursorBrush = SolidColor(AppColors.accent),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(text = hint, fontSize = 13.sp, color = AppColors.textSecondary)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                IOSButton(
                    text = "取消",
                    primary = false,
                    modifier = Modifier.weight(1f)
                ) { onDismiss() }
                Spacer(Modifier.width(12.dp))
                IOSButton(
                    text = "确定",
                    primary = true,
                    modifier = Modifier.weight(1f)
                ) { onConfirm(text) }
            }
        }
    }
}
