package com.example.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================================
// NEUMORPHIC COLOR PALETTE (From user's Soft UI screenshot: #0072ff, rgba(163,177,198,.65), etc.)
// ============================================================================
val NeuBackground = Color(0xFFE6EDF5)        // Master canvas soft cool gray
val NeuSurface = Color(0xFFEBF1F8)           // Raised surface color
val NeuSurfaceRaised = Color(0xFFEEF4FA)     // Slightly lighter elevated surface
val NeuDarkShadow = Color(0xFFA3B1C6)        // Exact rgb(163, 177, 198) from user screenshot
val NeuLightHighlight = Color(0xFFFFFFFF)    // Exact rgb(255, 255, 255) from user screenshot
val NeuInsetBg = Color(0xFFDEE5F0)           // Sunken debossed input background
val NeuInsetBorder = Color(0xFFCBD5E1)       // Sunken input border

val NeuAccentBlue = Color(0xFF0072FF)        // Exact #0072ff from user screenshot CSS
val NeuAccentBlueHover = Color(0xFF005ED4)   // Deep hover/pressed state
val NeuAccentBlueGlow = Color(0x590072FF)    // rgba(0, 114, 255, 0.35) glow shadow

val NeuTextPrimary = Color(0xFF1E293B)       // Slate dark primary text
val NeuTextSecondary = Color(0xFF475569)     // Slate medium secondary text
val NeuTextMuted = Color(0xFF64748B)         // Slate light placeholder text

val NeuSuccess = Color(0xFF10B981)           // Soft emerald
val NeuError = Color(0xFFEF4444)             // Soft crimson alert

// ============================================================================
// NEUMORPHIC REUSABLE COMPOSABLES
// ============================================================================

/**
 * Raised Neumorphic Card with soft dual elevation and top-left light border highlight.
 */
@Composable
fun NeuCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    backgroundColor: Color = NeuSurface,
    elevation: Dp = 8.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = NeuDarkShadow.copy(alpha = 0.55f),
                spotColor = NeuDarkShadow.copy(alpha = 0.70f)
            )
            .clip(shape)
            .background(backgroundColor)
            .border(
                border = BorderStroke(
                    width = 1.2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            NeuLightHighlight.copy(alpha = 0.95f),
                            NeuLightHighlight.copy(alpha = 0.40f),
                            NeuDarkShadow.copy(alpha = 0.25f)
                        )
                    )
                ),
                shape = shape
            )
            .padding(contentPadding)
    ) {
        Column(content = content)
    }
}

/**
 * Sunken / Inset Neumorphic Box for TextFields and debossed controls (like the email/password fields in screenshot).
 */
@Composable
fun NeuInsetBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = NeuInsetBg,
    contentAlignment: Alignment = Alignment.CenterStart,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(
                border = BorderStroke(
                    width = 1.2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            NeuInsetBorder.copy(alpha = 0.85f),
                            NeuInsetBorder.copy(alpha = 0.40f),
                            NeuLightHighlight.copy(alpha = 0.80f)
                        )
                    )
                ),
                shape = shape
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = contentAlignment,
        content = content
    )
}

/**
 * Primary Neumorphic Button with vibrant blue gradient and soft blue drop glow (matches .switch-circle and login button).
 */
@Composable
fun NeuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(16.dp),
    isPrimary: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val bgModifier = if (isPrimary) {
        Modifier
            .shadow(
                elevation = 8.dp,
                shape = shape,
                ambientColor = NeuAccentBlueGlow,
                spotColor = NeuAccentBlue.copy(alpha = 0.50f)
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF268AFF),
                        NeuAccentBlue
                    )
                )
            )
    } else {
        Modifier
            .shadow(
                elevation = 6.dp,
                shape = shape,
                ambientColor = NeuDarkShadow.copy(alpha = 0.50f),
                spotColor = NeuDarkShadow.copy(alpha = 0.60f)
            )
            .clip(shape)
            .background(NeuSurfaceRaised)
            .border(
                border = BorderStroke(
                    width = 1.2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            NeuLightHighlight.copy(alpha = 0.95f),
                            NeuDarkShadow.copy(alpha = 0.20f)
                        )
                    )
                ),
                shape = shape
            )
    }

    Box(
        modifier = modifier
            .then(bgModifier)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple()
            ) { onClick() }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content
        )
    }
}

/**
 * Circular Neumorphic Action Button (matches the circular .switch-circle button in user's screenshot).
 */
@Composable
fun NeuCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    isPrimary: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val bgModifier = if (isPrimary) {
        Modifier
            .shadow(
                elevation = 8.dp,
                shape = CircleShape,
                ambientColor = NeuAccentBlueGlow,
                spotColor = NeuAccentBlue.copy(alpha = 0.50f)
            )
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF268AFF),
                        NeuAccentBlue
                    )
                )
            )
    } else {
        Modifier
            .shadow(
                elevation = 6.dp,
                shape = CircleShape,
                ambientColor = NeuDarkShadow.copy(alpha = 0.55f),
                spotColor = NeuDarkShadow.copy(alpha = 0.65f)
            )
            .clip(CircleShape)
            .background(NeuSurfaceRaised)
            .border(
                border = BorderStroke(
                    width = 1.2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            NeuLightHighlight.copy(alpha = 0.95f),
                            NeuDarkShadow.copy(alpha = 0.25f)
                        )
                    )
                ),
                shape = CircleShape
            )
    }

    Box(
        modifier = modifier
            .size(size)
            .then(bgModifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple()
            ) { onClick() },
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * Neumorphic Badge / Icon Container (like the top lock circle in user's screenshot).
 */
@Composable
fun NeuBadge(
    modifier: Modifier = Modifier,
    size: Dp = 54.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 6.dp,
                shape = CircleShape,
                ambientColor = NeuDarkShadow.copy(alpha = 0.55f),
                spotColor = NeuDarkShadow.copy(alpha = 0.65f)
            )
            .clip(CircleShape)
            .background(NeuSurface)
            .border(
                border = BorderStroke(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            NeuLightHighlight,
                            NeuDarkShadow.copy(alpha = 0.30f)
                        )
                    )
                ),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}
