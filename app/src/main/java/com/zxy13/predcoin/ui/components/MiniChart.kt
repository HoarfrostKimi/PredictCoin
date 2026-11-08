package com.zxy13.predcoin.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zxy13.predcoin.ui.AppColors
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 收益曲线：横轴下注额，纵轴左侧期望净收益（accent）、右侧对数增长率（橙）。
 * 两条曲线各自独立归一化到画布高度；概率一律用收缩后的 qAdj，否则峰顶会对不上凯利金额。
 */
@Composable
fun MiniChart(
    balance: Double,
    pEff: Double,
    qAdj: Double,
    stake: Int,
    maxFrac: Double,
    fKelly: Double,
    modifier: Modifier = Modifier,
    stakeLow: Int = stake,
    stakeHigh: Int = stake,
    height: Dp = 180.dp
) {
    val w = max(balance, 1.0)
    val xMax = max(w * maxFrac * 1.6, w * fKelly * 1.85)
        .coerceIn(w * 0.01, w * 0.95)

    val samples = 60

    // 曲线 1：期望净收益
    val evValues = List(samples + 1) { i ->
        val x = xMax * i / samples
        qAdj * (x / pEff) - x
    }

    // 曲线 2：对数增长率
    val growthValues = List(samples + 1) { i ->
        val x = xMax * i / samples
        val surviveWin = w - x + x / pEff
        val surviveLose = w - x
        if (surviveWin <= 0.0 || surviveLose <= 0.0) {
            Double.NEGATIVE_INFINITY
        } else {
            qAdj * ln(surviveWin) + (1 - qAdj) * ln(surviveLose) - ln(w)
        }
    }

    val orange = Color(0xFFFF9500)

    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val padTop = 12.dp.toPx()
        val padBottom = 26.dp.toPx()
        val chartHeight = (size.height - padTop - padBottom).coerceAtLeast(1f)

        fun xAt(i: Int): Float = size.width * i / samples

        fun normalize(values: List<Double>): List<Float> {
            val finite = values.filter { it.isFinite() }
            if (finite.isEmpty()) return values.map { 0.5f }
            val mn = finite.min()
            val mx = finite.max()
            val range = (mx - mn).let { if (it <= 1e-12) 1.0 else it }
            return values.map { v -> if (!v.isFinite()) 0f else ((v - mn) / range).toFloat() }
        }

        fun yAt(normalized: Float): Float = padTop + (1f - normalized) * chartHeight

        val evNorm = normalize(evValues)
        val growthNorm = normalize(growthValues)

        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 10.5.sp.toPx()
            color = AppColors.textSecondary.toArgb()
        }

        // 建议区间色带
        if (stakeHigh > stakeLow && stakeLow > 0) {
            val bandStart = (size.width * (stakeLow / xMax)).toFloat().coerceIn(0f, size.width)
            val bandEnd = (size.width * (stakeHigh / xMax)).toFloat().coerceIn(bandStart, size.width)
            drawRect(
                color = AppColors.accent.copy(alpha = 0.10f),
                topLeft = Offset(bandStart, padTop),
                size = Size(bandEnd - bandStart, chartHeight)
            )
        }

        // 竖直虚线：推荐下注额（区间中点）
        if (stake > 0) {
            val lineX = (size.width * (stake / xMax)).toFloat().coerceIn(0f, size.width)
            drawLine(
                color = AppColors.accent.copy(alpha = 0.45f),
                start = Offset(lineX, padTop),
                end = Offset(lineX, padTop + chartHeight),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
            )
        }

        fun buildPath(norm: List<Float>): Path {
            val path = Path()
            norm.forEachIndexed { i, v ->
                val x = xAt(i)
                val y = yAt(v)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            return path
        }

        drawPath(
            path = buildPath(evNorm),
            color = AppColors.accent,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            path = buildPath(growthNorm),
            color = orange,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )

        // 对数增长率的峰值（峰在 0 处说明没有正期望，不标点）
        var peakIndex = 0
        growthValues.forEachIndexed { i, v ->
            if (v > growthValues[peakIndex]) peakIndex = i
        }
        if (peakIndex > 0) {
            val peakX = xAt(peakIndex)
            val peakY = yAt(growthNorm[peakIndex])

            drawCircle(color = orange, radius = 3.5.dp.toPx(), center = Offset(peakX, peakY))
            drawCircle(color = Color.White, radius = 1.6.dp.toPx(), center = Offset(peakX, peakY))

            val bestStake = (xMax * peakIndex / samples).roundToInt()
            val label = "最优 $bestStake 币"
            val labelWidth = paint.measureText(label)
            val labelX = (peakX - labelWidth / 2f).coerceIn(0f, (size.width - labelWidth).coerceAtLeast(0f))
            val labelY = (peakY + 6.dp.toPx() + paint.textSize)
                .coerceAtMost(size.height - 3.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText(label, labelX, labelY, paint)
        }

        // 横轴两端标注
        val baselineY = size.height - 4.dp.toPx()
        drawContext.canvas.nativeCanvas.drawText("0", 0f, baselineY, paint)
        val endLabel = "${xMax.roundToInt()} 币"
        drawContext.canvas.nativeCanvas.drawText(
            endLabel,
            size.width - paint.measureText(endLabel),
            baselineY,
            paint
        )
    }
}
