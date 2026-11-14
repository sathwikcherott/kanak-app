package com.kanak.app.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kanak.app.designsystem.theme.EmeraldPrimary
import com.kanak.app.designsystem.theme.EmeraldSoftBg
import com.kanak.app.designsystem.theme.KanakCornerRadius
import com.kanak.app.designsystem.theme.KanakSpacing
import com.kanak.app.designsystem.theme.StatusError
import com.kanak.app.designsystem.theme.StatusWarning
import com.kanak.app.model.CategorizationState
import com.kanak.app.model.EntrySource

@Composable
fun KanakSourceBadge(
    source: EntrySource,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor) = when (source) {
        EntrySource.TYPED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        EntrySource.VOICE -> EmeraldSoftBg to EmeraldPrimary
        EntrySource.NOTIFICATION -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
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
        CategorizationState.CONFIRMED -> EmeraldSoftBg to EmeraldPrimary
        CategorizationState.AUTO_CATEGORIZED -> EmeraldSoftBg to EmeraldPrimary
        CategorizationState.UNCATEGORIZED -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        CategorizationState.PENDING_REVIEW -> StatusWarning.copy(alpha = 0.2f) to StatusWarning
        CategorizationState.PENDING_ENRICHMENT -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        CategorizationState.ENRICHMENT_FAILED -> StatusError.copy(alpha = 0.2f) to StatusError
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
