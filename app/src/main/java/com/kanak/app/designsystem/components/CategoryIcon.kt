package com.kanak.app.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kanak.app.model.ExpenseCategory

@Composable
fun CategoryIcon(
    category: ExpenseCategory,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val strokeWidth = 2.dp.toPx()
        val w = this.size.width
        val h = this.size.height

        when (category) {
            ExpenseCategory.GIRLFRIEND -> {
                val heartPath = Path().apply {
                    moveTo(w * 0.5f, h * 0.82f)
                    cubicTo(w * 0.15f, h * 0.55f, w * 0.1f, h * 0.25f, w * 0.32f, h * 0.2f)
                    cubicTo(w * 0.42f, h * 0.18f, w * 0.48f, h * 0.3f, w * 0.5f, h * 0.35f)
                    cubicTo(w * 0.52f, h * 0.3f, w * 0.58f, h * 0.18f, w * 0.68f, h * 0.2f)
                    cubicTo(w * 0.9f, h * 0.25f, w * 0.85f, h * 0.55f, w * 0.5f, h * 0.82f)
                    close()
                }
                drawPath(heartPath, tint, style = Stroke(strokeWidth))
            }
            ExpenseCategory.FOOD -> {
                drawLine(tint, Offset(w * 0.35f, h * 0.2f), Offset(w * 0.35f, h * 0.8f), strokeWidth = strokeWidth)
                drawLine(tint, Offset(w * 0.25f, h * 0.2f), Offset(w * 0.35f, h * 0.45f), strokeWidth = strokeWidth)
                drawLine(tint, Offset(w * 0.45f, h * 0.2f), Offset(w * 0.35f, h * 0.45f), strokeWidth = strokeWidth)
                drawLine(tint, Offset(w * 0.65f, h * 0.2f), Offset(w * 0.65f, h * 0.8f), strokeWidth = strokeWidth)
                drawLine(tint, Offset(w * 0.65f, h * 0.2f), Offset(w * 0.75f, h * 0.45f), strokeWidth = strokeWidth)
            }
            ExpenseCategory.GROCERIES -> {
                val path = Path().apply {
                    moveTo(w * 0.15f, h * 0.4f)
                    lineTo(w * 0.85f, h * 0.4f)
                    lineTo(w * 0.75f, h * 0.85f)
                    lineTo(w * 0.25f, h * 0.85f)
                    close()
                }
                drawPath(path, tint, style = Stroke(strokeWidth))
                val handle = Path().apply {
                    moveTo(w * 0.3f, h * 0.4f)
                    cubicTo(w * 0.3f, h * 0.15f, w * 0.7f, h * 0.15f, w * 0.7f, h * 0.4f)
                }
                drawPath(handle, tint, style = Stroke(strokeWidth))
            }
            ExpenseCategory.TRANSPORT -> {
                val path = Path().apply {
                    moveTo(w * 0.1f, h * 0.65f)
                    lineTo(w * 0.25f, h * 0.45f)
                    lineTo(w * 0.75f, h * 0.45f)
                    lineTo(w * 0.9f, h * 0.65f)
                    lineTo(w * 0.9f, h * 0.8f)
                    lineTo(w * 0.1f, h * 0.8f)
                    close()
                }
                drawPath(path, tint, style = Stroke(strokeWidth))
                drawCircle(tint, radius = w * 0.08f, center = Offset(w * 0.3f, h * 0.8f))
                drawCircle(tint, radius = w * 0.08f, center = Offset(w * 0.7f, h * 0.8f))
            }
            ExpenseCategory.SHOPPING -> {
                drawRect(tint, topLeft = Offset(w * 0.2f, h * 0.35f), size = Size(w * 0.6f, h * 0.5f), style = Stroke(strokeWidth))
                val handle = Path().apply {
                    moveTo(w * 0.35f, h * 0.35f)
                    cubicTo(w * 0.35f, h * 0.15f, w * 0.65f, h * 0.15f, w * 0.65f, h * 0.35f)
                }
                drawPath(handle, tint, style = Stroke(strokeWidth))
            }
            ExpenseCategory.BILL -> {
                drawRect(tint, topLeft = Offset(w * 0.25f, h * 0.15f), size = Size(w * 0.5f, h * 0.7f), style = Stroke(strokeWidth))
                drawLine(tint, Offset(w * 0.35f, h * 0.35f), Offset(w * 0.65f, h * 0.35f), strokeWidth = strokeWidth)
                drawLine(tint, Offset(w * 0.35f, h * 0.5f), Offset(w * 0.65f, h * 0.5f), strokeWidth = strokeWidth)
                drawLine(tint, Offset(w * 0.35f, h * 0.65f), Offset(w * 0.5f, h * 0.65f), strokeWidth = strokeWidth)
            }
            ExpenseCategory.HEALTH -> {
                val path = Path().apply {
                    moveTo(w * 0.4f, h * 0.15f)
                    lineTo(w * 0.6f, h * 0.15f)
                    lineTo(w * 0.6f, h * 0.4f)
                    lineTo(w * 0.85f, h * 0.4f)
                    lineTo(w * 0.85f, h * 0.6f)
                    lineTo(w * 0.6f, h * 0.6f)
                    lineTo(w * 0.6f, h * 0.85f)
                    lineTo(w * 0.4f, h * 0.85f)
                    lineTo(w * 0.4f, h * 0.6f)
                    lineTo(w * 0.15f, h * 0.6f)
                    lineTo(w * 0.15f, h * 0.4f)
                    lineTo(w * 0.4f, h * 0.4f)
                    close()
                }
                drawPath(path, tint, style = Stroke(strokeWidth))
            }
            ExpenseCategory.ENTERTAINMENT -> {
                val path = Path().apply {
                    moveTo(w * 0.2f, h * 0.25f)
                    lineTo(w * 0.8f, h * 0.25f)
                    lineTo(w * 0.8f, h * 0.75f)
                    lineTo(w * 0.2f, h * 0.75f)
                    close()
                }
                drawPath(path, tint, style = Stroke(strokeWidth))
                drawCircle(tint, radius = w * 0.08f, center = Offset(w * 0.5f, h * 0.5f))
            }
            ExpenseCategory.EDUCATION -> {
                val cap = Path().apply {
                    moveTo(w * 0.5f, h * 0.2f)
                    lineTo(w * 0.85f, h * 0.4f)
                    lineTo(w * 0.5f, h * 0.6f)
                    lineTo(w * 0.15f, h * 0.4f)
                    close()
                }
                drawPath(cap, tint, style = Stroke(strokeWidth))
                drawLine(tint, Offset(w * 0.3f, h * 0.5f), Offset(w * 0.3f, h * 0.75f), strokeWidth = strokeWidth)
                drawLine(tint, Offset(w * 0.7f, h * 0.5f), Offset(w * 0.7f, h * 0.75f), strokeWidth = strokeWidth)
            }
            ExpenseCategory.OTHER -> {
                drawCircle(tint, radius = w * 0.07f, center = Offset(w * 0.25f, h * 0.5f))
                drawCircle(tint, radius = w * 0.07f, center = Offset(w * 0.5f, h * 0.5f))
                drawCircle(tint, radius = w * 0.07f, center = Offset(w * 0.75f, h * 0.5f))
            }
        }
    }
}
