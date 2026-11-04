package com.kanak.app.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.kanak.app.designsystem.theme.KanakCornerRadius
import com.kanak.app.designsystem.theme.KanakSpacing
import com.kanak.app.model.CategorizationState
import com.kanak.app.model.EntrySource

@Composable
fun KanakSourceBadge(
    source: EntrySource,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor) = when (source) {
        EntrySource.TYPED -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        EntrySource.VOICE -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }

    Box(
        modifier = modifier
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(KanakCornerRadius.pill)
            )
            .padding(horizontal = KanakSpacing.small, vertical = KanakSpacing.extraSmall)
    ) {
        Text(
            text = source.name,
            style = MaterialTheme.typography.labelMedium,
            color = textColor
        )
    }
}

@Composable
fun KanakCategorizationBadge(
    state: CategorizationState,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor) = when (state) {
        CategorizationState.CONFIRMED -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        CategorizationState.AUTO_CATEGORIZED -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        CategorizationState.UNCATEGORIZED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        CategorizationState.PENDING_REVIEW -> Color(0xFFFFF3CD) to Color(0xFF856404)
        CategorizationState.PENDING_ENRICHMENT -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        CategorizationState.ENRICHMENT_FAILED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }

    Box(
        modifier = modifier
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(KanakCornerRadius.pill)
            )
            .padding(horizontal = KanakSpacing.small, vertical = KanakSpacing.extraSmall)
    ) {
        Text(
            text = state.name.replace("_", " "),
            style = MaterialTheme.typography.labelMedium,
            color = textColor
        )
    }
}
