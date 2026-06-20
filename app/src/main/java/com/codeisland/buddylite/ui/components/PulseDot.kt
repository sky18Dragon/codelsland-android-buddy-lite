package com.codeisland.buddylite.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.codeisland.buddylite.data.model.CompanionStatus
import com.codeisland.buddylite.ui.theme.BuddyColors
import kotlin.math.sin

@Composable
fun PulseDot(
    status: CompanionStatus,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp
) {
    val color = when {
        status.isWaiting -> BuddyColors.StatusAttention
        status.isActive -> BuddyColors.StatusActive
        else -> BuddyColors.StatusIdle
    }

    val pulseAlpha = when {
        status.isWaiting -> {
            val transition = rememberInfiniteTransition()
            transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse)
            )
        }
        status.isActive -> {
            val transition = rememberInfiniteTransition()
            transition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse)
            )
        }
        else -> null
    }

    Canvas(modifier = modifier.size(size)) {
        val dotRadius = size.toPx() / 2.5f
        val center = Offset(size.toPx() / 2, size.toPx() / 2)

        // Outer glow
        if (pulseAlpha != null) {
            drawCircle(
                color = color.copy(alpha = pulseAlpha.value * 0.3f),
                radius = dotRadius * 2.2f,
                center = center
            )
        }

        // Inner dot
        drawCircle(color = color, radius = dotRadius, center = center)
    }
}
