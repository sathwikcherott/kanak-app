package com.kanak.app.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kanak.app.designsystem.components.CategoryIcon
import com.kanak.app.designsystem.components.KanakCard
import com.kanak.app.designsystem.theme.EmeraldSecondary
import com.kanak.app.designsystem.theme.KanakCornerRadius
import com.kanak.app.designsystem.theme.KanakSpacing
import com.kanak.app.designsystem.theme.StatusError
import com.kanak.app.designsystem.theme.StatusSuccess
import com.kanak.app.model.Expense
import com.kanak.app.ui.analytics.AnalyticsUiState
import com.kanak.app.ui.analytics.DailySpendingItem

@Composable
fun AnalyticsScreen(
    uiState: AnalyticsUiState,
    onPreviousMonthClicked: () -> Unit,
    onNextMonthClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = KanakSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(KanakSpacing.medium)
    ) {
        item {
            Column(
                modifier = Modifier.padding(top = KanakSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Analytics & Insights",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Monthly spending patterns & trends",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            MonthNavigationHeader(
                monthYearName = uiState.selectedMonthYearName.ifBlank { "Current Month" },
                dateRangeText = uiState.dateRangeText,
                canNavigateNext = uiState.canNavigateNext,
                onPreviousClicked = onPreviousMonthClicked,
                onNextClicked = onNextMonthClicked
            )
        }

        item {
            SpendingOverviewGrid(uiState = uiState)
        }

        item {
            DailySpendingChartSection(uiState = uiState)
        }

        item {
            CategoryBreakdownSection(uiState = uiState)
        }

        item {
            MonthlyComparisonSection(uiState = uiState)
        }

        item {
            Spacer(modifier = Modifier.height(KanakSpacing.large))
        }
    }
}

/**
 * Backward compatibility overload for legacy callers.
 */
@Composable
fun AnalyticsScreen(
    uiState: HomeUiState,
    modifier: Modifier = Modifier
) {
    val legacyAnalyticsUiState = remember(uiState) {
        AnalyticsUiState(
            selectedMonthYearName = uiState.currentMonthYearName,
            totalSpendingMinor = uiState.currentMonthTotalMinor,
            transactionCount = uiState.currentMonthTransactionCount,
            currency = uiState.currency,
            previousMonthTotalMinor = uiState.previousMonthTotalMinor,
            momPercentText = uiState.monthOverMonthPercent?.let { if (it >= 0) "+$it%" else "$it%" }
        )
    }
    AnalyticsScreen(
        uiState = legacyAnalyticsUiState,
        onPreviousMonthClicked = {},
        onNextMonthClicked = {},
        modifier = modifier
    )
}

@Composable
private fun MonthNavigationHeader(
    monthYearName: String,
    dateRangeText: String,
    canNavigateNext: Boolean,
    onPreviousClicked: () -> Unit,
    onNextClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    KanakCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(KanakCornerRadius.medium))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onPreviousClicked)
                    .padding(horizontal = KanakSpacing.medium, vertical = KanakSpacing.small)
            ) {
                Text(
                    text = "← Prev",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = monthYearName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (dateRangeText.isNotBlank()) {
                    Text(
                        text = dateRangeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(KanakCornerRadius.medium))
                    .background(
                        if (canNavigateNext) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                    .clickable(enabled = canNavigateNext, onClick = onNextClicked)
                    .padding(horizontal = KanakSpacing.medium, vertical = KanakSpacing.small)
            ) {
                Text(
                    text = "Next →",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (canNavigateNext) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            }
        }
    }
}

@Composable
private fun SpendingOverviewGrid(
    uiState: AnalyticsUiState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KanakSpacing.small)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KanakSpacing.small)
        ) {
            OverviewStatCard(
                label = "TOTAL SPENT",
                value = uiState.totalSpendingFormatted,
                highlight = true,
                modifier = Modifier.weight(1f)
            )
            OverviewStatCard(
                label = "TRANSACTIONS",
                value = uiState.transactionCount.toString(),
                highlight = false,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KanakSpacing.small)
        ) {
            OverviewStatCard(
                label = "DAILY AVG",
                value = uiState.averageDailySpendingFormatted,
                highlight = false,
                modifier = Modifier.weight(1f)
            )
            OverviewStatCard(
                label = "PEAK DAY",
                value = if (uiState.highestSpendingDayAmountMinor > 0L) uiState.highestSpendingDayAmountFormatted else "N/A",
                subtitle = if (uiState.highestSpendingDayAmountMinor > 0L) uiState.highestSpendingDayText else null,
                highlight = false,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun OverviewStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    highlight: Boolean = false
) {
    KanakCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = if (highlight) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DailySpendingChartSection(
    uiState: AnalyticsUiState,
    modifier: Modifier = Modifier
) {
    KanakCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(KanakSpacing.small)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DAILY SPENDING TREND",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = uiState.dateRangeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (uiState.dailySpendingList.isNotEmpty()) {
                DailySpendingCanvasChart(
                    items = uiState.dailySpendingList,
                    maxAmountMinor = uiState.maxDailySpendingMinor,
                    currency = uiState.currency,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )
            } else {
                Text(
                    text = "No spending data available for this month.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = KanakSpacing.medium)
                )
            }
        }
    }
}

