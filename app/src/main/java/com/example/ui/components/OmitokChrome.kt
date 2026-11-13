package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.ServerHealth
import com.example.i18n.AppLanguage
import com.example.i18n.OmitokStrings
import com.example.ui.theme.CardSlate
import com.example.ui.theme.CyberViolet
import com.example.ui.theme.ElectricMagenta
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.WarningAmber

@Composable
fun OmitokPrismEmblem(
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val center = Offset(w / 2f, h / 2f)
        val radius = minOf(w, h) * 0.42f

        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(NeonCyan, CyberViolet, ElectricMagenta, NeonCyan)
            ),
            radius = radius,
            center = center,
            style = Stroke(width = w * 0.11f, cap = StrokeCap.Round)
        )

        val triangle = Path().apply {
            moveTo(center.x - radius * 0.24f, center.y - radius * 0.34f)
            lineTo(center.x + radius * 0.44f, center.y)
            lineTo(center.x - radius * 0.24f, center.y + radius * 0.34f)
            close()
        }
        drawPath(
            path = triangle,
            brush = Brush.linearGradient(listOf(NeonCyan, ElectricMagenta))
        )
    }
}

@Composable
fun OmitokBrandHeader(
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
    ) {
        OmitokPrismEmblem(size = 38.dp)
        Text(
            text = "OMITOK",
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun LanguageTogglePill(
    currentLanguage: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = CardSlate.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, SubtleBorder),
        modifier = modifier.testTag("language_toggle_pill")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(4.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Language,
                contentDescription = null,
                tint = NeonCyan,
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .size(18.dp)
            )
            AppLanguage.entries.forEach { lang ->
                val selected = lang == currentLanguage
                val bg by animateColorAsState(
                    if (selected) NeonCyan else Color.Transparent,
                    label = "lang_pill_bg"
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(bg)
                        .clickable { onLanguageChange(lang) }
                        .defaultMinSize(minWidth = 44.dp, minHeight = 36.dp)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = lang.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) ObsidianBlack else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ServerStatusChip(
    health: ServerHealth?,
    isChecking: Boolean,
    strings: OmitokStrings,
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dotColor = when {
        isChecking -> WarningAmber
        health?.isOnline == true -> SuccessEmerald
        else -> ErrorCoral
    }
    val statusText = when {
        isChecking -> strings.serverChecking
        health?.isOnline == true -> "${strings.serverOnline} • ${health.latencyMs}ms"
        health != null -> "${strings.serverOffline} (${health.message})"
        else -> strings.serverChecking
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = CardSlate.copy(alpha = 0.86f),
        border = BorderStroke(1.dp, dotColor.copy(alpha = 0.45f)),
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onRefreshClick)
            .testTag("server_status_chip")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            if (isChecking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 2.dp,
                    color = WarningAmber
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = strings.retry,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun OmitokPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = NeonCyan,
            contentColor = ObsidianBlack,
            disabledContainerColor = NeonCyan.copy(alpha = 0.35f),
            disabledContentColor = ObsidianBlack.copy(alpha = 0.6f)
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        modifier = modifier.height(52.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.5.dp,
                color = ObsidianBlack
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun OmitokSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.2.dp, SubtleBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = CardSlate.copy(alpha = 0.65f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        modifier = modifier.height(52.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
