package com.keepaccounts.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.keepaccounts.ui.KaTab

/**
 * 底部导航图标，用 Canvas 手绘。
 *
 * 为什么不用 `material-icons-extended`：
 * 1. 那个依赖体积巨大（全量图标十几 MB），只为 5 个图标引它不值；
 * 2. 它已经停止跟随 Compose 版本更新，容易在 `Could not resolve` 上卡住构建。
 *
 * 这里全部用归一化坐标（0~1）绘制，缩放由 Canvas 自己处理，不依赖任何图标库。
 */
@Composable
fun TabIcon(tab: KaTab, tint: Color, modifier: Modifier = Modifier.size(21.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sw = w * 0.088f
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

        fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
            drawLine(
                color = tint,
                start = Offset(x1 * w, y1 * h),
                end = Offset(x2 * w, y2 * h),
                strokeWidth = sw,
                cap = StrokeCap.Round
            )
        }

        when (tab) {
            KaTab.Ledger -> {
                line(0.16f, 0.29f, 0.84f, 0.29f)
                line(0.16f, 0.50f, 0.84f, 0.50f)
                line(0.16f, 0.71f, 0.60f, 0.71f)
            }

            KaTab.Assets -> {
                // 钱包：矩形轮廓 + 右侧卡扣
                line(0.13f, 0.26f, 0.87f, 0.26f)
                line(0.87f, 0.26f, 0.87f, 0.78f)
                line(0.87f, 0.78f, 0.13f, 0.78f)
                line(0.13f, 0.78f, 0.13f, 0.26f)
                line(0.62f, 0.52f, 0.75f, 0.52f)
            }

            KaTab.Pending -> {
                // 铃铛：钟体 + 底沿 + 钟舌
                val bell = Path().apply {
                    moveTo(0.29f * w, 0.68f * h)
                    lineTo(0.29f * w, 0.44f * h)
                    quadraticBezierTo(0.29f * w, 0.26f * h, 0.50f * w, 0.26f * h)
                    quadraticBezierTo(0.71f * w, 0.26f * h, 0.71f * w, 0.44f * h)
                    lineTo(0.71f * w, 0.68f * h)
                }
                drawPath(bell, tint, style = stroke)
                line(0.20f, 0.72f, 0.80f, 0.72f)

                val clapper = Path().apply {
                    moveTo(0.43f * w, 0.82f * h)
                    quadraticBezierTo(0.50f * w, 0.92f * h, 0.57f * w, 0.82f * h)
                }
                drawPath(clapper, tint, style = stroke)
            }

            KaTab.Stats -> {
                line(0.25f, 0.79f, 0.25f, 0.46f)
                line(0.50f, 0.79f, 0.50f, 0.21f)
                line(0.75f, 0.79f, 0.75f, 0.58f)
            }

            KaTab.Settings -> {
                // 两道滑块 + 两个旋钮
                line(0.13f, 0.34f, 0.38f, 0.34f)
                line(0.56f, 0.34f, 0.87f, 0.34f)
                drawCircle(
                    color = tint,
                    radius = 0.077f * w,
                    center = Offset(0.47f * w, 0.34f * h),
                    style = Stroke(width = sw)
                )
                line(0.13f, 0.66f, 0.62f, 0.66f)
                line(0.80f, 0.66f, 0.87f, 0.66f)
                drawCircle(
                    color = tint,
                    radius = 0.077f * w,
                    center = Offset(0.71f * w, 0.66f * h),
                    style = Stroke(width = sw)
                )
            }
        }
    }
}
