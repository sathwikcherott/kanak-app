package com.kanak.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kanak.app.designsystem.theme.KanakCornerRadius
import com.kanak.app.designsystem.theme.KanakSpacing

@Composable
fun KanakBottomNavigation(
    activeTab: KanakTab,
    onTabSelected: (KanakTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = KanakSpacing.small, vertical = KanakSpacing.extraSmall)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            KanakTab.entries.forEach { tab ->
                val selected = tab == activeTab
                val contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                val shape = RoundedCornerShape(KanakCornerRadius.pill)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(shape)
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { onTabSelected(tab) }
                        .padding(horizontal = KanakSpacing.medium, vertical = KanakSpacing.small)
                        .semantics { role = Role.Tab }
                ) {
                    val iconSymbol = when (tab) {
                        KanakTab.HOME -> "🏠"
                        KanakTab.TRANSACTIONS -> "📊"
                        KanakTab.ANALYTICS -> "📈"
                        KanakTab.SETTINGS -> "⚙️"
                    }
                    Text(
                        text = iconSymbol,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = tab.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}
