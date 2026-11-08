package com.kanak.app.ui.navigation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kanak.app.designsystem.theme.KanakCornerRadius
import com.kanak.app.designsystem.theme.KanakSpacing

@Composable
fun KanakBottomNavigation(
    activeTab: KanakTab,
    onTabSelected: (KanakTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(72.dp)
                .padding(horizontal = KanakSpacing.small, vertical = KanakSpacing.extraSmall),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            KanakTab.entries.forEach { tab ->
                val selected = tab == activeTab
                val iconColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                val textColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                val indicatorBg = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                val interactionSource = remember { MutableInteractionSource() }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onTabSelected(tab) }
                        )
                        .semantics {
                            role = Role.Tab
                            this.selected = selected
                        }
                        .padding(vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(KanakCornerRadius.medium))
                            .background(indicatorBg)
                            .padding(horizontal = 18.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        KanakNavIcon(
                            tab = tab,
                            tint = iconColor,
                            size = 24.dp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = tab.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = textColor,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun KanakNavIcon(
    tab: KanakTab,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val strokeWidth = 2.dp.toPx()

        when (tab) {
            KanakTab.HOME -> {
                val housePath = Path().apply {
                    moveTo(w * 0.12f, h * 0.46f)
                    lineTo(w * 0.5f, h * 0.14f)
                    lineTo(w * 0.88f, h * 0.46f)
                    lineTo(w * 0.88f, h * 0.86f)
                    lineTo(w * 0.12f, h * 0.86f)
                    close()
                }
                drawPath(
                    path = housePath,
                    color = tint,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                val doorPath = Path().apply {
                    moveTo(w * 0.38f, h * 0.86f)
                    lineTo(w * 0.38f, h * 0.58f)
                    lineTo(w * 0.62f, h * 0.58f)
                    lineTo(w * 0.62f, h * 0.86f)
                }
                drawPath(
                    path = doorPath,
                    color = tint,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
            KanakTab.TRANSACTIONS -> {
                val receiptPath = Path().apply {
                    moveTo(w * 0.22f, h * 0.16f)
                    lineTo(w * 0.36f, h * 0.28f)
                    lineTo(w * 0.5f, h * 0.16f)
                    lineTo(w * 0.64f, h * 0.28f)
                    lineTo(w * 0.78f, h * 0.16f)
                    lineTo(w * 0.78f, h * 0.86f)
                    lineTo(w * 0.22f, h * 0.86f)
                    close()
                }
                drawPath(
                    path = receiptPath,
                    color = tint,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                drawLine(
                    color = tint,
                    start = Offset(w * 0.35f, h * 0.46f),
                    end = Offset(w * 0.65f, h * 0.46f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )

                drawLine(
                    color = tint,
                    start = Offset(w * 0.35f, h * 0.64f),
                    end = Offset(w * 0.55f, h * 0.64f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
            KanakTab.ANALYTICS -> {
                drawLine(
                    color = tint,
                    start = Offset(w * 0.25f, h * 0.84f),
                    end = Offset(w * 0.25f, h * 0.6f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )

                drawLine(
                    color = tint,
                    start = Offset(w * 0.5f, h * 0.84f),
                    end = Offset(w * 0.5f, h * 0.38f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )

                drawLine(
                    color = tint,
                    start = Offset(w * 0.75f, h * 0.84f),
                    end = Offset(w * 0.75f, h * 0.16f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
            KanakTab.SETTINGS -> {
                val y1 = h * 0.35f
                val y2 = h * 0.65f
                val circleRadius = w * 0.11f

                drawLine(
                    color = tint,
                    start = Offset(w * 0.15f, y1),
                    end = Offset((w * 0.65f) - circleRadius, y1),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset((w * 0.65f) + circleRadius, y1),
                    end = Offset(w * 0.85f, y1),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = tint,
                    radius = circleRadius,
                    center = Offset(w * 0.65f, y1),
                    style = Stroke(width = strokeWidth)
                )

                drawLine(
                    color = tint,
                    start = Offset(w * 0.15f, y2),
                    end = Offset((w * 0.35f) - circleRadius, y2),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = tint,
                    start = Offset((w * 0.35f) + circleRadius, y2),
                    end = Offset(w * 0.85f, y2),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = tint,
                    radius = circleRadius,
                    center = Offset(w * 0.35f, y2),
                    style = Stroke(width = strokeWidth)
                )
            }
        }
    }
}