@Composable
private fun DailySpendingCanvasChart(
    items: List<DailySpendingItem>,
    maxAmountMinor: Long,
    currency: String,
    modifier: Modifier = Modifier
) {
    var selectedDayItem by remember(items) {
        mutableStateOf(items.maxByOrNull { it.amountMinor } ?: items.firstOrNull())
    }

    val selectedFormatted = remember(selectedDayItem, currency) {
        selectedDayItem?.let { item ->
            val amt = Expense(amountMinor = item.amountMinor, currency = currency).amountWithSymbol()
            "Day ${item.dayOfMonth}: $amt"
        } ?: "Tap a bar to see day details"
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val gridLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val textMutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = selectedFormatted,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = primaryColor,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(items) {
                        detectTapGestures { offset ->
                            val width = size.width
                            val totalDays = items.size
                            if (totalDays > 0) {
                                val dayWidth = width.toFloat() / totalDays
                                val tappedIndex = (offset.x / dayWidth).toInt().coerceIn(0, totalDays - 1)
                                selectedDayItem = items.getOrNull(tappedIndex)
                            }
                        }
                    }
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val totalDays = items.size
                if (totalDays == 0) return@Canvas

                val barWidth = (canvasWidth / totalDays) * 0.65f
                val barSpacing = canvasWidth / totalDays

                val strokeEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)

                drawLine(
                    color = gridLineColor,
                    start = Offset(0f, canvasHeight * 0.5f),
                    end = Offset(canvasWidth, canvasHeight * 0.5f),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = strokeEffect
                )

                val effectiveMax = if (maxAmountMinor > 0L) maxAmountMinor.toFloat() else 1f

                items.forEachIndexed { index, item ->
                    val xCenter = (index * barSpacing) + (barSpacing / 2f)
                    val barLeft = xCenter - (barWidth / 2f)

                    val barHeight = if (item.amountMinor > 0L) {
                        ((item.amountMinor.toFloat() / effectiveMax) * (canvasHeight - 16.dp.toPx())).coerceAtLeast(4.dp.toPx())
                    } else {
                        2.dp.toPx()
                    }

                    val barTop = canvasHeight - barHeight

                    val isSelected = selectedDayItem?.dayOfMonth == item.dayOfMonth

                    val barColor = when {
                        isSelected -> primaryColor
                        item.amountMinor > 0L -> EmeraldSecondary
                        else -> surfaceVariantColor.copy(alpha = 0.5f)
                    }

                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(barLeft, barTop),
                        size = Size(barWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val totalDays = items.size
            if (totalDays > 0) {
                Text("1", style = MaterialTheme.typography.labelSmall, color = textMutedColor)
                if (totalDays >= 10) Text("10", style = MaterialTheme.typography.labelSmall, color = textMutedColor)
                if (totalDays >= 20) Text("20", style = MaterialTheme.typography.labelSmall, color = textMutedColor)
                Text("$totalDays", style = MaterialTheme.typography.labelSmall, color = textMutedColor)
            }
        }
    }
}

@Composable
private fun CategoryBreakdownSection(
    uiState: AnalyticsUiState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KanakSpacing.small)
    ) {
        Text(
            text = "SPENDING BY CATEGORY",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (uiState.categoryBreakdown.isNotEmpty()) {
            KanakCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(KanakSpacing.medium)) {
                    uiState.categoryBreakdown.forEach { item ->
                        val formattedAmount = Expense(amountMinor = item.amountMinor, currency = uiState.currency).amountWithSymbol()
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(KanakSpacing.small)
                                ) {
                                    item.category?.let { cat ->
                                        CategoryIcon(
                                            category = cat,
                                            tint = MaterialTheme.colorScheme.primary,
                                            size = 18.dp
                                        )
                                    }
                                    Text(
                                        text = item.category?.displayName ?: "Uncategorized",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = "$formattedAmount (${item.percentage}%)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            LinearProgressIndicator(
                                progress = { (item.percentage / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(KanakCornerRadius.pill)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            KanakCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "No category spending for this month.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MonthlyComparisonSection(
    uiState: AnalyticsUiState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KanakSpacing.small)
    ) {
        Text(
            text = "MONTHLY COMPARISON",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        KanakCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(KanakSpacing.medium)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SELECTED MONTH",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = uiState.totalSpendingFormatted,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "PREVIOUS MONTH",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = uiState.previousMonthTotalFormatted,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (uiState.momPercentText != null || uiState.comparisonNote.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(KanakSpacing.extraSmall)
                    ) {
                        if (uiState.momPercentText != null) {
                            val isMore = uiState.isIncrease
                            val badgeColor = if (isMore) StatusError else StatusSuccess

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(KanakCornerRadius.pill))
                                    .background(badgeColor.copy(alpha = 0.2f))
                                    .padding(horizontal = KanakSpacing.small, vertical = 2.dp)
                            ) {
                                Text(
                                    text = uiState.momPercentText,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeColor
                                )
                            }
                        }

                        if (uiState.comparisonNote.isNotBlank()) {
                            Text(
                                text = uiState.comparisonNote,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
