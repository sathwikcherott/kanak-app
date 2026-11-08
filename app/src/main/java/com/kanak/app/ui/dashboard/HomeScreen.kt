package com.kanak.app.ui.dashboard

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kanak.app.designsystem.components.CategoryIcon
import com.kanak.app.designsystem.components.KanakCard
import com.kanak.app.designsystem.components.KanakPrimaryButton
import com.kanak.app.designsystem.components.KanakSecondaryButton
import com.kanak.app.designsystem.components.getCategoryColor
import com.kanak.app.designsystem.theme.KanakCornerRadius
import com.kanak.app.designsystem.theme.KanakSpacing
import com.kanak.app.designsystem.theme.StatusError
import com.kanak.app.designsystem.theme.StatusSuccess
import com.kanak.app.model.Expense
import com.kanak.app.model.ExpenseCategory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onAddExpenseClicked: () -> Unit,
    onVoiceEntryClicked: () -> Unit,
    onViewAllTransactionsClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = KanakSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(KanakSpacing.medium)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = KanakSpacing.medium),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Kanak",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Offline Expense Tracker",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(KanakCornerRadius.pill))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = KanakSpacing.medium, vertical = KanakSpacing.extraSmall)
                ) {
                    Text(
                        text = uiState.currentMonthYearName.ifBlank { "Current Month" },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        item {
            KanakCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(KanakSpacing.medium)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "TODAY",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = uiState.todayFormatted,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "TRANSACTIONS",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${uiState.currentMonthTransactionCount} this month",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "CURRENT MONTH TOTAL",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = uiState.totalFormatted,
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (uiState.monthOverMonthPercent != null) {
                            val pct = uiState.monthOverMonthPercent
                            val isMore = pct > 0
                            val badgeColor = if (isMore) StatusError else StatusSuccess
                            val signStr = if (isMore) "+$pct%" else "$pct%"

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(KanakSpacing.extraSmall)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(KanakCornerRadius.pill))
                                        .background(badgeColor.copy(alpha = 0.15f))
                                        .padding(horizontal = KanakSpacing.small, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = signStr,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeColor
                                    )
                                }
                                Text(
                                    text = "vs last month (${uiState.previousTotalFormatted})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else if (uiState.previousMonthTotalMinor > 0L) {
                            Text(
                                text = "Previous month: ${uiState.previousTotalFormatted}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "Tracking offline expenses locally",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KanakSpacing.medium)
            ) {
                KanakPrimaryButton(
                    onClick = onAddExpenseClicked,
                    text = "+ Add Expense",
                    modifier = Modifier.weight(1f)
                )

                KanakSecondaryButton(
                    onClick = onVoiceEntryClicked,
                    text = "🎤 Voice",
                    modifier = Modifier.weight(0.6f)
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(KanakSpacing.small)) {
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
                                CategoryBreakdownRow(item = item, currency = uiState.currency)
                            }
                        }
                    }
                } else {
                    KanakCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "No expenses recorded yet. Tap + Add Expense to get started.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECENT TRANSACTIONS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (uiState.recentTransactions.isNotEmpty()) {
                    TextButton(onClick = onViewAllTransactionsClicked) {
                        Text(
                            text = "View All →",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (uiState.recentTransactions.isNotEmpty()) {
            items(uiState.recentTransactions.take(5), key = { it.id }) { expense ->
                DashboardExpenseRow(expense = expense)
            }
        }

        item {
            Spacer(modifier = Modifier.height(KanakSpacing.large))
        }
    }
}

@Composable
private fun CategoryBreakdownRow(
    item: CategorySpending,
    currency: String,
    modifier: Modifier = Modifier
) {
    val formattedAmount = Expense(amountMinor = item.amountMinor, currency = currency).amountWithSymbol()
    val catColor = item.category?.getCategoryColor() ?: MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier.fillMaxWidth(),
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
                        tint = catColor,
                        size = 18.dp
                    )
                }
                Text(
                    text = item.category?.displayName ?: "Uncategorized",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(KanakSpacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedAmount,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "(${item.percentage}%)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LinearProgressIndicator(
            progress = { (item.percentage / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(KanakCornerRadius.pill)),
            color = catColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun DashboardExpenseRow(
    expense: Expense,
    modifier: Modifier = Modifier
) {
    val dateFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    val dateStr = dateFormat.format(Date(expense.occurredAt))
    val category = expense.category
    val catColor = category?.getCategoryColor() ?: MaterialTheme.colorScheme.primary

    KanakCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KanakSpacing.medium)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(KanakCornerRadius.medium))
                        .background(catColor.copy(alpha = 0.15f))
                        .padding(10.dp)
                ) {
                    CategoryIcon(
                        category = category ?: ExpenseCategory.OTHER,
                        tint = catColor,
                        size = 20.dp
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = expense.category?.displayName ?: "Uncategorized",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = expense.note ?: dateStr,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = expense.amountWithSymbol(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
