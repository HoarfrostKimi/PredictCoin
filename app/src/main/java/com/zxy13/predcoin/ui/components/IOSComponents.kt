package com.zxy13.predcoin.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zxy13.predcoin.ui.AppColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 等宽数字：所有会变化的数值都用它，避免宽度跳动 */
val Tnum = TextStyle(fontFeatureSettings = "tnum")

/** iOS 风格卡片：无边框、无阴影，靠背景色差区分层次 */
@Composable
fun IOSCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.card)
            .padding(contentPadding),
        content = content
    )
}

/**
 * iOS 风格分段控件。
 * 白色底片只有一块，切换时整体横向滑动——不要两个选项各自淡入淡出，
 * 否则过渡中间会在灰底上叠出一块发亮的区域。
 */
@Composable
fun IOSSegmented(
    options: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    height: Dp = 30.dp,
    fontSize: TextUnit = 13.sp,
    onSelect: (Int) -> Unit
) {
    val count = options.size.coerceAtLeast(1)
    val shape = RoundedCornerShape(7.dp)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(AppColors.segBg)
            .padding(2.dp)
    ) {
        val itemWidth = maxWidth / count
        val offsetX by animateDpAsState(
            targetValue = itemWidth * selected.coerceIn(0, count - 1),
            animationSpec = tween(durationMillis = 200),
            label = "segPill"
        )

        // 唯一的白色底片
        Box(
            modifier = Modifier
                .offset(x = offsetX)
                .width(itemWidth)
                .height(height)
                .shadow(1.dp, shape)
                .background(AppColors.card, shape)
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                val isSelected = index == selected
                val fg by animateColorAsState(
                    targetValue = if (isSelected) AppColors.text else AppColors.textSecondary,
                    label = "segFg"
                )
                val interaction = remember { MutableInteractionSource() }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(height)
                        .selectable(
                            selected = isSelected,
                            interactionSource = interaction,
                            indication = null,
                            onClick = { onSelect(index) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = fg,
                        fontSize = fontSize,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * iOS 风格滑块，内部值域固定 0f..1f。
 * 使用 Material3 Slider 的手势/无障碍能力，但 thumb 与 track 全部自己绘制。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IOSSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = { onValueChange(it.coerceIn(0f, 1f)) },
            modifier = Modifier.fillMaxSize(),
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent
            ),
            thumb = {
                Box(
                    Modifier
                        .size(22.dp)
                        .shadow(3.dp, CircleShape)
                        .background(Color.White, CircleShape)
                        .border(0.5.dp, Color(0x1A000000), CircleShape)
                )
            },
            track = { sliderState ->
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                ) {
                    val radiusPx = 11.dp.toPx()
                    val usable = (size.width - 2 * radiusPx).coerceAtLeast(0f)
                    val corner = CornerRadius(size.height / 2f)

                    drawRoundRect(
                        color = AppColors.trackOff,
                        topLeft = Offset.Zero,
                        size = Size(size.width, size.height),
                        cornerRadius = corner
                    )

                    val fraction = sliderState.value.coerceIn(0f, 1f)
                    val filled = (radiusPx + usable * fraction)
                        .coerceIn(size.height, size.width)
                    drawRoundRect(
                        color = AppColors.accent,
                        topLeft = Offset.Zero,
                        size = Size(filled, size.height),
                        cornerRadius = corner
                    )
                }
            }
        )
    }
}

/**
 * 左滑露出「删除」的列表行（iOS 风格）：滑动只做横向手势，纵向滚动不受影响。
 */
@Composable
fun SwipeToDeleteRow(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val revealWidth = 84.dp
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
    ) {
        // 底层：删除按钮
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(AppColors.red.copy(alpha = 0.12f))
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(revealWidth)
                    .background(AppColors.red)
                    .clickable {
                        scope.launch { offset.snapTo(0f) }
                        onDelete()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "删除",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }

        // 上层：内容，左滑后露出底层
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .background(AppColors.card)
                .pointerInput(Unit) {
                    val revealPx = revealWidth.toPx()
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val target = if (offset.value < -revealPx * 0.45f) -revealPx else 0f
                                offset.animateTo(target)
                            }
                        },
                        onDragCancel = {
                            scope.launch { offset.animateTo(0f) }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val next = (offset.value + dragAmount).coerceIn(-revealPx, 0f)
                            scope.launch { offset.snapTo(next) }
                        }
                    )
                }
        ) {
            content()
        }
    }
}

/** iOS 风格按钮 */
@Composable
fun IOSButton(
    text: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .graphicsLayer { alpha = if (pressed) 0.6f else 1f }
            .clip(shape)
            .background(if (primary) AppColors.accent else AppColors.card)
            .then(
                if (primary) Modifier
                else Modifier.border(1.dp, AppColors.textTertiary, shape)
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (primary) Color.White else AppColors.text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
